package org.nmox.studio.apiclient.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gates for the v1.200.0 arc-review fixes — both are wiring
 * inside a Swing method no headless test can drive end-to-end, so the
 * gate pins the calls in place (the DialogSafetyTest idiom).
 *
 * <p>1. Deleting a request or a collection must forget the deleted
 * requests' keychain secrets (DB Studio remove-connection parity): the
 * id leaves the file forever, so a kept entry is an orphaned secret
 * nothing can read again.
 *
 * <p>2. Clearing the response must refresh the find bar: the v1.198.0
 * refind sites cover the send paths, but a re-aim clears the body via
 * {@code clearResponse()} — a stale match count would claim matches in
 * a response that no longer exists.
 */
class DeleteHygieneGateTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "apiclient", "ui", "ApiClientTopComponent.java"));
    }

    private static String method(String src, String name) {
        int at = src.indexOf("private void " + name + "(");
        assertThat(at).as(name + " exists").isGreaterThan(0);
        // methods here are short; the next method boundary is enough
        int end = src.indexOf("\n    private ", at + 1);
        return src.substring(at, end > 0 ? end : src.length());
    }

    @Test
    @DisplayName("deleteSelected forgets keychain secrets for every deleted request")
    void deleteForgetsSecrets() throws Exception {
        String body = method(source(), "deleteSelected");
        assertThat(body)
                .as("deleted ids must be forgotten from the keychain — "
                        + "removing this recreates the orphaned-secret leak")
                .contains("ApiSecrets::delete");
        assertThat(body)
                .as("the collection branch must forget EVERY member request's "
                        + "secret, not only a single-request delete")
                .contains("c.requests.forEach(r -> forget.add(r.id))");
        assertThat(body)
                .as("keyring calls may block on OS prompts — off the EDT")
                .contains("RP.post");
    }

    @Test
    @DisplayName("clearResponse refreshes the find bar so the match count cannot go stale")
    void clearResponseRefreshesFindBar() throws Exception {
        String src = source();
        int at = src.indexOf("void clearResponse()");
        assertThat(at).isGreaterThan(0);
        String body = src.substring(at, src.indexOf("\n    }", at));
        assertThat(body)
                .as("the clearing path must refind — the send paths alone "
                        + "leave a stale count after a re-aim")
                .contains("refindInBody()");
    }
}
