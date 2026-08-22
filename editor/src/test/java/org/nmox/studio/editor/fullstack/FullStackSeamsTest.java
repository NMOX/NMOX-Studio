package org.nmox.studio.editor.fullstack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full-stack seams (v2.31.0): env keys and the client→route jump.
 * Each refusal names the mutant that must die by it — the context
 * checks ARE the features' honesty.
 */
class FullStackSeamsTest {

    // ---- EnvKeys ----------------------------------------------------------

    @Test
    @DisplayName("declarations: offsets, export prefix, comments out, values truncated")
    void envDeclarations() {
        String env = "# comment KEY=nope\n"
                + "DATABASE_URL=postgres://u:p@localhost/db\n"
                + "export API_KEY='secret-value-longer-than-24-chars'\n"
                + "EMPTY=\n"
                + "not a key line\n"
                + "2BAD=starts-with-digit\n";
        List<EnvKeys.EnvKey> keys = EnvKeys.declarations(env, new File("x"));
        assertThat(keys).extracting(EnvKeys.EnvKey::name)
                .containsExactly("DATABASE_URL", "API_KEY", "EMPTY");
        assertThat(keys.get(0).offset()).isEqualTo(env.indexOf("DATABASE_URL"));
        assertThat(keys.get(1).offset()).isEqualTo(env.indexOf("API_KEY"));
        assertThat(keys.get(1).value())
                .as("secret values are truncated for display")
                .hasSize(22).endsWith("…");
    }

    @Test
    @DisplayName("scan: load order wins duplicates, only the root family is read")
    void envScan(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".env"), "SHARED=from-env\nONLY_ENV=1\n");
        Files.writeString(dir.resolve(".env.local"), "SHARED=from-local\nONLY_LOCAL=1\n");
        Path sub = dir.resolve("subdir");
        Files.createDirectory(sub);
        Files.writeString(sub.resolve(".env"), "NESTED=never-read\n");
        List<EnvKeys.EnvKey> keys = EnvKeys.scan(dir.toFile());
        assertThat(keys).extracting(EnvKeys.EnvKey::name)
                .containsExactly("SHARED", "ONLY_ENV", "ONLY_LOCAL");
        assertThat(keys.get(0).file().getName())
                .as(".env outranks .env.local for the jump")
                .isEqualTo(".env");
    }

    @Test
    @DisplayName("keyPrefix fires after process.env. and import.meta.env., refuses elsewhere")
    void envPrefix() {
        assertThat(EnvKeys.keyPrefix("const u = process.env.")).isEmpty();
        assertThat(EnvKeys.keyPrefix("const u = process.env.DATA")).isEqualTo("DATA");
        assertThat(EnvKeys.keyPrefix("import.meta.env.VITE_")).isEqualTo("VITE_");
        assertThat(EnvKeys.keyPrefix("const u = env.DATA"))
                .as("a bare env object is not the accessor")
                .isNull();
        assertThat(EnvKeys.keyPrefix("myprocess.env.DATA")).isNull();
        assertThat(EnvKeys.keyPrefix("plain DATA")).isNull();
    }

    @Test
    @DisplayName("keySpanAt spans a key in an accessor, refuses prose")
    void envSpan() {
        String js = "const u = process.env.DATABASE_URL; log(DATABASE_URL);";
        int in = js.indexOf("DATABASE_URL") + 3;
        assertThat(EnvKeys.keySpanAt(js, in)).containsExactly(
                js.indexOf("DATABASE_URL"), js.indexOf("DATABASE_URL") + 12);
        int prose = js.lastIndexOf("DATABASE_URL") + 3;
        assertThat(EnvKeys.keySpanAt(js, prose)).isNull();
    }

    // ---- Routes -----------------------------------------------------------

    @Test
    @DisplayName("clientPathSpanAt: fetch/axios strings starting with /, nothing else")
    void clientSpan() {
        String js = "await fetch('/api/users'); axios.get(\"/api/x\");"
                + " log('/api/users'); fetch('users');";
        int inFetch = js.indexOf("/api/users") + 2;
        assertThat(Routes.clientPathSpanAt(js, inFetch)).containsExactly(
                js.indexOf("/api/users"), js.indexOf("/api/users") + 10);
        int inAxios = js.indexOf("/api/x") + 1;
        assertThat(Routes.clientPathSpanAt(js, inAxios)).containsExactly(
                js.indexOf("/api/x"), js.indexOf("/api/x") + 6);
        int inLog = js.lastIndexOf("/api/users") + 2;
        assertThat(Routes.clientPathSpanAt(js, inLog))
                .as("an arbitrary string is not a client call")
                .isNull();
        int noSlash = js.indexOf("'users'") + 2;
        assertThat(Routes.clientPathSpanAt(js, noSlash))
                .as("a relative word is not a route path")
                .isNull();
    }

    @Test
    @DisplayName("routesIn reads the v1.292.0 rule; findRoute sweeps the project")
    void routeSweep(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("server.js"),
                "const app = express();\n"
                + "app.get('/health', (req, res) => res.send('ok'));\n"
                // the receiver rule ALONE guards this line (v1.292.0
                // lesson: test each guard with its own discriminator —
                // 'get' is a real verb and the path is real, only the
                // receiver shape says cache is not a router)
                + "cache.get('/api/users', warm);\n"
                + "router.post(\"/api/users\", createUser);\n"
                + "app.use(express.json());\n");
        Path heavy = dir.resolve("node_modules");
        Files.createDirectory(heavy);
        Files.writeString(heavy.resolve("dep.js"), "app.get('/api/users', x);");

        Routes.Route r = Routes.findRoute(dir.toFile(), "/api/users");
        assertThat(r).isNotNull();
        assertThat(r.verb()).isEqualTo("post");
        assertThat(r.file().getName())
                .as("node_modules is never swept")
                .isEqualTo("server.js");
        assertThat(Routes.findRoute(dir.toFile(), "/nope")).isNull();
    }
}
