package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code cd myproject && nmox .} — the command a web developer types with
 * {@code code .} in their hands (dx-plan 3.1, row 2). The platform launcher
 * already hands a path, and the caller's working directory, to a running
 * instance (the CLI handshake: {@code org.netbeans.modules.openfile.Handler}
 * is the implicit {@code --open} option and resolves a relative name against
 * {@code Env.getCurrentDirectory()}, which {@code CLIHandler} fills from the
 * client's {@code user.dir}). What was missing was the door: nothing put a
 * command on PATH on macOS or Windows, and the Linux package called it
 * {@code nmox-studio}. And the platform's {@code --open} shows a folder with
 * no manifest as a raw explorer tab rather than aiming it, so every door
 * rewrites its arguments the same way: a folder becomes {@code --aim
 * <absolute>} (File ▸ Open Folder…'s own verb), a file {@code --open
 * <absolute>}, an option and its value pass through. A name that is not
 * there is refused BY THE LAUNCHER, before anything starts: {@code nmox:
 * <name>: no such file or folder} on stderr and exit 2. It used to pass
 * through "so the IDE's refusal names it", but every door starts the IDE
 * detached with its output discarded, so that refusal reached nobody and
 * {@code nmox missing} printed nothing and returned 0 (the 3.1 review,
 * finding 5). Both Unix launchers are RUN here against a missing path and
 * a stand-in IDE that records whether it ever started.
 *
 * <p>Four homes, one command, and this gate holds each one:
 *
 * <ul>
 *   <li><b>macOS</b> — the cask links the bundle's MAIN EXECUTABLE as
 *       {@code nmox}. Not a helper script elsewhere in the bundle: the main
 *       executable is the one file Gatekeeper already accepts being executed
 *       (3.0.0–3.0.2 were refused for a quarantined NESTED script run by its shebang).
 *       That puts the launcher behind a symlink, where {@code dirname "$0"}
 *       names Homebrew's {@code bin/} — so the launcher follows the link.
 *       Started through a link it backgrounds the IDE and returns; run by
 *       LaunchServices (the real path) it stays in the foreground, because
 *       LaunchServices tracks that process as the app. Both proven by running
 *       the launcher the build writes, against a stand-in bundle.
 *   <li><b>Linux</b> — {@code packaging/linux/nmox}, as {@code bin/nmox} in
 *       the tarball and {@code /usr/bin/nmox} in the .deb, run for real here.
 *   <li><b>Windows</b> — {@code nmox.cmd} in {@code {app}\cli} and an
 *       installer task that puts that folder on PATH (and takes it off
 *       again). {@code iscc} does not run on this machine; the
 *       {@code windows-installer-check} workflow installs, reinstalls and
 *       uninstalls for real. This gate holds the shape.
 *   <li><b>The cask's two homes</b> — {@link CaskGeneratorParityTest} holds
 *       them byte-identical; this gate holds that the stanza is there and
 *       names the file the bundle really declares as its executable.
 * </ul>
 */
class TerminalCommandGateTest {

    private static final Path BUILD_DMG = Path.of("..", "packaging", "macos", "build-dmg.sh");
    private static final Path LINUX_NMOX = Path.of("..", "packaging", "linux", "nmox");
    private static final Path BUILD_PACKAGES = Path.of("..", "packaging", "linux", "build-packages.sh");
    private static final Path ISS = Path.of("..", "packaging", "windows", "nmox-studio.iss");
    private static final Path NMOX_CMD = Path.of("..", "packaging", "windows", "nmox.cmd");
    private static final Path CASK = Path.of("..", "Casks", "nmox-studio.rb");
    private static final Path WORKFLOW = Path.of("..", ".github", "workflows", "release.yml");
    private static final Path WIN_CHECK = Path.of("..", ".github", "workflows", "windows-installer-check.yml");

    private static final String LAUNCHER_OPEN = "cat > \"$BUNDLE/Contents/MacOS/nmox-studio\" <<'LAUNCHER'\n";

    @TempDir
    Path tmp;

    private static String read(Path p) throws IOException {
        assertThat(p).as("%s exists", p).exists();
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** The bundle launcher exactly as build-dmg.sh writes it. */
    private static String launcher() throws IOException {
        String dmg = read(BUILD_DMG);
        int from = dmg.indexOf(LAUNCHER_OPEN);
        assertThat(from).as("build-dmg.sh writes the bundle launcher as a heredoc").isNotEqualTo(-1);
        int to = dmg.indexOf("\nLAUNCHER\n", from);
        assertThat(to).as("the launcher heredoc is closed").isGreaterThan(from);
        return dmg.substring(from + LAUNCHER_OPEN.length(), to + 1);
    }

    // ---------------------------------------------------------------- macOS

    @Test
    @DisplayName("macOS: through a chain of links the launcher finds its bundle, aims folders, opens files, and returns at once")
    @DisabledOnOs(OS.WINDOWS)
    void macLauncherFollowsLinksAndBackgrounds() throws Exception {
        Bundle b = bundle();
        // a relative hop and an absolute hop - both readlink branches
        Path hop = Files.createDirectories(tmp.resolve("hop"));
        Files.createSymbolicLink(hop.resolve("nmox-studio"), b.launcher);
        Path bin = Files.createDirectories(tmp.resolve("brew/bin"));
        Path nmox = bin.resolve("nmox");
        Files.createSymbolicLink(nmox, Path.of("../../hop/nmox-studio"));
        Path project = project();

        Process p = start(project, nmox.toString(), TYPED);
        assertThat(p.waitFor(20, TimeUnit.SECONDS))
                .as("started from a terminal (through a link) the launcher returns while the IDE is still running;"
                        + " launcher said: %s", slurp(tmp.resolve("launcher.out")))
                .isTrue();
        assertThat(p.exitValue()).as("and it returns success").isZero();
        assertThat(b.record).as("the IDE stand-in is still waiting to be released").doesNotExist();

        Files.writeString(b.release, "go");
        List<String> rec = awaitRecord(b.record);
        assertThat(rec.get(1)).as("found the bundle's bin/nmoxstudio through the links").isEqualTo("-J-Xdock:name=NMOX Studio");
        assertTranslated(rec, 2, project);
    }

    @Test
    @DisplayName("macOS: run by its real path (Finder, the Dock, open) the launcher stays in the foreground and passes arguments as given")
    @DisabledOnOs(OS.WINDOWS)
    void macLauncherRunDirectlyStaysInForeground() throws Exception {
        Bundle b = bundle();
        Files.writeString(b.release, "go"); // the stand-in exits at once
        Files.writeString(tmp.resolve("x.js"), "x");
        Process p = start(tmp, b.launcher.toString(), List.of("x.js"));
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).as("launcher finished").isTrue();
        assertThat(p.exitValue())
                .as("exec: the launcher IS the IDE process, so its exit is the IDE's (LaunchServices tracks it)")
                .isEqualTo(7);
        assertThat(b.record).as("the IDE ran before the launcher returned").exists();
        assertThat(Files.readAllLines(b.record)).as("LaunchServices' own arguments are not rewritten")
                .containsExactly(tmp.toRealPath().toString(), "-J-Xdock:name=NMOX Studio", "x.js");
    }

    @Test
    @DisplayName("macOS: started by LaunchServices in /, the IDE runs from home, so its Terminal does not open in /")
    @DisabledOnOs(OS.WINDOWS)
    void macLauncherLeavesTheRootDirectory() throws Exception {
        Bundle b = bundle();
        Files.writeString(b.release, "go");
        Path home = Files.createDirectories(tmp.resolve("home"));
        List<String> argv = List.of(b.launcher.toString());
        ProcessBuilder pb = new ProcessBuilder(argv).directory(new java.io.File("/")).redirectErrorStream(true)
                .redirectOutput(tmp.resolve("launcher-root.out").toFile());
        pb.environment().put("HOME", home.toString());
        Process p = pb.start();
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).as("launcher finished").isTrue();
        assertThat(Path.of(Files.readAllLines(b.record).get(0)).toRealPath())
                .as("the IDE's working directory").isEqualTo(home.toRealPath());
    }

    @Test
    @DisplayName("macOS: every way the launcher starts the platform hands the script to /bin/sh")
    void everyLaunchHandsTheScriptToSh() throws IOException {
        List<String> starts = new ArrayList<>();
        for (String line : launcher().split("\n")) {
            String t = line.strip();
            if (t.contains("\"$RES/bin/nmoxstudio\"") && !t.startsWith("#")) {
                starts.add(t);
            }
        }
        assertThat(starts).as("foreground and background, bundled runtime and fallback JDK").hasSize(4);
        for (String s : starts) {
            assertThat(s.startsWith("exec /bin/sh ") || s.startsWith("nohup /bin/sh "))
                    .as("a quarantined script run by its shebang is refused by Gatekeeper: %s", s).isTrue();
        }
    }

    // ---------------------------------------------------------------- Linux

    @Test
    @DisplayName("Linux: nmox, through a link, finds bin/nmoxstudio beside itself, aims folders, opens files, and returns")
    @DisabledOnOs(OS.WINDOWS)
    void linuxCommandFollowsLinkAndBackgrounds() throws Exception {
        Path appBin = Files.createDirectories(tmp.resolve("opt/nmox-studio/bin"));
        Path nmox = appBin.resolve("nmox");
        Files.copy(LINUX_NMOX, nmox);
        executable(nmox);
        Path record = tmp.resolve("record.txt");
        Path release = tmp.resolve("release");
        writeIde(appBin.resolve("nmoxstudio"), record, release, 0);
        Path usrBin = Files.createDirectories(tmp.resolve("usr/bin"));
        Files.createSymbolicLink(usrBin.resolve("nmox"), nmox); // what the .deb ships
        Path project = project();

        Process p = start(project, usrBin.resolve("nmox").toString(), TYPED);
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).as("nmox returns while the IDE runs").isTrue();
        assertThat(p.exitValue()).isZero();
        assertThat(record).as("the IDE stand-in is still waiting").doesNotExist();
        Files.writeString(release, "go");
        assertTranslated(awaitRecord(record), 1, project);
    }

    @Test
    @DisplayName("the two Unix launchers spell the argument rule identically")
    void unixLaunchersShareOneRule() throws IOException {
        assertThat(rule(read(LINUX_NMOX))).as("packaging/linux/nmox and the macOS bundle launcher")
                .isEqualTo(rule(launcher()));
    }

    @Test
    @DisplayName("Linux: nmox with no nmoxstudio beside it says so and fails")
    @DisabledOnOs(OS.WINDOWS)
    void linuxCommandRefusesOutLoud() throws Exception {
        Path lonely = tmp.resolve("nmox");
        Files.copy(LINUX_NMOX, lonely);
        executable(lonely);
        Process p = new ProcessBuilder(lonely.toString(), ".").redirectErrorStream(true)
                .redirectOutput(tmp.resolve("nmox.out").toFile()).start();
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).isEqualTo(1);
        assertThat(slurp(tmp.resolve("nmox.out"))).contains("cannot find nmoxstudio");
    }

    // ---------------------------------------------------------------- refusals

    /**
     * What a mistyped command looks like, and what the launcher must say
     * about it. Every row is refused in the shell, before the IDE starts:
     * a bare name that is not there, the value of an explicit --aim or
     * --open that is not there, a FILE given to --aim (which takes a
     * folder), and an option left without its value. The existing folder
     * and file in the same argv prove the refusal is not waiting for the
     * first argument - it is the whole argv or nothing.
     */
    static java.util.stream.Stream<Arguments> refusals() {
        return java.util.stream.Stream.of(
                Arguments.of(List.of(".", "nope.txt"), "nmox: nope.txt: no such file or folder"),
                Arguments.of(List.of("src/app.js", "gone/deeper"), "nmox: gone/deeper: no such file or folder"),
                Arguments.of(List.of("--aim", "missing"), "nmox: missing: no such file or folder"),
                Arguments.of(List.of("--open", "missing.js"), "nmox: missing.js: no such file or folder"),
                Arguments.of(List.of("--aim", "src/app.js"), "nmox: src/app.js: not a folder (--aim takes a folder)"),
                Arguments.of(List.of(".", "--aim"), "nmox: --aim needs a value"),
                Arguments.of(List.of("gone.js:42"), "nmox: gone.js:42: no such file or folder"),
                Arguments.of(List.of("src/app.js:x"), "nmox: src/app.js:x: no such file or folder"),
                Arguments.of(List.of("src:4"), "nmox: src:4: no such file or folder"),
                Arguments.of(List.of("--open", "gone.js:3"), "nmox: gone.js:3: no such file or folder"),
                // past nine digits the platform's int parse fails and it says
                // nothing, so the launcher refuses where someone can hear it
                Arguments.of(List.of("src/app.js:12345678901"), "nmox: src/app.js:12345678901: no such file or folder"),
                // VS Code's own flags with no counterpart: the platform answered
                // "Unknown option" to /dev/null and never started (measured)
                Arguments.of(List.of("--wait", "src/app.js"), "nmox: --wait is VS Code's and has no counterpart here"),
                Arguments.of(List.of("-d", "a", "b"), "nmox: -d is VS Code's and has no counterpart here"));
    }

    @ParameterizedTest(name = "Linux: nmox {0} is refused before anything starts")
    @MethodSource("refusals")
    @DisabledOnOs(OS.WINDOWS)
    void linuxCommandRefusesAMissingPath(List<String> typed, String said) throws Exception {
        Path appBin = Files.createDirectories(tmp.resolve("opt/nmox-studio/bin"));
        Path nmox = appBin.resolve("nmox");
        Files.copy(LINUX_NMOX, nmox);
        executable(nmox);
        Path started = tmp.resolve("started");
        writeTripwireIde(appBin.resolve("nmoxstudio"), started);
        assertRefused(start(project(), nmox.toString(), typed), said, started);
    }

    @ParameterizedTest(name = "macOS: nmox {0} is refused before anything starts")
    @MethodSource("refusals")
    @DisabledOnOs(OS.WINDOWS)
    void macLauncherRefusesAMissingPath(List<String> typed, String said) throws Exception {
        Bundle b = bundle();
        Path started = tmp.resolve("started");
        writeTripwireIde(b.launcher.resolveSibling("../Resources/nmoxstudio/bin/nmoxstudio").normalize(), started);
        Path bin = Files.createDirectories(tmp.resolve("brew/bin"));
        Path nmox = bin.resolve("nmox");
        Files.createSymbolicLink(nmox, b.launcher);
        assertRefused(start(project(), nmox.toString(), typed), said, started);
    }

    @Test
    @DisplayName("Linux: an explicit --aim naming a real folder is checked, accepted and handed over as typed")
    @DisabledOnOs(OS.WINDOWS)
    void linuxCommandAcceptsAnExplicitAim() throws Exception {
        Path appBin = Files.createDirectories(tmp.resolve("opt/nmox-studio/bin"));
        Path nmox = appBin.resolve("nmox");
        Files.copy(LINUX_NMOX, nmox);
        executable(nmox);
        Path record = tmp.resolve("record.txt");
        Path release = tmp.resolve("release");
        Files.writeString(release, "go");
        writeIde(appBin.resolve("nmoxstudio"), record, release, 0);
        Process p = start(project(), nmox.toString(), List.of("--aim", "src", "--open", "src/app.js"));
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).as("nmox returns").isTrue();
        assertThat(p.exitValue()).as("nmox said: %s", slurp(tmp.resolve("launcher.out"))).isZero();
        assertThat(awaitRecord(record).subList(1, 5)).as("the platform resolves an option's value itself")
                .containsExactly("--aim", "src", "--open", "src/app.js");
    }

    // ------------------------------------------------------ code -g's habit

    /**
     * {@code code -g file:line[:col]}: a name that is not there but reads
     * NAME:LINE or NAME:LINE:COL with NAME an existing file opens that file
     * at that line. The platform's own {@code --open} takes FILE:LINE (its
     * Handler splits at the last colon when the whole name is not a file)
     * and has no column, so the launcher drops one. {@code -g} and
     * {@code --goto} are VS Code's spelling and are accepted and dropped;
     * an explicit {@code --open NAME:LINE} passes as typed, the platform
     * resolving it.
     */
    static java.util.stream.Stream<Arguments> gotos() {
        return java.util.stream.Stream.of(
                Arguments.of(List.of("src/app.js:42"), List.of("--open", "@/src/app.js:42")),
                Arguments.of(List.of("src/app.js:42:7"), List.of("--open", "@/src/app.js:42")),
                Arguments.of(List.of("-g", "src/app.js:3:1"), List.of("--open", "@/src/app.js:3")),
                Arguments.of(List.of("--goto", "src/app.js:5"), List.of("--open", "@/src/app.js:5")),
                Arguments.of(List.of("-r", "src/app.js:6"), List.of("--open", "@/src/app.js:6")),
                Arguments.of(List.of("-n", "src/app.js:7"), List.of("--open", "@/src/app.js:7")),
                Arguments.of(List.of("--open", "src/app.js:9:2"), List.of("--open", "src/app.js:9")));
    }

    @ParameterizedTest(name = "Linux: nmox {0} opens the file at the line")
    @MethodSource("gotos")
    @DisabledOnOs(OS.WINDOWS)
    void linuxCommandGoesToALine(List<String> typed, List<String> want) throws Exception {
        Path appBin = Files.createDirectories(tmp.resolve("opt/nmox-studio/bin"));
        Path nmox = appBin.resolve("nmox");
        Files.copy(LINUX_NMOX, nmox);
        executable(nmox);
        Path record = tmp.resolve("record.txt");
        Path release = tmp.resolve("release");
        Files.writeString(release, "go");
        writeIde(appBin.resolve("nmoxstudio"), record, release, 0);
        Path project = project();
        Process p = start(project, nmox.toString(), typed);
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).as("nmox returns").isTrue();
        assertThat(p.exitValue()).as("nmox said: %s", slurp(tmp.resolve("launcher.out"))).isZero();
        assertGoto(awaitRecord(record).subList(1, 3), want, project);
    }

    @ParameterizedTest(name = "macOS: nmox {0} opens the file at the line")
    @MethodSource("gotos")
    @DisabledOnOs(OS.WINDOWS)
    void macLauncherGoesToALine(List<String> typed, List<String> want) throws Exception {
        Bundle b = bundle();
        Files.writeString(b.release, "go");
        Path bin = Files.createDirectories(tmp.resolve("brew/bin"));
        Path nmox = bin.resolve("nmox");
        Files.createSymbolicLink(nmox, b.launcher);
        Path project = project();
        Process p = start(project, nmox.toString(), typed);
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).as("nmox returns").isTrue();
        assertThat(p.exitValue()).as("nmox said: %s", slurp(tmp.resolve("launcher.out"))).isZero();
        List<String> rec = awaitRecord(b.record);
        int at = rec.indexOf("--open");
        assertThat(at).as("the launcher handed over an --open: %s", rec).isNotEqualTo(-1);
        assertGoto(rec.subList(at, at + 2), want, project);
    }

    /**
     * A file whose own name ends in {@code :digits} is a name first: with
     * {@code src/x:12} on disk, {@code src/x:12:3} is that file at line 3,
     * which only the first pass stopping on an existing file gets right (the
     * 3.1.0 review's surviving mutant). Trying NAME:LINE before the exact
     * name would hand the platform the same string for {@code src/x:12}, and
     * the platform opens an existing name as named - equivalent, so it is
     * pinned here by the case that does differ.
     */
    @ParameterizedTest(name = "Unix: a name ending in :12 is a name first ({0})")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"linux", "mac"})
    @DisabledOnOs(OS.WINDOWS)
    void aNameEndingInDigitsIsANameFirst(String which) throws Exception {
        Path project = project();
        Files.writeString(project.resolve("src/x:12"), "x");
        Path record;
        String command;
        if (which.equals("linux")) {
            Path appBin = Files.createDirectories(tmp.resolve("opt/nmox-studio/bin"));
            Path nmox = appBin.resolve("nmox");
            Files.copy(LINUX_NMOX, nmox);
            executable(nmox);
            record = tmp.resolve("record.txt");
            Files.writeString(tmp.resolve("release"), "go");
            writeIde(appBin.resolve("nmoxstudio"), record, tmp.resolve("release"), 0);
            command = nmox.toString();
        } else {
            Bundle b = bundle();
            Files.writeString(b.release, "go");
            Path link = Files.createDirectories(tmp.resolve("brew/bin")).resolve("nmox");
            Files.createSymbolicLink(link, b.launcher);
            record = b.record;
            command = link.toString();
        }
        Process p = start(project, command, List.of("src/x:12:3"));
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).as("nmox said: %s", slurp(tmp.resolve("launcher.out"))).isZero();
        List<String> rec = awaitRecord(record);
        String opened = rec.get(rec.indexOf("--open") + 1);
        assertThat(opened).endsWith("/src/x:12:3");
        assertThat(Path.of(opened.substring(0, opened.lastIndexOf(':'))).toRealPath())
                .isEqualTo(project.toRealPath().resolve("src/x:12"));
    }

    /** {@code @} in {@code want} stands for the project's real absolute path. */
    private static void assertGoto(List<String> got, List<String> want, Path project) throws IOException {
        assertThat(got.get(0)).isEqualTo(want.get(0));
        String w = want.get(1);
        if (w.startsWith("@/")) {
            String g = got.get(1);
            int colon = g.lastIndexOf(':');
            assertThat(Path.of(g.substring(0, colon))).as("by its absolute path").isAbsolute();
            assertThat(Path.of(g.substring(0, colon)).toRealPath())
                    .isEqualTo(project.toRealPath().resolve(w.substring(2, w.lastIndexOf(':'))));
            assertThat(g.substring(colon)).as("the line, and no column").isEqualTo(w.substring(w.lastIndexOf(':')));
        } else {
            assertThat(got.get(1)).isEqualTo(w);
        }
    }

    // ------------------------------------------------------------- --help

    /**
     * {@code nmox --help} went to the platform, whose own usage the detached
     * launch sent to /dev/null: the command printed nothing and exited 0
     * (measured). Each launcher now answers {@code -h}/{@code --help} itself,
     * on stdout, and starts nothing.
     */
    @ParameterizedTest(name = "Unix: nmox {0} prints the usage and starts nothing")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"--help", "-h"})
    @DisabledOnOs(OS.WINDOWS)
    void unixHelpSpeaks(String flag) throws Exception {
        Path appBin = Files.createDirectories(tmp.resolve("opt/nmox-studio/bin"));
        Path nmox = appBin.resolve("nmox");
        Files.copy(LINUX_NMOX, nmox);
        executable(nmox);
        Path started = tmp.resolve("started");
        writeTripwireIde(appBin.resolve("nmoxstudio"), started);
        Bundle b = bundle();
        writeTripwireIde(b.launcher.resolveSibling("../Resources/nmoxstudio/bin/nmoxstudio").normalize(), started);
        Path link = Files.createDirectories(tmp.resolve("brew/bin")).resolve("nmox");
        Files.createSymbolicLink(link, b.launcher);
        for (Path command : List.of(nmox, link)) {
            Process p = start(project(), command.toString(), List.of(".", flag));
            assertThat(p.waitFor(20, TimeUnit.SECONDS)).isTrue();
            String out = slurp(tmp.resolve("launcher.out"));
            assertThat(p.exitValue()).as("%s %s said: %s", command, flag, out).isZero();
            assertThat(out).startsWith(USAGE.get(0)).contains(USAGE.get(4));
        }
        Thread.sleep(1500);
        assertThat(started).as("--help starts nothing").doesNotExist();
    }

    /** The usage, as the Unix launchers print it. */
    private static final List<String> USAGE = List.of(
            "Usage: nmox [options] [folder | file[:line[:column]]]...",
            "",
            "  nmox .              aim NMOX Studio at this folder, as File > Open Folder... does",
            "  nmox src/app.js     open a file",
            "  nmox src/app.js:42  open it at line 42 (-g and --goto are accepted)",
            "  nmox                start NMOX Studio",
            "",
            "It returns at once; a second nmox hands its folder or files to the IDE",
            "already running. A name that is not there is refused here, before anything",
            "starts. VS Code's -r is accepted and -n opens in the one window;",
            "-w, -d, -a and -v have no counterpart and are refused. Any other",
            "option goes to the IDE unchanged.");

    @Test
    @DisplayName("all three launchers print the same usage")
    void everyLauncherPrintsTheSameUsage() throws IOException {
        for (String unix : List.of(read(LINUX_NMOX), launcher())) {
            int from = unix.indexOf("cat <<'USAGE'\n");
            assertThat(from).as("the usage is a quoted heredoc").isNotEqualTo(-1);
            String body = unix.substring(from + "cat <<'USAGE'\n".length(), unix.indexOf("\nUSAGE\n", from));
            assertThat(List.of(body.split("\n", -1))).isEqualTo(USAGE);
            assertThat(unix).contains("-h|--help) nmox_usage; exit 0 ;;\n");
        }
        String cmd = read(NMOX_CMD);
        int from = cmd.indexOf("\n:usage\n");
        assertThat(from).as("nmox.cmd has a :usage routine").isNotEqualTo(-1);
        List<String> shown = new ArrayList<>();
        for (String line : cmd.substring(from + 8).split("\n")) {
            if (!line.startsWith("echo(")) {
                assertThat(line).isEqualTo("exit /b 0");
                break;
            }
            // cmd's escapes: ^ before each of | & < > ^, and %% for %
            shown.add(line.substring(5).replaceAll("\\^(.)", "$1").replace("%%", "%"));
        }
        assertThat(shown).as("nmox.cmd's :usage, cmd's escapes undone").isEqualTo(USAGE);
        assertThat(cmd).contains("if /i \"%~1\"==\"--help\" goto usage\n").contains("if /i \"%~1\"==\"-h\" goto usage\n")
                .contains("if \"%~1\"==\"/?\" goto usage\n");
    }

    @Test
    @DisplayName("the refusal is spelled the same by all three launchers")
    void everyLauncherSpeaksTheSameRefusal() throws IOException {
        for (String unix : List.of(read(LINUX_NMOX), launcher())) {
            assertThat(unix).contains("printf 'nmox: %s: no such file or folder\\n' \"$a\" >&2\n")
                    .contains("printf 'nmox: %s: not a folder (--aim takes a folder)\\n' \"$a\" >&2\n")
                    .contains("printf 'nmox: %s needs a value\\n' \"$value\" >&2\n");
            assertThat(unix.split("\n\\s*exit 2( ;;)?\n", -1)).as("each of the five refusal sites exits 2").hasSize(6);
        }
        String cmd = read(NMOX_CMD);
        assertThat(cmd).contains(">&2 echo(nmox: !NMOX_A!: no such file or folder\n")
                .contains(">&2 echo(nmox: !NMOX_A!: not a folder ^(--aim takes a folder^)\n")
                .contains(">&2 echo(nmox: %NMOX_VALUE% needs a value\n");
        assertThat(cmd.split("\n\\s*exit /b 2\n", -1)).as("each of the four refusal sites exits 2").hasSize(5);
        assertThat(cmd).as("a bare name that is neither folder nor file nor NAME:LINE is refused, not passed on")
                .contains("if exist \"%~1\\*\" goto folder\nif exist \"%~1\" goto file\ncall :goto\n"
                        + "if errorlevel 1 goto missing\n");
        assertThat(cmd).as("an explicit --open's missing value gets the same NAME:LINE chance, then the refusal")
                .contains(":openline\n").contains("call :goto\nif errorlevel 1 goto missing\nset \"NMOX_A=%NMOX_GF%:%NMOX_GL%\"\n");
    }

    @Test
    @DisplayName("Linux: the tarball carries bin/nmox and the .deb puts /usr/bin/nmox on PATH")
    void linuxPackagesShipTheCommand() throws IOException {
        String sh = read(BUILD_PACKAGES);
        assertThat(sh).as("the tarball ships the command beside the launcher")
                .contains("install -m 755 packaging/linux/nmox \"$TAR_STAGE/nmox-studio-$VERSION/bin/nmox\"\n");
        assertThat(sh).as("the .deb ships it under /opt")
                .contains("install -m 755 packaging/linux/nmox \"$DEB_STAGE/opt/nmox-studio/bin/nmox\"\n");
        assertThat(sh).as("and links it onto PATH")
                .contains("ln -s /opt/nmox-studio/bin/nmox \"$DEB_STAGE/usr/bin/nmox\"\n");
    }

    // ---------------------------------------------------------------- the cask

    @Test
    @DisplayName("the cask links the bundle's declared executable as nmox, in both homes, right after the app")
    void caskLinksTheMainExecutable() throws IOException {
        Matcher m = Pattern.compile("<key>CFBundleExecutable</key>\\s*<string>([^<]+)</string>")
                .matcher(read(BUILD_DMG));
        assertThat(m.find()).as("build-dmg.sh declares the bundle executable").isTrue();
        String exe = m.group(1);
        assertThat(LAUNCHER_OPEN).as("and writes that very file").contains("/Contents/MacOS/" + exe + "\"");
        String stanza = "binary \"#{appdir}/NMOX Studio.app/Contents/MacOS/" + exe + "\", target: \"nmox\"\n";
        String app = "app \"NMOX Studio.app\"\n";
        for (Path home : List.of(CASK, WORKFLOW)) {
            String text = read(home);
            int a = text.indexOf(app);
            int b = text.indexOf(stanza);
            assertThat(b).as("%s links the main executable as nmox", home).isNotEqualTo(-1);
            assertThat(text.lastIndexOf(stanza)).as("%s does it once", home).isEqualTo(b);
            assertThat(text.substring(a + app.length(), b).strip())
                    .as("%s: brew style puts binary directly after app", home).isEmpty();
        }
    }

    // ---------------------------------------------------------------- Windows

    @Test
    @DisplayName("Windows: nmox.cmd starts the launcher the Start menu starts, without holding the console")
    void windowsShimStartsTheLauncher() throws IOException {
        String iss = read(ISS);
        Matcher icon = Pattern.compile("(?m)^Name: \"\\{group\\}\\\\NMOX Studio\"; Filename: \"\\{app\\}\\\\bin\\\\([^\"]+)\"")
                .matcher(iss);
        assertThat(icon.find()).as("the Start-menu shortcut names the launcher exe").isTrue();
        String cmd = read(NMOX_CMD);
        assertThat(cmd).as("the shim finds that launcher beside its own folder")
                .contains("set \"NMOX_EXE=%~dp0..\\bin\\" + icon.group(1) + "\"\n");
        assertThat(cmd).as("START returns at once, handing over the rewritten arguments")
                .contains("start \"\" \"%NMOX_EXE%\" %NMOX_ARGS%\n");
        assertThat(cmd).as("a folder is aimed and a file opened, by absolute path")
                .contains("for %%D in (\"%~f1\\.\") do set \"NMOX_A=%%~fD\"\nset \"NMOX_VERB=--aim\"\n")
                .contains("set \"NMOX_A=%~f1\"\nset \"NMOX_VERB=--open\"\n");
        assertThat(iss).as("nmox.cmd lands in a folder of its own, beside bin")
                .contains("Source: \"nmox.cmd\"; DestDir: \"{app}\\cli\"\n");
    }

    @Test
    @DisplayName("Windows: nmox.cmd never expands an argument twice, so % ^ & ! in a file name survive")
    void windowsShimExpandsEachArgumentOnce() throws IOException {
        String cmd = read(NMOX_CMD);
        List<String> code = cmd.lines().filter(l -> !l.startsWith("rem")).toList();
        assertThat(code).as("CALL re-expands %% and doubles ^ in its arguments: no `call set`, no `call` with"
                + " arguments at all (a bare `call :label` passes nothing through the second parse)")
                .noneMatch(l -> l.matches("(?i)\\s*call\\s+(?!:(append|goto)$).*"));
        assertThat(code.get(1)).as("the argument is read with delayed expansion OFF, so a ! stays a character")
                .isEqualTo("setlocal DisableDelayedExpansion");
        for (int i = 0; i < code.size(); i++) {
            if (code.get(i).equalsIgnoreCase("setlocal EnableDelayedExpansion")) {
                assertThat(code.get(i - 1)).as("delayed expansion is switched on only inside the routines that"
                        + " read a value back, never where %~1 is read").isIn(":append", ":goto", ":missing", ":notfolder", ":vscodeonly");
            }
        }
        assertThat(cmd).as("the value is appended with delayed expansion on and carried out of the inner"
                + " SETLOCAL by FOR /F, into a context where it is off again")
                .contains("for /f \"delims=\" %%L in (\"\"!NMOX_L!\"\") do endlocal & set \"NMOX_ARGS=%%~L\"\n");
        assertThat(cmd.split("\ncall :append\n", -1)).as("one path into NMOX_ARGS").hasSize(2);
    }

    @Test
    @DisplayName("Windows: the installer check runs nmox.cmd on the name call-set mangled, and on every refusal")
    void workflowRunsTheShim() throws IOException {
        String wf = read(WIN_CHECK);
        int step = wf.indexOf("- name: Verify nmox.cmd turns folders and files into --aim and --open\n");
        assertThat(step).as("the workflow step").isNotEqualTo(-1);
        int end = wf.indexOf("\n  # ", step);
        String body = wf.substring(step, end == -1 ? wf.length() : end);
        assertThat(body).as("a file name carrying %, ^, & and ! reaches the launcher intact")
                .contains("$odd = 'p%NMOXNOPE%q ^r &s !t.js'")
                .contains("'--open', \"$root\\proj dir\\src\\$odd\")");
        assertThat(body).as("each refusal: exit 2, its sentence, and no launcher")
                .contains("@('missing.txt', 'nmox: missing.txt: no such file or folder')")
                .contains("@('--aim src\\app.js', 'nmox: src\\app.js: not a folder')")
                .contains("if ($p.ExitCode -ne 2)")
                .contains("started the launcher it refused");
        assertThat(body).as("a missing name no longer passes through").doesNotContain("'missing.txt', '-J-Xmx1g'");
    }

    @Test
    @DisplayName("Windows: nmox.cmd knows the same value-taking options as the Unix launchers")
    void windowsShimSharesTheOptionList() throws IOException {
        Matcher unix = Pattern.compile("(?m)^\\s*(--userdir\\|[^)]+)\\)$").matcher(read(LINUX_NMOX));
        assertThat(unix.find()).as("the Unix launcher lists its value-taking options").isTrue();
        Matcher win = Pattern.compile("(?m)^for %%V in \\(([^)]+)\\) do").matcher(read(NMOX_CMD));
        assertThat(win.find()).as("nmox.cmd lists its value-taking options").isTrue();
        assertThat(List.of(win.group(1).split(" "))).as("an option value is never taken for a path, on any OS")
                .containsExactly(unix.group(1).split("\\|"));
    }

    @Test
    @DisplayName("Windows: the PATH task is offered in every installer language and undone on uninstall")
    void windowsPathTask() throws IOException {
        String iss = read(ISS);
        assertThat(iss).contains("Name: \"addtopath\"; Description: \"{cm:AddToPath}\"\n");
        assertThat(section(iss, "Tasks").lines().filter(l -> l.startsWith("Name: \"addtopath\""))
                .toList()).as("ticked by default, as VS Code's installer ticks it (the folder task,"
                        + " OpenFolderFromOsGateTest's, is the one left unticked)")
                .singleElement().asString().doesNotContain("unchecked");
        String messages = section(iss, "CustomMessages");
        assertThat(messages).as("the fallback every language without its own line gets").contains("\nAddToPath=");
        Matcher lang = Pattern.compile("(?m)^Name: \"([a-z]{2})\"; MessagesFile:").matcher(iss);
        int langs = 0;
        while (lang.find()) {
            langs++;
            if (!"en".equals(lang.group(1))) {
                assertThat(messages).as("the wizard speaks %s, so the PATH task must too", lang.group(1))
                        .contains("\n" + lang.group(1) + ".AddToPath=");
            }
        }
        assertThat(langs).as("the installer's languages").isGreaterThanOrEqualTo(8);
        assertThat(iss).as("Cyrillic, Hebrew and Arabic need a compiler that reads UTF-8 without a BOM")
                .contains("#if Ver < EncodeVer(6,3,0)\n");
        String registry = section(iss, "Registry");
        for (String root : List.of("Root: HKCU; Subkey: \"Environment\"",
                "Root: HKLM; Subkey: \"SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment\"")) {
            assertThat(registry).as("the per-user and all-users installs each get their own Path").contains(root);
        }
        assertThat(registry.split("Tasks: addtopath", -1)).as("both entries ride the task").hasSize(3);
        assertThat(registry.lines().filter(l -> !l.startsWith(";")))
                .as("{olddata};{app}\\cli wrote \";<folder>\" when there was no Path yet")
                .noneMatch(l -> l.contains("{olddata}"));
        assertThat(registry).as("both append, through the one function that adds a separator only between"
                + " two entries").contains("ValueData: \"{code:PathWithCli|user}\"")
                .contains("ValueData: \"{code:PathWithCli|system}\"");
        assertThat(registry).contains("Check: NeedsUserPathEntry").contains("Check: NeedsSystemPathEntry");
        assertThat(iss).as("Explorer is told, so a new terminal sees the change").contains("ChangesEnvironment=yes\n");
        String code = section(iss, "Code");
        assertThat(code).as("an empty Path gains the folder alone; one ending in ; gains no second separator")
                .contains("function AppendPathEntry(Paths, Dir: String): String;\nbegin\n  if Paths = '' then\n"
                        + "    Result := Dir\n  else if Paths[Length(Paths)] = ';' then\n    Result := Paths + Dir\n"
                        + "  else\n    Result := Paths + ';' + Dir;\nend;\n")
                .contains("Result := AppendPathEntry(Paths, CliDir());");
        String remove = code.substring(code.indexOf("procedure RemovePathEntry("),
                code.indexOf("procedure CurUninstallStepChanged("));
        assertThat(remove).as("only the exact entry goes: an empty entry is the user's and is kept")
                .contains("if (Part <> '') and\n")
                .doesNotContain("else if Part <> '' then");
        assertThat(remove).as("kept entries are joined by position, so an empty one keeps its separators")
                .contains("if not First then\n        Kept := Kept + ';';");
        String wf = read(WIN_CHECK);
        int step = wf.indexOf("- name: Verify the nmox command on PATH\n");
        String body = wf.substring(step, wf.indexOf("\n      - name:", step + 1));
        assertThat(body).as("the installer check reads the Path raw, installs onto no Path and onto one with"
                + " an empty entry, and uninstalls back to exactly what it found")
                .contains("'DoNotExpandEnvironmentNames'")
                .contains("with no user Path the installer wrote")
                .contains("the uninstaller left a user Path")
                .contains("$mine = 'C:\\keep one;;%USERPROFILE%\\keep two'")
                .contains("the uninstaller left [$now], not the [$mine] it found");
        assertThat(code).as("the uninstaller takes the folder off the Path it went on")
                .contains("procedure CurUninstallStepChanged(")
                .contains("RemovePathEntry(HKEY_LOCAL_MACHINE, SystemEnvKey, CliDir())")
                .contains("RemovePathEntry(HKEY_CURRENT_USER, UserEnvKey, CliDir())");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * What a developer types: the folder they stand in, a file in it, an
     * option whose VALUE is an existing folder (must stay a value, not become
     * --aim), and a JVM flag. A name that is not there is refused before the
     * IDE starts (see {@link #refusals()}), so it has no place in a launch.
     */
    private static final List<String> TYPED = List.of(".", "src/app.js", "--cachedir", "src", "-J-Xmx1g");

    private Path project() throws IOException {
        Path project = Files.createDirectories(tmp.resolve("my project/src"));
        Files.writeString(project.resolve("app.js"), "x");
        return project.getParent();
    }

    private Process start(Path cwd, String command, List<String> args) throws IOException {
        List<String> argv = new ArrayList<>();
        argv.add(command);
        argv.addAll(args);
        return new ProcessBuilder(argv).directory(cwd.toFile()).redirectErrorStream(true)
                .redirectOutput(tmp.resolve("launcher.out").toFile()).start();
    }

    /** The record of an IDE started with {@link #TYPED} from {@code project}, argv beginning at {@code from}. */
    private static void assertTranslated(List<String> rec, int from, Path project) throws IOException {
        Path real = project.toRealPath();
        assertThat(Path.of(rec.get(0)).toRealPath()).as("the IDE runs in the caller's folder").isEqualTo(real);
        List<String> argv = rec.subList(from, rec.size());
        assertThat(argv).as("the argv the platform launcher received").hasSize(7);
        assertThat(argv.get(0)).as("a folder is aimed").isEqualTo("--aim");
        assertThat(Path.of(argv.get(1))).as("by its absolute path").isAbsolute();
        assertThat(Path.of(argv.get(1)).toRealPath()).as("'.' is the caller's folder").isEqualTo(real);
        assertThat(argv.get(2)).as("a file is opened").isEqualTo("--open");
        assertThat(Path.of(argv.get(3))).as("by its absolute path").isAbsolute();
        assertThat(Path.of(argv.get(3)).toRealPath()).isEqualTo(real.resolve("src/app.js"));
        assertThat(argv.subList(4, 7)).as("options and an option's value pass through as typed")
                .containsExactly("--cachedir", "src", "-J-Xmx1g");
    }

    /**
     * The argument-rewriting loop, from the {@code goto_line} helper it
     * calls through the main loop's {@code done} and the dangling-option
     * refusal after it (the first {@code fi} past the loop), whitespace
     * aside.
     */
    private static List<String> rule(String script) {
        int from = script.indexOf("goto_line() {\n");
        assertThat(from).as("the launcher rewrites its arguments").isNotEqualTo(-1);
        List<String> lines = new ArrayList<>();
        boolean inLoop = false;
        boolean looped = false;
        for (String line : script.substring(from).split("\n")) {
            lines.add(line.strip());
            // the MAIN loop's done: goto_line has a for ... done of its own,
            // and stopping there compared nothing past it (3.1.0 - a mutant
            // in the option case lived through this test until it did)
            inLoop |= line.strip().startsWith("while ");
            looped |= inLoop && line.strip().equals("done");
            if (looped && line.strip().equals("fi")) {
                return lines;
            }
        }
        throw new AssertionError("the argument loop is never closed");
    }

    /** An .iss section's body, from its header to the next header. */
    private static String section(String iss, String name) {
        int at = iss.indexOf("\n[" + name + "]\n");
        assertThat(at).as("the .iss has a [%s] section", name).isNotEqualTo(-1);
        int next = iss.indexOf("\n[", at + 2);
        return next == -1 ? iss.substring(at) : iss.substring(at, next + 1);
    }

    private record Bundle(Path launcher, Path record, Path release) {
    }

    /** A stand-in "NMOX Studio.app": the real launcher, a java that runs, a platform launcher that records. */
    private Bundle bundle() throws IOException {
        Path contents = tmp.resolve("Apps/NMOX Studio.app/Contents");
        Path macos = Files.createDirectories(contents.resolve("MacOS"));
        Path res = contents.resolve("Resources/nmoxstudio");
        Path launcher = macos.resolve("nmox-studio");
        Files.writeString(launcher, launcher());
        executable(launcher);
        Path java = Files.createDirectories(res.resolve("jre/bin")).resolve("java");
        Files.writeString(java, "#!/bin/sh\nexit 0\n");
        executable(java);
        Path record = tmp.resolve("record.txt");
        Path release = tmp.resolve("release");
        Path ide = Files.createDirectories(res.resolve("bin")).resolve("nmoxstudio");
        writeIde(ide, record, release, 7);
        return new Bundle(launcher, record, release);
    }

    /**
     * The platform launcher's stand-in: waits (bounded) until released, then
     * records its physical working directory and its arguments one per line.
     * Deliberately NOT executable — the launcher must hand it to /bin/sh.
     */
    private static void writeIde(Path at, Path record, Path release, int exit) throws IOException {
        Files.writeString(at, "#!/bin/sh\n"
                + "i=0\n"
                + "while [ ! -f '" + release + "' ] && [ $i -lt 300 ]; do sleep 0.1; i=$((i+1)); done\n"
                + "out='" + record + "'\n"
                + "pwd -P > \"$out.tmp\"\n"
                + "for a in \"$@\"; do printf '%s\\n' \"$a\" >> \"$out.tmp\"; done\n"
                + "mv \"$out.tmp\" \"$out\"\n"
                + "exit " + exit + "\n");
    }

    /**
     * A platform launcher that records, as its FIRST act, that it was ever
     * started - no waiting, no release file. A refusal test that finds no
     * tripwire after the launcher returned (and a grace period for a
     * detached start) has proved nothing started.
     */
    private static void writeTripwireIde(Path at, Path started) throws IOException {
        Files.writeString(at, "#!/bin/sh\n: > '" + started + "'\nexit 0\n");
    }

    private void assertRefused(Process p, String said, Path started) throws Exception {
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).as("the refusal returns").isTrue();
        String out = slurp(tmp.resolve("launcher.out"));
        assertThat(p.exitValue()).as("a refusal exits 2 (AimOption's code); the launcher said: %s", out).isEqualTo(2);
        assertThat(out).as("the refusal names what was not there").contains(said);
        Thread.sleep(1500); // a detached start, had there been one, would have tripped by now
        assertThat(started).as("nothing was started for an argv the launcher refused").doesNotExist();
    }

    private static void executable(Path p) throws IOException {
        Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private static List<String> awaitRecord(Path record) throws Exception {
        for (int i = 0; i < 200 && !Files.exists(record); i++) {
            Thread.sleep(100);
        }
        assertThat(record).as("the IDE stand-in ran").exists();
        return Files.readAllLines(record);
    }

    private static String slurp(Path p) throws IOException {
        return Files.exists(p) ? Files.readString(p) : "";
    }
}
