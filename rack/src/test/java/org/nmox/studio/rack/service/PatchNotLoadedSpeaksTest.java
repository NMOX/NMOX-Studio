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
    @DisplayName("the two refusals a reader can act on are said in the reader's OWN language, with no English argument and the file named ONCE")
    void theTwoActionableRefusalsSpeakWholly() throws Exception {
        File patch = new File("/projects/shop/.nmoxrack.json");

        java.io.IOException tooLarge = null;
        java.io.File big = java.io.File.createTempFile("big", ".nmoxrack.json");
        try {
            byte[] block = new byte[1024 * 1024];
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(big.toPath())) {
                for (int i = 0; i < 9; i++) {
                    out.write(block);
                }
            }
            try {
                org.nmox.studio.rack.model.RackIO.readDocument(big);
            } catch (java.io.IOException refused) {
                tooLarge = refused;
            }
        } finally {
            big.delete();
        }
        assertThat(tooLarge).as("a 9 MiB patch is refused").isNotNull();
        String big9 = RackService.patchNotLoadedText(patch, tooLarge);
        // GROUPED, because a size is read by a person: MessageFormat writes 9,216
        // under this test's English and 9.216 under German — the v2.104.0 Numbers law
        assertThat(big9).contains("9,216 KiB").contains("8 MiB").contains("empty");
        assertThat(big9).as("the file named once, never twice")
                .containsOnlyOnce(".nmoxrack.json");

        java.io.File bad = java.io.File.createTempFile("bad", ".nmoxrack.json");
        java.io.IOException corrupt = null;
        try {
            java.nio.file.Files.writeString(bad.toPath(), "{ \"devices\": [ ");
            try {
                org.nmox.studio.rack.model.RackIO.readDocument(bad);
            } catch (java.io.IOException refused) {
                corrupt = refused;
            }
        } finally {
            bad.delete();
            new java.io.File(bad.getPath() + ".bak").delete();
        }
        assertThat(corrupt).as("a truncated patch is refused").isNotNull();
        String text = RackService.patchNotLoadedText(patch, corrupt);
        assertThat(text).contains("not valid JSON").contains(".bak");
        assertThat(text).as("the PARSER's English complaint stays in the log, never on the status line")
                .doesNotContain("Expected").doesNotContain("character");
        assertThat(text).as("the reader's own directory layout is never printed")
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
