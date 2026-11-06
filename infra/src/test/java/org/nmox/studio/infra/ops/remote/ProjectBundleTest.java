package org.nmox.studio.infra.ops.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** What travels in a deploy, what stays, and that the tar is a tar. */
class ProjectBundleTest {

    @Test
    @DisplayName("heavy dirs, .git and .env stay home; a symlink is skipped by name; the rest travels")
    void plan(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("Dockerfile"), "FROM scratch\nEXPOSE 3000\n");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/index.js"), "console.log(1)");
        Files.createDirectories(root.resolve("node_modules/x"));
        Files.writeString(root.resolve("node_modules/x/i.js"), "x");
        Files.createDirectories(root.resolve(".git"));
        Files.writeString(root.resolve(".git/HEAD"), "ref");
        Files.writeString(root.resolve(".env"), "SECRET=1");
        Files.writeString(root.resolve(".env.production"), "SECRET=2");
        try {
            Files.createSymbolicLink(root.resolve("link"), root.resolve("src"));
        } catch (IOException | UnsupportedOperationException noLinks) {
            // a filesystem without symlinks: the skip rule is still covered by the other entries
        }
        ProjectBundle.Manifest m = ProjectBundle.plan(root);
        assertThat(m.refused()).isFalse();
        assertThat(m.files()).containsExactly("Dockerfile", "src/index.js");
        assertThat(m.skipped()).anyMatch(s -> s.startsWith("node_modules/"))
                .anyMatch(s -> s.startsWith(".git/"))
                .anyMatch(s -> s.startsWith(".env ("))
                .anyMatch(s -> s.startsWith(".env.production ("));
    }

    @Test
    @DisplayName("past the caps the bundle is refused whole, never cut")
    void caps(@TempDir Path root) throws IOException {
        for (int i = 0; i < 5; i++) {
            Files.writeString(root.resolve("f" + i), "0123456789");
        }
        assertThat(ProjectBundle.plan(root, 3, 1_000_000).refusal()).contains("more than 3 files");
        assertThat(ProjectBundle.plan(root, 100, 25).refusal()).contains("MB");
        assertThat(ProjectBundle.plan(root, 100, 1_000_000).refused()).isFalse();
    }

    @Test
    @DisplayName("the written tar.gz lists the planned entries with their sizes and modes")
    void tar(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("Dockerfile"), "FROM scratch\n");
        Files.createDirectories(root.resolve("bin"));
        Path script = root.resolve("bin/run.sh");
        Files.writeString(script, "#!/bin/sh\necho hi\n");
        script.toFile().setExecutable(true);
        ProjectBundle.Manifest m = ProjectBundle.plan(root);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProjectBundle.write(root, m, out);
        List<String> entries = new ArrayList<>();
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            byte[] header = new byte[512];
            while (in.readNBytes(header, 0, 512) == 512 && header[0] != 0) {
                String name = new String(header, 0, 100, StandardCharsets.UTF_8).trim().replace("\0", "");
                long size = Long.parseLong(new String(header, 124, 11, StandardCharsets.US_ASCII).trim(), 8);
                String mode = new String(header, 100, 7, StandardCharsets.US_ASCII).trim();
                String magic = new String(header, 257, 5, StandardCharsets.US_ASCII);
                assertThat(magic).isEqualTo("ustar");
                entries.add(name + ":" + size + ":" + mode);
                long skip = (size + 511) / 512 * 512;
                in.readNBytes((int) skip);
            }
        }
        assertThat(entries).containsExactly("Dockerfile:13:0000644", "bin/:0:0000755", "bin/run.sh:18:0000755");
        // and the system's own tar agrees, where there is one
        Path file = root.resolve("bundle.tgz");
        Files.write(file, out.toByteArray());
        try {
            Process p = new ProcessBuilder("tar", "tzf", file.toString()).redirectErrorStream(true).start();
            String listing = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() == 0) {
                assertThat(listing.lines().map(String::trim).toList()).containsExactly("Dockerfile", "bin/", "bin/run.sh");
            }
        } catch (IOException | InterruptedException noTar) {
            // no tar on this machine: the Java read above stands
        }
    }
}
