package org.nmox.studio.rack.projectstudio;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The environment doctor's contract: the checklist covers the core
 * tools plus every learning-space interpreter exactly once, and a probe
 * tells the truth for both a tool that exists and one that doesn't.
 */
class EnvironmentDoctorTest {

    @Test
    @DisplayName("The checklist covers core tools and every REPL interpreter, no duplicates")
    void checklistCoverage() {
        List<String[]> checks = EnvironmentDoctor.checklist();
        List<String> tools = checks.stream().map(c -> c[0]).toList();
        assertThat(tools).contains("git", "node", "npm", "docker", "mvn");
        assertThat(tools).as("the LAMP stack is swept")
                .contains("composer", "mysql", "nginx", "apachectl");
        assertThat(tools).as("learning-space interpreters are swept")
                .contains("clisp", "sqlite3");
        assertThat(tools).as("the Web3 toolbelt is swept")
                .contains("forge", "anvil", "cast", "chisel", "solc", "slither", "solhint");
        assertThat(tools).as("the classic web toolbelt is swept")
                .contains("webpack", "grunt", "gulp", "bower", "coffee");
        assertThat(tools).as("the Svelte language server is swept")
                .contains("svelteserver");
        // the classics install from npm, and the hints say exactly how
        for (String[] check : checks) {
            switch (check[0]) {
                case "webpack" -> assertThat(check[2]).isEqualTo("npm install -g webpack-cli");
                case "grunt" -> assertThat(check[2]).isEqualTo("npm install -g grunt-cli");
                case "gulp" -> assertThat(check[2]).isEqualTo("npm install -g gulp-cli");
                case "bower" -> assertThat(check[2]).isEqualTo("npm install -g bower");
                case "coffee" -> assertThat(check[2]).isEqualTo("npm install -g coffeescript");
                case "svelteserver" -> assertThat(check[2]).isEqualTo("npm install -g svelte-language-server");
                default -> { }
            }
        }
        assertThat(tools).doesNotHaveDuplicates();
        assertThat(checks).allSatisfy(c -> {
            assertThat(c[1]).as("%s has a purpose", c[0]).isNotBlank();
        });
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("Probing a real tool reports found with a version line")
    void probeFindsRealTool() {
        // git exists on every dev/CI box this project supports
        EnvironmentDoctor.Finding f = EnvironmentDoctor.probe("git", "vcs", "");
        assertThat(f.found()).isTrue();
        assertThat(f.detail()).containsIgnoringCase("git");
    }

    @Test
    @DisplayName("Probing a missing tool reports not found, never throws")
    void probeMissesHonestly() {
        EnvironmentDoctor.Finding f = EnvironmentDoctor.probe(
                "nmox-no-such-tool-xyz", "nothing", "install hint");
        assertThat(f.found()).isFalse();
        assertThat(f.detail()).isEqualTo("not found");
        assertThat(f.installHint()).isEqualTo("install hint");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("A tool that prints then holds its pipe open still returns under the leash")
    void probeHonorsTimeoutOnWedgedPipe() {
        // The tool launches, prints a version line, then sleeps 30s holding
        // stdout open. runBounded's 4s leash must kill it and we still keep
        // the early line. The OLD hand-rolled probe drained to EOF BEFORE
        // waitFor, so it would block the full 30s — this elapsed assertion is
        // the mutation proof that the timeout is now real.
        List<String> wedged = List.of("sh", "-c",
                "echo NMOX-DOCTOR 9.9.9; sleep 30");
        long start = System.nanoTime();
        EnvironmentDoctor.Finding f =
                EnvironmentDoctor.probeWith("sh", "wedged", "", wedged);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(f.found()).isTrue();
        assertThat(f.detail()).contains("NMOX-DOCTOR 9.9.9");
        assertThat(elapsedMs)
                .as("the 4s leash fired instead of waiting out the 30s sleep")
                .isLessThan(15_000);
    }

    @Test
    @DisplayName("The version dialect is --version except for the documented holdouts")
    void versionDialectHoldouts() {
        assertThat(EnvironmentDoctor.versionCommand("git")).containsExactly("git", "--version");
        assertThat(EnvironmentDoctor.versionCommand("go")).containsExactly("go", "version");
        assertThat(EnvironmentDoctor.versionCommand("zig")).containsExactly("zig", "version");
        assertThat(EnvironmentDoctor.versionCommand("nginx")).containsExactly("nginx", "-v");
        assertThat(EnvironmentDoctor.versionCommand("apachectl")).containsExactly("apachectl", "-v");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("cwebp gets the -version dialect — --version dumps a usage banner")
    void cwebpDialect() {
        org.assertj.core.api.Assertions.assertThat(EnvironmentDoctor.versionCommand("cwebp"))
                .containsExactly("cwebp", "-version");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a failing version command is SAID, never quoted as a version")
    void failingVersionCommandIsSaidNotQuoted() {
        // the v1.303.0 Doctor walk: a crashing corepack pnpm shim printed a
        // stack-trace path and the Doctor showed it as the version with a ✓;
        // cwebp's usage banner rendered as "Usage:". Divergent input: the
        // mutant that drops the exit check QUOTES the garbage line instead.
        org.assertj.core.api.Assertions.assertThat(
                EnvironmentDoctor.detailFor(1, "Usage: cwebp [options] in_file"))
                .isEqualTo("found — but its version command failed (exit 1)");
        org.assertj.core.api.Assertions.assertThat(
                EnvironmentDoctor.detailFor(0, "1.6.0"))
                .isEqualTo("1.6.0");
        org.assertj.core.api.Assertions.assertThat(
                EnvironmentDoctor.detailFor(0, ""))
                .isEqualTo("found (version unknown)");
        // -1 is the TIMEOUT sentinel: a wedged tool that already printed
        // its version keeps it (the wedged-pipe test's own case)
        org.assertj.core.api.Assertions.assertThat(
                EnvironmentDoctor.detailFor(-1, "NMOX-DOCTOR 9.9.9"))
                .isEqualTo("NMOX-DOCTOR 9.9.9");
        String longLine = "x".repeat(100);
        org.assertj.core.api.Assertions.assertThat(
                EnvironmentDoctor.detailFor(0, longLine))
                .hasSize(73).endsWith("…");
    }

    @Test
    @DisplayName("a drop-in probe runs its declared args — the tool, then every flag")
    void customCommandCarriesTheArgs() {
        // divergent input for the mutant that drops the addAll: a probe
        // whose dialect ISN'T --version (kubectl version --client) would
        // silently probe with no args at all
        assertThat(EnvironmentDoctor.customCommand("kubectl",
                List.of("version", "--client")))
                .containsExactly("kubectl", "version", "--client");
    }

    @Test
    @DisplayName("a drop-in probe's row says whose it is, and a missing tool is honest")
    void probeCustomProvenanceAndHonesty() {
        EnvironmentDoctor.Finding f = EnvironmentDoctor.probeCustom(
                new UserProbes.Custom("nmox-no-such-tool-xyz", "team CLI",
                        List.of("--version"), "ask #tooling"));
        assertThat(f.found()).isFalse();
        assertThat(f.detail()).isEqualTo("not found");
        assertThat(f.installHint()).isEqualTo("ask #tooling");
        assertThat(f.purpose())
                .as("the family's provenance marker — the table must say"
                        + " which rows came from ~/.nmox/doctor.d")
                .endsWith(" · yours");
    }

    @Test
    @DisplayName("lua, odin and instantfpc speak their own version dialects (the v2.85.0 Doctor walk: three honest 'failed' rows that were really the probe's fault)")
    void luaOdinInstantfpcDialects() {
        assertThat(EnvironmentDoctor.versionCommand("lua")).containsExactly("lua", "-v");
        assertThat(EnvironmentDoctor.versionCommand("odin")).containsExactly("odin", "version");
        assertThat(EnvironmentDoctor.versionCommand("instantfpc")).containsExactly("instantfpc", "-h");
        assertThat(EnvironmentDoctor.versionCommand("pnpm")).as("a tool whose --version genuinely crashes keeps the default; its row stays honest").containsExactly("pnpm", "--version");
    }
}
