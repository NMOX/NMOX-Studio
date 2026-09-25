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

    // ---- the refusal speaks once per fact, not once per read ---------------

    /**
     * The refusal is logged HERE so every caller speaks by construction —
     * which means this class also owns how OFTEN it speaks. Several of the
     * callers re-read the same file forever: {@code WebProject.getDisplayName}
     * runs on a Projects-tree PAINT and caches only successes, so an
     * over-cap {@code package.json} in a cloned repo would reach this refusal
     * on every repaint, writing a WARNING to {@code messages.log} from the
     * EDT each time. A log flood driven by a stranger's file is the same
     * class of defect as the heap this cap exists to protect.
     */
    private static java.util.List<java.util.logging.LogRecord> tapRefusals(Runnable body) {
        java.util.logging.Logger log =
                java.util.logging.Logger.getLogger(BoundedReads.class.getName());
        java.util.List<java.util.logging.LogRecord> seen =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.logging.Handler tap = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                seen.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        log.addHandler(tap);
        try {
            body.run();
        } finally {
            log.removeHandler(tap);
        }
        return seen;
    }

    private static void refuse(Path file) {
        try {
            BoundedReads.read(file, 4);
        } catch (IOException expected) {
            // the refusal is the point; what it LOGS is what this measures
        }
    }

    @Test
    @DisplayName("a file refused over and over is named once, not once per read")
    void theRefusalSpeaksOncePerFact(@TempDir Path tmp) throws IOException {
        Path big = tmp.resolve("package.json");
        Files.writeString(big, "0123456789", StandardCharsets.UTF_8);

        var records = tapRefusals(() -> {
            for (int paint = 0; paint < 50; paint++) {
                refuse(big);
            }
        });

        assertThat(records)
                .as("a paint loop over one over-cap file must not write 50 WARNINGs — "
                        + "the reader says a fact once")
                .hasSize(1);
        assertThat(records.get(0).getLevel()).isEqualTo(java.util.logging.Level.WARNING);
    }

    @Test
    @DisplayName("one file's silence never silences another's refusal")
    void everyRefusedFileGetsItsOwnSentence(@TempDir Path tmp) throws IOException {
        Path a = tmp.resolve("a.json");
        Path b = tmp.resolve("b.json");
        Files.writeString(a, "0123456789", StandardCharsets.UTF_8);
        Files.writeString(b, "0123456789", StandardCharsets.UTF_8);

        var records = tapRefusals(() -> {
            refuse(a);
            refuse(a);
            refuse(b);
            refuse(b);
        });

        assertThat(records).hasSize(2);
        assertThat(records.stream().map(r -> String.valueOf(r.getParameters()[0])).toList())
                .as("each file is named in its own refusal")
                .anyMatch(s -> s.contains("a.json"))
                .anyMatch(s -> s.contains("b.json"));
    }

    @Test
    @DisplayName("a file that changed size is a new fact, and the reader says it again")
    void aChangedSizeSpeaksAgain(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("grown.json");
        Files.writeString(f, "0123456789", StandardCharsets.UTF_8);
        refuse(f);

        Files.writeString(f, "0123456789abcdef", StandardCharsets.UTF_8);
        var records = tapRefusals(() -> refuse(f));

        assertThat(records)
                .as("silence is per FACT: the file is a different size now, so the reader "
                        + "must not stay quiet about it")
                .hasSize(1);
    }

    @Test
    @DisplayName("a link to /dev/zero reports size 0 and never ends: it is refused as not a regular file, not read")
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void aDeviceIsNotAFile(@TempDir Path dir) throws Exception {
        Path link = Files.createSymbolicLink(dir.resolve("README.md"), Path.of("/dev/zero"));
        assertThat(Files.size(link)).as("the size check alone lets it through").isZero();
        assertThatThrownBy(() -> BoundedReads.read(link, 1024)).hasMessageContaining("not a regular file");
        assertThatThrownBy(() -> BoundedReads.read(dir, 1024)).hasMessageContaining("not a regular file");
    }

    @Test
    @DisplayName("text that is not UTF-8 is still refused, as the whole-file read did")
    void malformedStillThrows(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("bad.txt");
        Files.write(f, new byte[] {'a', (byte) 0xff, 'b'});
        assertThatThrownBy(() -> BoundedReads.read(f, 1024)).isInstanceOf(java.nio.charset.CharacterCodingException.class);
        Files.writeString(f, "héllo ✓");
        assertThat(BoundedReads.read(f, 1024)).isEqualTo("héllo ✓");
    }
}
