package org.nmox.studio.rack.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** The bounded literal search (v2.79.0): case-folded, heavy dirs skipped, binaries skipped, hit cap and clip reported. */
class TextSearchTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("finds literal lines case-insensitively with relative files and 1-based lines; skips node_modules and binaries")
    void findsAndSkips() throws Exception {
        Files.writeString(root.resolve("a.js"), "const x = 1;\nfetch('/api/Checkout');\n");
        Path src = Files.createDirectories(root.resolve("src"));
        Files.writeString(src.resolve("b.js"), "// checkout here\n");
        Path nm = Files.createDirectories(root.resolve("node_modules/lib"));
        Files.writeString(nm.resolve("c.js"), "checkout in a dependency\n");
        Files.write(root.resolve("blob.bin"), new byte[] {'c', 'h', 'e', 'c', 'k', 'o', 'u', 't', 0, 1, 2});
        TextSearch.Answer a = TextSearch.search(root, "CHECKOUT", 10);
        assertThat(a.hits()).extracting(TextSearch.Hit::file).containsExactlyInAnyOrder("a.js", "src/b.js");
        assertThat(a.hits().stream().filter(h -> h.file().equals("a.js")).findFirst().orElseThrow().line()).isEqualTo(2);
        assertThat(a.truncated()).isFalse();
        assertThat(a.filesScanned()).isEqualTo(2);
        assertThat(TextSearch.search(root, "  ", 10).hits()).isEmpty();
        assertThat(TextSearch.search(root, "nonesuch", 10).hits()).isEmpty();
    }

    @Test
    @DisplayName("the hit cap is reported as truncated and long lines are clipped code-point-safely")
    void capsAndClips() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("needle ").append(i).append('\n');
        }
        sb.append("needle ").append("\u00e9".repeat(400)).append('\n');
        Files.writeString(root.resolve("many.txt"), sb.toString());
        TextSearch.Answer capped = TextSearch.search(root, "needle", 100);
        assertThat(capped.hits()).hasSize(TextSearch.MAX_HITS);
        assertThat(capped.truncated()).isTrue();
        TextSearch.Answer three = TextSearch.search(root, "needle", 3);
        assertThat(three.hits()).hasSize(3);
        assertThat(three.truncated()).isTrue();
        Files.writeString(root.resolve("many.txt"), "needle " + "\u00e9".repeat(400) + "\n");
        TextSearch.Hit longLine = TextSearch.search(root, "needle", 5).hits().get(0);
        assertThat(longLine.text()).hasSize(TextSearch.MAX_LINE + 1).endsWith("\u2026");
        assertThat(Texts.of(TextSearch.toJson("needle", three).put("available", true))).startsWith("many.txt:1 ").contains("(more matches than shown");
    }

    @Test
    @DisplayName("secret-bearing files are never searched: .env values, npmrc tokens, private keys stay out of an agent's reach (v2.84.0)")
    void secretsNeverSearched() throws Exception {
        java.nio.file.Files.createDirectories(root.resolve("src"));
        java.nio.file.Files.writeString(root.resolve(".env"), "API_KEY=hunter2-secret\n");
        java.nio.file.Files.writeString(root.resolve(".env.local"), "DB_PASSWORD=hunter2-secret\n");
        java.nio.file.Files.writeString(root.resolve(".npmrc"), "//registry.npmjs.org/:_authToken=hunter2-secret\n");
        java.nio.file.Files.writeString(root.resolve("server.key"), "hunter2-secret\n");
        java.nio.file.Files.writeString(root.resolve("src/app.js"), "const marker = 'hunter2-secret';\n");
        java.nio.file.Files.writeString(root.resolve(".env.example"), "API_KEY=hunter2-secret\n");
        TextSearch.Answer a = TextSearch.search(root, "hunter2-secret", 50);
        assertThat(a.hits()).extracting(TextSearch.Hit::file).containsExactly("src/app.js");
        assertThat(a.filesScanned()).as("the skipped files are not even counted as scanned").isEqualTo(1);
        assertThat(TextSearch.relativeFiles(root)).as("nor listed for completion").containsExactly("src/app.js");
        assertThat(TextSearch.isSecretBearing(".ENV")).isTrue();
        assertThat(TextSearch.isSecretBearing("id_ed25519")).isTrue();
        assertThat(TextSearch.isSecretBearing("cert.PEM")).isTrue();
        assertThat(TextSearch.isSecretBearing("environment.ts")).isFalse();
        assertThat(TextSearch.isSecretBearing("keys.js")).isFalse();
        assertThat(TextSearch.isSecretBearing("pem")).as("a bare name is not an extension").isFalse();
    }
}
