package org.nmox.studio.apiclient.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The send-history wiring laws, source-gated: history records the SENT
 * request on BOTH outcome paths (a failed send is exactly the row you
 * go looking for), the workspace load repopulates the list (a re-aim
 * must not show the previous project's sends — the v1.172.0 class),
 * and clearing confirms with the safe default.
 */
class HistoryWiringTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"));
    }

    @Test
    @DisplayName("Both send outcomes record the sent request")
    void bothOutcomesRecord() throws Exception {
        String s = source();
        int send = s.indexOf("private void send()");
        int end = s.indexOf("\n    private ", send + 10);
        String body = s.substring(send, end);
        assertThat(body.split("recordHistory\\(request", -1).length - 1)
                .as("success and failure paths each record the SENT request")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("applyWorkspace repopulates history — a re-aim can't show the old project's sends")
    void reAimRefreshesHistory() throws Exception {
        String s = source();
        int apply = s.indexOf("private void applyWorkspace(");
        int end = s.indexOf("\n    private ", apply + 10);
        assertThat(s.substring(apply, end)).contains("refreshHistory()");
    }

    @Test
    @DisplayName("Clear history confirms with the safe default (v1.98.0 idiom)")
    void clearConfirmsSafely() throws Exception {
        String s = source();
        int clear = s.indexOf("private void clearHistory()");
        int end = s.indexOf("\n    }", clear);
        assertThat(s.substring(clear, end)).contains("NotifyDescriptor.NO_OPTION");
    }
}
