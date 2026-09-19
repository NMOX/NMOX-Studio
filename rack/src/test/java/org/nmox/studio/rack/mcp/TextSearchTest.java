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
    @DisplayName("a capped WALK is a floor on the project, not a reason to stop reading: every listed file is still searched (v2.184.0)")
    void theWalkCapDoesNotEndTheSearch() throws Exception {
        // the FILE cap had never been exercised — only the HIT cap — which is
        // how `truncated` came to be one variable for both and ended the
        // per-file loop before its first iteration, so a project over the cap
        // answered filesScanned: 1. This repo has ~5,600 eligible files, so
        // that was every real search here.
        int files = TextSearch.MAX_FILES + 100;
        for (int i = 0; i < files; i++) {
            Files.writeString(root.resolve("f" + i + ".txt"), "alpha needle here\n");
        }

        TextSearch.Answer none = TextSearch.search(root, "nonesuch", 50);
        assertThat(none.filesScanned())
                .as("the walk's cap bounds what is READ, and every file it listed is read")
                .isEqualTo(TextSearch.MAX_FILES);
        assertThat(none.hits()).isEmpty();
        assertThat(none.truncated())
                .as("a capped walk is reported even when nothing matched — the answer is a floor, not a total")
                .isTrue();

        TextSearch.Answer many = TextSearch.search(root, "needle", TextSearch.MAX_HITS);
        assertThat(many.hits()).hasSize(TextSearch.MAX_HITS);
        assertThat(many.filesScanned())
                .as("one hit per file, so the hit cap stops it one file PAST the cap — the extra file is"
                        + " what proves a further match exists, which is what makes the hit cap exact;"
                        + " what it must never be is 1")
                .isEqualTo(TextSearch.MAX_HITS + 1);
        assertThat(many.truncated()).isTrue();
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
        // v2.85.0: the docker --env-file shape, htpasswd, and the secrets/credentials JSON conventions
        assertThat(TextSearch.isSecretBearing("production.env")).isTrue();
        assertThat(TextSearch.isSecretBearing(".htpasswd")).isTrue();
        assertThat(TextSearch.isSecretBearing("secrets.yaml")).isTrue();
        assertThat(TextSearch.isSecretBearing("credentials.json")).isTrue();
        assertThat(TextSearch.isSecretBearing("environment.json")).as("a name that merely contains env").isFalse();
    }
}
