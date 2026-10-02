package org.nmox.studio.editor.lsp;

import java.util.concurrent.ExecutionException;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A server's answer that a request was overtaken is not shown as an error;
 * every other error still is (3.5.10).
 *
 * <p>Seen in a walk: Run pressed while rust-analyzer was loading logged
 * {@code content modified} at SEVERE and lit the red mark on the status
 * line.
 */
class SilentServerErrorsTest {

    private static LogRecord record(Throwable thrown) {
        LogRecord record = new LogRecord(Level.SEVERE, null);
        record.setThrown(thrown);
        return record;
    }

    /** As the platform's client logs it: the answer, wrapped by the future that carried it. */
    private static Throwable answered(int code, String message) {
        return new ExecutionException(new ResponseErrorException(new ResponseError(code, message, null)));
    }

    @Test
    @DisplayName("content modified, request cancelled and server cancelled are dropped; the wrapped form the client logs included")
    void overtakenAnswersAreDropped() {
        Filter filter = new SilentServerErrors.Overtaken(null);
        assertThat(filter.isLoggable(record(answered(-32801, "content modified")))).isFalse();
        assertThat(filter.isLoggable(record(answered(-32800, "cancelled")))).isFalse();
        assertThat(filter.isLoggable(record(answered(-32802, "server cancelled")))).isFalse();
        assertThat(filter.isLoggable(record(new ResponseErrorException(new ResponseError(-32801, "content modified", null)))))
                .as("unwrapped").isFalse();
    }

    @Test
    @DisplayName("a server's real refusal is still shown, and so is everything that is not a server's answer")
    void everythingElsePasses() {
        Filter filter = new SilentServerErrors.Overtaken(null);
        assertThat(filter.isLoggable(record(answered(-32603, "file not found")))).as("an internal error").isTrue();
        assertThat(filter.isLoggable(record(answered(-32602, "No language service for 'file:///x'")))).isTrue();
        assertThat(filter.isLoggable(record(new IllegalStateException("content modified"))))
                .as("the words alone are not the answer: the code is").isTrue();
        assertThat(filter.isLoggable(record(null))).as("a record with nothing thrown").isTrue();
        assertThat(filter.isLoggable(new LogRecord(Level.INFO, "a message"))).isTrue();
    }

    @Test
    @DisplayName("the filter that was there before still decides for everything this one passes")
    void theFilterBeforeStillDecides() {
        Filter refusesAll = record -> false;
        assertThat(new SilentServerErrors.Overtaken(refusesAll).isLoggable(record(answered(-32603, "internal")))).isFalse();
        Filter passesAll = record -> true;
        assertThat(new SilentServerErrors.Overtaken(passesAll).isLoggable(record(answered(-32801, "content modified"))))
                .as("and it is not asked about a record this one drops").isFalse();
    }

    @Test
    @DisplayName("a cause chain that loops is read to a bound, not forever")
    void aLoopingChainEnds() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertThat(SilentServerErrors.overtaken(a)).isNull();
    }

    @Test
    @DisplayName("installed once on the logger Exceptions writes to, over whatever filter was there, and held")
    void installedOnce() throws Exception {
        Logger first = SilentServerErrors.install();
        assertThat(first.getName()).isEqualTo("org.openide.util.Exceptions");
        Filter installed = first.getFilter();
        assertThat(installed).isInstanceOf(SilentServerErrors.Overtaken.class);
        assertThat(SilentServerErrors.install().getFilter()).as("a second start does not wrap it again").isSameAs(installed);
        assertThat(Logger.getLogger(SilentServerErrors.LOGGER).isLoggable(Level.SEVERE)).isTrue();

        // what the processor generated, as the platform reads it: the outcome, not the annotation's spelling
        String started = java.nio.file.Files.readString(java.nio.file.Path.of(
                "target/classes/META-INF/namedservices/Modules/Start/java.lang.Runnable"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(started).as("it runs when the module starts, before any server answers")
                .contains("org.nmox.studio.editor.lsp.SilentServerErrors");
    }

    @Test
    @DisplayName("the class the filter looks for by name is lsp4j's, with the two methods it calls")
    void theNameIsTheRealClass() throws Exception {
        assertThat(ResponseErrorException.class.getName()).isEqualTo(SilentServerErrors.RESPONSE_ERROR);
        assertThat(ResponseErrorException.class.getMethod("getResponseError").getReturnType().getMethod("getCode")
                .getReturnType()).isEqualTo(int.class);
    }
}
