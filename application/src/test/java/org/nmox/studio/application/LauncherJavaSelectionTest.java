package org.nmox.studio.application;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Java-selection block in nmoxstudio.conf is what stands between a
 * fresh checkout and the platform launcher picking sdkman's Java 8 and
 * spawning a JVM that refuses to run and never exits. These tests
 * source the real conf file in a controlled /bin/sh with fixture JDKs
 * (stub {@code bin/java} scripts that print a version banner) and
 * assert which {@code jdkhome} comes out — the same way the real
 * launcher consumes it.
 */
@DisabledOnOs(OS.WINDOWS)
class LauncherJavaSelectionTest {

    private static Path conf;

    @TempDir
    Path tmp;

    @BeforeAll
    static void locateConf() {
        conf = Path.of("src/main/resources/nmoxstudio.conf").toAbsolutePath();
        assertThat(conf).exists();
    }

    /** Creates a fake JDK home whose bin/java -version reports {@code banner}. */
    private Path fakeJdk(String name, String banner) throws IOException {
        Path home = tmp.resolve(name);
        Files.createDirectories(home.resolve("bin"));
        Path java = home.resolve("bin/java");
        Files.writeString(java, "#!/bin/sh\necho 'openjdk version \"" + banner
                + "\"' >&2\n");
        assertThat(java.toFile().setExecutable(true)).isTrue();
        return home;
    }

    /**
     * Sources the conf with the given env and launcher args; prints the
     * resulting jdkhome. Returns [exitCode, stdout].
     */
    private Object[] sourceConf(Map<String, String> env, String... launcherArgs)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("/bin/sh", "-c",
                ". '" + conf + "'; printf '%s' \"$jdkhome\"", "sh"));
        cmd.addAll(List.of(launcherArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().remove("JAVA_HOME");
        pb.environment().remove("jdkhome");
        // default the probes to nowhere so the host machine's JDKs can't leak in
        pb.environment().put("nmox_java_home_cmd", "/nonexistent-java-home-cmd");
        pb.environment().put("nmox_jvm_dirs", "/nonexistent-jvm-dir/*");
        pb.environment().put("PATH", "/usr/bin:/bin");
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).isTrue();
        return new Object[]{p.exitValue(), out};
    }

    @Test
    @DisplayName("JAVA_HOME pointing at a 21+ JDK is honored")
    void javaHomeHonored() throws Exception {
        Path jdk = fakeJdk("jdk21", "21.0.3");
        Object[] r = sourceConf(Map.of("JAVA_HOME", jdk.toString()));
        assertThat(r[0]).isEqualTo(0);
        assertThat(r[1].toString()).endsWith(jdk.toString());
    }

    @Test
    @DisplayName("An old JAVA_HOME (1.8) is passed over for the newest scanned 21+ JDK")
    void oldJavaHomeSkippedForScan() throws Exception {
        Path jdk8 = fakeJdk("jdk8", "1.8.0_402");
        Path jdk21 = fakeJdk("jvm/a-21/Contents/Home", "21.0.3");
        Path jdk23 = fakeJdk("jvm/b-23/Contents/Home", "23.0.1");
        Object[] r = sourceConf(Map.of(
                "JAVA_HOME", jdk8.toString(),
                "nmox_jvm_dirs", tmp.resolve("jvm") + "/*/Contents/Home"));
        assertThat(r[0]).isEqualTo(0);
        assertThat(r[1].toString())
                .as("newest of the scanned JDKs wins")
                .endsWith(jdk23.toString())
                .doesNotContain("a-21");
    }

    @Test
    @DisplayName("The java_home query is preferred over the directory scan")
    void javaHomeCmdPreferred() throws Exception {
        Path queried = fakeJdk("queried-jdk", "22.0.1");
        Path cmd = tmp.resolve("java_home");
        Files.writeString(cmd, "#!/bin/sh\nprintf '%s' '" + queried + "'\n");
        assertThat(cmd.toFile().setExecutable(true)).isTrue();
        Object[] r = sourceConf(Map.of("nmox_java_home_cmd", cmd.toString()));
        assertThat(r[0]).isEqualTo(0);
        assertThat(r[1].toString()).endsWith(queried.toString());
    }

    @Test
    @DisplayName("No suitable Java anywhere: clean exit 3 with an actionable message")
    void nothingSuitableFailsFast() throws Exception {
        // the fake 8 sits FIRST on PATH so the host's real java (which
        // /usr/bin/java would otherwise resolve) can't leak in — this also
        // exercises the last-resort rejecting an old PATH java
        Path jdk8 = fakeJdk("only-jdk8", "1.8.0_402");
        Object[] r = sourceConf(Map.of(
                "JAVA_HOME", jdk8.toString(),
                "PATH", jdk8.resolve("bin") + File.pathSeparator + "/usr/bin:/bin"));
        assertThat(r[0]).isEqualTo(3);
        assertThat(r[1].toString())
                .contains("Java 21 or newer")
                .contains("--jdkhome");
    }

    @Test
    @DisplayName("--jdkhome on the launcher command line disables the block, including fail-fast")
    void cliJdkhomeStepsAside() throws Exception {
        // no suitable java anywhere, but the user said --jdkhome: the
        // conf must not exit 3 — the platform launcher parses the flag
        Object[] r = sourceConf(Map.of(), "--jdkhome", "/some/jdk", "--nosplash");
        assertThat(r[0]).isEqualTo(0);
        assertThat(r[1].toString()).isEmpty();
    }

    @Test
    @DisplayName("A pre-set jdkhome (installer's bundled JRE) is never touched")
    void presetJdkhomeUntouched() throws Exception {
        List<String> cmd = List.of("/bin/sh", "-c",
                "jdkhome=jre; . '" + conf + "'; printf '%s' \"$jdkhome\"");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().remove("JAVA_HOME");
        pb.environment().put("nmox_java_home_cmd", "/nonexistent");
        pb.environment().put("nmox_jvm_dirs", "/nonexistent/*");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).isEqualTo(0);
        assertThat(out).isEqualTo("jre");
    }

    @Test
    @DisplayName("PATH java at 21+ is accepted as the silent last resort (jdkhome stays empty)")
    void pathJavaLastResort() throws Exception {
        Path jdk = fakeJdk("path-jdk", "21.0.2");
        Object[] r = sourceConf(Map.of(
                "PATH", jdk.resolve("bin") + File.pathSeparator + "/usr/bin:/bin"));
        assertThat(r[0]).isEqualTo(0);
        assertThat(r[1].toString())
                .as("left empty so the platform launcher resolves the PATH java itself")
                .isEmpty();
    }
}
