package org.nmox.studio.core.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bounded reader's behaviour, not its shape: an oversize file is
 * refused UNREAD and the refusal names the size; an ordinary file comes
 * back byte for byte.
 */
class BoundedReadsTest {

    @Test
    @DisplayName("an ordinary file reads unchanged, text and lines alike")
    void ordinaryFileReadsUnchanged(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("package.json");
        String content = "{\n  \"name\": \"café\",\n  \"main\": \"index.js\"\n}\n";
        Files.writeString(f, content, StandardCharsets.UTF_8);

        assertThat(BoundedReads.read(f)).isEqualTo(content);
        assertThat(BoundedReads.read(f.toFile())).isEqualTo(content);
        assertThat(BoundedReads.readLines(f))
                .containsExactly("{", "  \"name\": \"café\",", "  \"main\": \"index.js\"", "}");
    }

    @Test
    @DisplayName("a file exactly at the ceiling still reads — the cap refuses only what is OVER it")
    void aFileAtTheCeilingReads(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("edge.json");
        Files.writeString(f, "abcdefgh", StandardCharsets.UTF_8); // 8 bytes
        assertThat(BoundedReads.read(f, 8)).isEqualTo("abcdefgh");
    }

    @Test
    @DisplayName("an oversize file is refused BEFORE a byte is read, and the refusal names its size")
    void oversizeFileIsRefusedUnread(@TempDir Path tmp) throws Exception {
        Path big = tmp.resolve(".nmoxtasks.json");
        try (RandomAccessFile raf = new RandomAccessFile(big.toFile(), "rw")) {
            // sparse: the SIZE is the whole point — a real 8 MiB write would
            // only prove the machine can write 8 MiB
            raf.setLength(BoundedReads.DEFAULT_MAX_BYTES + 1);
        }
        long expectedKib = (BoundedReads.DEFAULT_MAX_BYTES + 1) / 1024;

        assertThatThrownBy(() -> BoundedReads.read(big))
                .isInstanceOf(BoundedReads.TooLarge.class)
                .isInstanceOf(IOException.class)
                .hasMessageContaining(".nmoxtasks.json")
                .hasMessageContaining(expectedKib + " KiB")
                .hasMessageContaining("8 MiB cap")
                .hasMessageContaining("not read");

        assertThatThrownBy(() -> BoundedReads.readLines(big))
                .as("the line-oriented door carries the same ceiling")
                .isInstanceOf(BoundedReads.TooLarge.class);

        assertThat(big).as("refused, never moved aside or changed").exists();
        assertThat(Files.size(big)).isEqualTo(BoundedReads.DEFAULT_MAX_BYTES + 1);
    }

    @Test
    @DisplayName("the refusal carries the measured size so a caller can word its own message")
    void refusalCarriesTheMeasurements(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("manifest.json");
        Files.writeString(f, "0123456789", StandardCharsets.UTF_8);
        try {
            BoundedReads.read(f, 4);
            throw new AssertionError("expected a refusal");
        } catch (BoundedReads.TooLarge tooLarge) {
            assertThat(tooLarge.fileName()).isEqualTo("manifest.json");
            assertThat(tooLarge.size()).isEqualTo(10);
            assertThat(tooLarge.maxBytes()).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("a caller wording its own refusal gets the product's voice — a sub-MiB cap reads in KiB")
    void refusalWordingIsShared() {
        assertThat(BoundedReads.refusal("Rack patch", "x.json", 9L * 1024 * 1024, 8L * 1024 * 1024))
                .isEqualTo("Rack patch x.json is 9216 KiB, over the 8 MiB cap — not read");
        assertThat(BoundedReads.refusal("", "x.json", 200_000, 64 * 1024))
                .as("a ceiling that is not a whole number of mebibytes must not read '0 MiB'")
                .isEqualTo("x.json is 195 KiB, over the 64 KiB cap — not read");
    }

    @Test
    @DisplayName("a missing file fails as I/O, not as a size refusal")
    void missingFileIsPlainIo(@TempDir Path tmp) {
        assertThatThrownBy(() -> BoundedReads.read(tmp.resolve("absent.json")))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(BoundedReads.TooLarge.class);
    }
}
