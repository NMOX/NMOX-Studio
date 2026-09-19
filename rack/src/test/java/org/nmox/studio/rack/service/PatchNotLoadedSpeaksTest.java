package org.nmox.studio.rack.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.GateSources;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refusals speak — including the one nobody asked for (ledger 104, v2.180.0).
 *
 * <p>Aiming a project loads its {@code .nmoxrack.json}. When that file is
 * corrupt or over the 8 MiB cap, {@code RackIO.load}'s contract is to replace
 * the rack's contents, and a patch it refuses supplies none — so the reader
 * got an EMPTY rack and the reason went to a log file they never open. Every
 * other refusal in the product says what happened; this one did not, because
 * it is the one the user did not ask for.
 */
class PatchNotLoadedSpeaksTest {

    private Locale before;

    @BeforeEach
    void english() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    @Test
    @DisplayName("the sentence names the file, says the rack is empty, and carries the reason — which already says whether the file was kept as .bak or left unread")
    void theSentenceSaysAllThree() {
        File patch = new File("/projects/shop/.nmoxrack.json");

        String corrupt = RackService.patchNotLoadedText(patch,
                new IOException("Corrupt rack patch .nmoxrack.json (kept as .bak): Expected a ',' or '}'"));
        assertThat(corrupt).contains(".nmoxrack.json").contains("empty").contains("kept as .bak");

        String tooLarge = RackService.patchNotLoadedText(patch,
                new IOException("Rack patch .nmoxrack.json is 9216 KiB, over the 8 MiB cap — not read"));
        assertThat(tooLarge).contains("over the 8 MiB cap").contains("not read");

        assertThat(corrupt).as("the file NAME, never the reader's directory layout")
                .doesNotContain("/projects/shop");
    }

    @Test
    @DisplayName("a failure with no message still says something a reader can act on — never an empty half-sentence")
    void aSilentFailureStillSpeaks() {
        String text = RackService.patchNotLoadedText(new File(".nmoxrack.json"), new IllegalStateException());
        assertThat(text).contains(".nmoxrack.json").contains("IllegalStateException");
        assertThat(text.strip()).doesNotEndWith("—");
    }

    @Test
    @DisplayName("the aim path SAYS it: autoLoadPatch's catch reaches the status line, not the log alone")
    void theCatchReachesTheStatusLine() throws Exception {
        String src = GateSources.stripComments(Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "rack", "service", "RackService.java"), StandardCharsets.UTF_8).replace("\r\n", "\n"));
        int auto = src.indexOf("private void autoLoadPatch()");
        assertThat(auto).as("autoLoadPatch exists").isPositive();
        String body = src.substring(auto, src.indexOf("private void resetToStarterRack", auto));
        int caught = body.indexOf("catch (Exception");
        assertThat(caught).as("the load is guarded").isPositive();
        assertThat(body.substring(caught))
                .as("a refusal the reader did not ask for still speaks where the rest of the aim speaks")
                .contains("status(patchNotLoadedText(");
    }
}
