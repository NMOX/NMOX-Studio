package org.nmox.studio.apiclient.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lazy-hydration laws (v1.201.0, David-approved after a live
 * prompt-storm): API Studio must not bulk-read every request's keychain
 * token at tab open — after any binary change (every upgrade) that
 * fired a macOS password prompt at startup. Tokens load per request on
 * first display/send/duplicate/copy-curl instead. The wiring lives in
 * one Swing class, so these are source gates (the DialogSafetyTest
 * idiom), each half proven by mutation.
 */
class LazyHydrationGateTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "apiclient", "ui", "ApiClientTopComponent.java"));
    }

    private static String method(String src, String signature) {
        int at = src.indexOf(signature);
        assertThat(at).as(signature + " exists").isGreaterThan(0);
        int end = src.indexOf("\n    private ", at + 1);
        return src.substring(at, end > 0 ? end : src.length());
    }

    @Test
    @DisplayName("workspace load never bulk-reads the keychain — the prompt storm stays dead")
    void reconcileDoesNotBulkRead() throws Exception {
        String body = method(source(), "private void reconcileSecrets(");
        assertThat(body)
                .as("re-adding ApiSecrets.read to the load loop recreates the "
                        + "at-startup password prompt after every upgrade")
                .doesNotContain("ApiSecrets.read");
        assertThat(body)
                .as("the legacy plaintext migration WRITE must stay — old files "
                        + "still need their tokens moved into the keychain")
                .contains("ApiSecrets.save");
    }

    @Test
    @DisplayName("every consumer hydrates its own request on first use")
    void consumersHydrate() throws Exception {
        String src = source();
        assertThat(method(src, "private void bindRequest("))
                .as("selection shows the real token once loaded").contains("hydrateAuth(");
        assertThat(method(src, "private void send()"))
                .as("a send loads the token on the send lane, off the EDT")
                .contains("hydrateAuthNow(request)");
        assertThat(method(src, "private void duplicateSelected("))
                .as("duplicating an untouched request must still carry its secret")
                .contains("hydrateAuthNow(source)");
        assertThat(src)
                .as("copy-curl renders what Send would run — auth included")
                .contains("hydrateAuthNow(target)");
    }

    @Test
    @DisplayName("save() skips never-hydrated requests — no keychain entry is wiped with an empty push")
    void saveSkipsUnhydrated() throws Exception {
        String body = method(source(), "private void save()");
        assertThat(body)
                .as("pushing a never-hydrated request's blank token would "
                        + "overwrite its keychain entry with \"\" on the first "
                        + "autosave — the silent token-destruction path")
                .contains("hydratedAuth.contains(r.id)");
    }

    @Test
    @DisplayName("a consulted id is never consulted again — a denied prompt stays denied")
    void deniedPromptCachedPerSession() throws Exception {
        String body = method(source(), "private void hydrateAuthNow(");
        assertThat(body)
                .as("the add-and-check is the once-per-session guarantee: "
                        + "without it every selection re-fires the OS prompt "
                        + "the user already denied")
                .contains("!hydratedAuth.add(r.id)");
    }
}
