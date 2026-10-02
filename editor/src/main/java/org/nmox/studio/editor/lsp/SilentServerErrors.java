package org.nmox.studio.editor.lsp;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.openide.modules.OnStart;

/**
 * A language server's "ask me again" is not an error to show (3.5.10).
 *
 * <p>The protocol gives a server three answers that mean the request was
 * overtaken and nothing is wrong: {@code ContentModified} (-32801: the
 * document or the workspace changed while it was working),
 * {@code RequestCancelled} (-32800) and {@code ServerCancelled} (-32802).
 * Its specification says of the first that a client "generally should not
 * show it in the UI for the end-user". The platform's client hands every
 * error answer to {@code Exceptions.printStackTrace}, which logs it at
 * SEVERE and lights the red error mark on the status line.
 *
 * <p>Measured on a scratch Cargo project: open a Rust file, press Run while
 * rust-analyzer is still loading. {@code cargo run} writes
 * {@code Cargo.lock} and {@code target}, the server reloads its workspace,
 * and the mark-occurrences request in flight is answered
 * {@code content modified}: one SEVERE and a red mark, on the first Run of
 * a new project, for a request the client simply makes again.
 *
 * <p>A filter on the logger {@code Exceptions} writes to drops a record
 * whose cause is one of those three answers, and notes it at FINE under
 * this class's name so it can still be read. Every other record passes,
 * through whatever filter was there before. A server's real refusals
 * ({@code file not found} for a file in no project, ledger 133) are not
 * touched: they are not "ask again", and hiding them would hide a server
 * that does not serve.
 */
@OnStart
public final class SilentServerErrors implements Runnable {

    /** The logger {@code org.openide.util.Exceptions.printStackTrace} writes to. */
    static final String LOGGER = "org.openide.util.Exceptions";
    /** lsp4j's exception for an error answer; read by name, so the module needs no dependency on the library. */
    static final String RESPONSE_ERROR = "org.eclipse.lsp4j.jsonrpc.ResponseErrorException";

    static final int REQUEST_CANCELLED = -32800;
    static final int CONTENT_MODIFIED = -32801;
    static final int SERVER_CANCELLED = -32802;

    private static final Logger LOG = Logger.getLogger(SilentServerErrors.class.getName());

    /** Held for the life of the module: the log manager keeps loggers weakly. */
    private static Logger held;

    @Override
    public void run() {
        install();
    }

    static synchronized Logger install() {
        if (held == null) {
            held = Logger.getLogger(LOGGER);
            Filter before = held.getFilter();
            if (!(before instanceof Overtaken)) {
                held.setFilter(new Overtaken(before));
            }
        }
        return held;
    }

    /** The filter: false for an overtaken request's answer, otherwise what the filter before it says. */
    static final class Overtaken implements Filter {

        private final Filter before;

        Overtaken(Filter before) {
            this.before = before;
        }

        @Override
        public boolean isLoggable(LogRecord record) {
            Integer code = overtaken(record.getThrown());
            if (code != null) {
                LOG.log(Level.FINE, "a language server answered {0} (the request was overtaken); not shown", code);
                return false;
            }
            return before == null || before.isLoggable(record);
        }
    }

    /**
     * The code, when this throwable or one of its causes is a server's
     * answer that a request was overtaken; null for anything else. Bounded:
     * a cause chain can be made to loop.
     */
    static Integer overtaken(Throwable thrown) {
        Throwable t = thrown;
        for (int depth = 0; t != null && depth < 16; depth++, t = t.getCause()) {
            if (!RESPONSE_ERROR.equals(t.getClass().getName())) {
                continue;
            }
            try {
                Object error = t.getClass().getMethod("getResponseError").invoke(t);
                Object code = error == null ? null : error.getClass().getMethod("getCode").invoke(error);
                if (code instanceof Integer c
                        && (c == CONTENT_MODIFIED || c == REQUEST_CANCELLED || c == SERVER_CANCELLED)) {
                    return c;
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return null; // not the shape this knows: the record is shown, as before
            }
            return null; // an error answer of another kind: shown
        }
        return null;
    }
}
