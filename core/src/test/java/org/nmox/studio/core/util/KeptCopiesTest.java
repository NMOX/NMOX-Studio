package org.nmox.studio.core.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A kept copy is never overwritten (3.4): the second rescue of a file that
 * went bad twice used to replace the first one's bytes.
 */
class KeptCopiesTest {

    private static void write(File f, String text) throws Exception {
        Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the first copy is <name>.bak")
    void firstIsBak(@TempDir File dir) throws Exception {
        File f = new File(dir, ".nmoxapi.json");
        write(f, "first");
        File copy = KeptCopies.copyAside(f);
        assertThat(copy.getName()).isEqualTo(".nmoxapi.json.bak");
        assertThat(Files.readString(copy.toPath())).isEqualTo("first");
    }

    @Test
    @DisplayName("a second rescue takes a numbered sibling and the first survives")
    void secondNeverOverwritesFirst(@TempDir File dir) throws Exception {
        File f = new File(dir, ".nmoxdb.json");
        write(f, "first");
        File one = KeptCopies.copyAside(f);
        write(f, "second");
        File two = KeptCopies.copyAside(f);
        write(f, "third");
        File three = KeptCopies.copyAside(f);

        assertThat(two.getName()).isEqualTo(".nmoxdb.json.2.bak");
        assertThat(three.getName()).isEqualTo(".nmoxdb.json.3.bak");
        assertThat(Files.readString(one.toPath()))
                .as("the first rescue keeps the bytes it rescued").isEqualTo("first");
        assertThat(Files.readString(two.toPath())).isEqualTo("second");
        assertThat(Files.readString(three.toPath())).isEqualTo("third");
    }

    @Test
    @DisplayName("a gap is reused: the first FREE name, not the next number")
    void firstFreeName(@TempDir File dir) throws Exception {
        File f = new File(dir, ".nmoxinfra.json");
        write(f, "x");
        write(new File(dir, ".nmoxinfra.json.bak"), "old");
        write(new File(dir, ".nmoxinfra.json.3.bak"), "older");
        assertThat(KeptCopies.nextFree(f).getName()).isEqualTo(".nmoxinfra.json.2.bak");
    }
}
