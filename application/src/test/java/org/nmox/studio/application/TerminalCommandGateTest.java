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
 * <absolute>}, an option and its value pass through, a missing name stays
 * as typed so the IDE's refusal can name it.
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
                .contains("--aim \"%%NMOX_DIR%%\"").contains("--open \"%~f1\"");
        assertThat(iss).as("nmox.cmd lands in a folder of its own, beside bin")
                .contains("Source: \"nmox.cmd\"; DestDir: \"{app}\\cli\"\n");
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
        assertThat(section(iss, "Tasks")).as("ticked by default, as VS Code's installer ticks it")
                .doesNotContain("unchecked");
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
        assertThat(registry.split("\\{olddata\\};\\{app\\}\\\\cli", -1)).as("both append, never replace").hasSize(3);
        assertThat(registry).contains("Check: NeedsUserPathEntry").contains("Check: NeedsSystemPathEntry");
        assertThat(iss).as("Explorer is told, so a new terminal sees the change").contains("ChangesEnvironment=yes\n");
        String code = section(iss, "Code");
        assertThat(code).as("the uninstaller takes the folder off the Path it went on")
                .contains("procedure CurUninstallStepChanged(")
                .contains("RemovePathEntry(HKEY_LOCAL_MACHINE, SystemEnvKey, CliDir())")
                .contains("RemovePathEntry(HKEY_CURRENT_USER, UserEnvKey, CliDir())");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * What a developer types: the folder they stand in, a file in it, an
     * option whose VALUE is an existing folder (must stay a value, not become
     * --aim), a name that is not there, and a JVM flag.
     */
    private static final List<String> TYPED = List.of(".", "src/app.js", "--cachedir", "src", "missing.txt", "-J-Xmx1g");

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
        assertThat(argv).as("the argv the platform launcher received").hasSize(8);
        assertThat(argv.get(0)).as("a folder is aimed").isEqualTo("--aim");
        assertThat(Path.of(argv.get(1))).as("by its absolute path").isAbsolute();
        assertThat(Path.of(argv.get(1)).toRealPath()).as("'.' is the caller's folder").isEqualTo(real);
        assertThat(argv.get(2)).as("a file is opened").isEqualTo("--open");
        assertThat(Path.of(argv.get(3))).as("by its absolute path").isAbsolute();
        assertThat(Path.of(argv.get(3)).toRealPath()).isEqualTo(real.resolve("src/app.js"));
        assertThat(argv.subList(4, 8)).as("options, an option's value and a missing name pass through as typed")
                .containsExactly("--cachedir", "src", "missing.txt", "-J-Xmx1g");
    }

    /** The argument-rewriting loop, from {@code n=$#} to its {@code done}, whitespace aside. */
    private static List<String> rule(String script) {
        int from = script.indexOf("n=$#\n");
        assertThat(from).as("the launcher rewrites its arguments").isNotEqualTo(-1);
        List<String> lines = new ArrayList<>();
        for (String line : script.substring(from).split("\n")) {
            lines.add(line.strip());
            if (line.strip().equals("done")) {
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
