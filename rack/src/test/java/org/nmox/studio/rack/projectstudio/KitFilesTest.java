package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one kit write law (extracted in the v1.141.0 debt sprint from the
 * copies in ClassicKit and ContractKit): write-if-absent, leave an
 * identical file untouched, keep a differing file and land the proposal
 * as .suggested, and never overwrite an existing .suggested.
 */
class KitFilesTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("A missing file is written, nested dirs created")
    void writesMissing() throws Exception {
        KitFiles.Write w = KitFiles.writeNeverClobber(root.toFile(), "a/b/c.txt", "hi");
        assertThat(w.changed()).isTrue();
        assertThat(w.status()).isEqualTo("written");
        assertThat(Files.readString(root.resolve("a/b/c.txt"))).isEqualTo("hi");
    }

    @Test
    @DisplayName("An identical file is left untouched, reported unchanged")
    void identicalUntouched() throws Exception {
        KitFiles.writeNeverClobber(root.toFile(), "f.txt", "same");
        KitFiles.Write w = KitFiles.writeNeverClobber(root.toFile(), "f.txt", "same");
        assertThat(w.changed()).isFalse();
        assertThat(w.status()).isEqualTo("already exists, untouched");
    }

    @Test
    @DisplayName("A differing file is kept; the proposal lands as .suggested")
    void differingSuggested() throws Exception {
        Files.writeString(root.resolve("f.txt"), "mine");
        KitFiles.Write w = KitFiles.writeNeverClobber(root.toFile(), "f.txt", "theirs");
        assertThat(Files.readString(root.resolve("f.txt"))).isEqualTo("mine");
        assertThat(w.path()).isEqualTo("f.txt.suggested");
        assertThat(Files.readString(root.resolve("f.txt.suggested"))).isEqualTo("theirs");
        assertThat(w.changed()).isTrue();
    }

    @Test
    @DisplayName("An existing .suggested is never overwritten — skipped")
    void suggestedNeverOverwritten() throws Exception {
        Files.writeString(root.resolve("f.txt"), "mine");
        Files.writeString(root.resolve("f.txt.suggested"), "old proposal");
        KitFiles.Write w = KitFiles.writeNeverClobber(new File(root.toFile(), ""), "f.txt", "new");
        assertThat(w.changed()).isFalse();
        assertThat(w.status()).contains("both exist");
        assertThat(Files.readString(root.resolve("f.txt.suggested"))).isEqualTo("old proposal");
    }

    @Test
    @DisplayName("shortName: a short name is kept verbatim (stripped)")
    void shortNameKeepsShort() {
        assertThat(KitFiles.shortName("Acme")).isEqualTo("Acme");
        assertThat(KitFiles.shortName("  Acme  ")).isEqualTo("Acme");
        assertThat(KitFiles.shortName("Twelve chars")).isEqualTo("Twelve chars"); // exactly 12
    }

    @Test
    @DisplayName("shortName: a long name is cut to 12 code points with no trailing space")
    void shortNameTruncatesCleanly() {
        // "Acme Rocket Shipping Co" cut at 12 lands on a space -> stripped
        assertThat(KitFiles.shortName("Acme Rocket Shipping Co"))
                .isEqualTo("Acme Rocket")
                .doesNotEndWith(" ");
    }

    @Test
    @DisplayName("shortName: truncation never strands a lone surrogate (v1.287.0 class)")
    void shortNameNeverSplitsSurrogate() {
        // 11 ASCII then an astral emoji (the 12th code point). The buggy
        // substring(0,12) keeps 11 chars + the emoji's HIGH surrogate ALONE
        // — a stranded unit that mints an invalid JSON string. A code-point
        // cut keeps the emoji WHOLE (both surrogate units) as the 12th glyph.
        String name = "AAAAAAAAAAA🚀Rocket"; // 11 'A' + 🚀 + Rocket
        String out = KitFiles.shortName(name);
        assertThat(out).isEqualTo("AAAAAAAAAAA🚀"); // emoji kept whole
        // a lone (unpaired) surrogate does not survive a UTF-8 round trip —
        // it decodes back as U+FFFD. The buggy substring(0,12) would strand
        // the emoji's high surrogate and fail this; a code-point cut passes.
        assertThat(new String(out.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8))
                .as("no lone surrogate: the string round-trips through UTF-8")
                .isEqualTo(out);
    }
}
