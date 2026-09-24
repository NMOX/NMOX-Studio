package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.tools.vscode.VsCodeTasks.Host;
import org.nmox.studio.tools.vscode.VsCodeTasks.Launch;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;
import org.nmox.studio.tools.vscode.VsCodeTasks.Reason;
import org.nmox.studio.tools.vscode.VsCodeTasks.Refused;
import org.nmox.studio.tools.vscode.VsCodeTasks.Resolved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Which shell a {@code "type": "shell"} task runs in (v3.1.0, the hostile
 * review's findings 6 and 7). VS Code runs the line in the user's shell —
 * {@code $SHELL} on macOS and Linux, a login shell on macOS, PowerShell on
 * Windows — and {@code options.shell} replaces that shell with exactly the
 * arguments it names. Running the line in a different shell is running a
 * different command ({@code source .venv/bin/activate && pytest} fails
 * under dash), so the choice is pinned here, every case through a
 * {@link Host} seam so the test plays any OS on any OS.
 */
class VsCodeTaskShellTest {

    @TempDir
    Path project;

    private static final String LINE_TASK = """
            {"tasks": [{"label": "t", "type": "shell", "command": "source .venv/bin/activate && pytest",
              "args": ["-k", "two words"]%s}]}""";

    private static final String LINE = "source .venv/bin/activate && pytest -k 'two words'";

    /** A host with {@code env}, where exactly {@code executables} exist and {@code path} maps bare names. */
    private static Host host(Os os, Map<String, String> env, List<String> executables, Map<String, String> path) {
        // compared through File on both sides, so a POSIX fixture path reads
        // the same on the Windows lane (where File turns / into \\)
        return new Host(os, env::get,
                f -> executables.stream().anyMatch(e -> new File(e).getPath().equals(f.getPath())), path::get);
    }

    private Resolved resolve(String options, Host host) {
        String json = LINE_TASK.formatted(options.isEmpty() ? "" : ", \"options\": " + options);
        List<VsCodeTasks.TaskDef> tasks = VsCodeTasks.parse(json, host.os());
        assertThat(tasks).hasSize(1);
        return VsCodeTasks.resolve(tasks.get(0), project.toFile(), host);
    }

    private List<String> argv(String options, Host host) {
        Resolved r = resolve(options, host);
        assertThat(r).isInstanceOf(Launch.class);
        return ((Launch) r).argv();
    }

    /** What {@code -EncodedCommand} carries: the line as UTF-16LE, base64. */
    static String encoded(String line) {
        return Base64.getEncoder().encodeToString(line.getBytes(StandardCharsets.UTF_16LE));
    }

    private static String decoded(String b64) {
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_16LE);
    }

    @Test
    @DisplayName("POSIX: the user's $SHELL runs the line with -c, as VS Code does — not /bin/sh")
    void posixUsesTheUsersShell() {
        Host linux = host(Os.LINUX, Map.of("SHELL", "/usr/bin/zsh"), List.of("/usr/bin/zsh"), Map.of());
        assertThat(argv("", linux)).containsExactly("/usr/bin/zsh", "-c", LINE);

        Host bash = host(Os.LINUX, Map.of("SHELL", "/bin/bash"), List.of("/bin/bash"), Map.of());
        assertThat(argv("", bash)).as("Linux profiles carry no -l").containsExactly("/bin/bash", "-c", LINE);
    }

    @Test
    @DisplayName("macOS: a zsh, bash or fish starts as a login shell (-l), as VS Code's default profiles do; others do not")
    void macStartsALoginShell() {
        for (String shell : List.of("/bin/zsh", "/bin/bash", "/opt/homebrew/bin/fish")) {
            Host mac = host(Os.MAC, Map.of("SHELL", shell), List.of(shell), Map.of());
            assertThat(argv("", mac)).as(shell).containsExactly(shell, "-l", "-c", LINE);
        }
        Host nu = host(Os.MAC, Map.of("SHELL", "/opt/homebrew/bin/nu"), List.of("/opt/homebrew/bin/nu"), Map.of());
        assertThat(argv("", nu)).containsExactly("/opt/homebrew/bin/nu", "-c", LINE);
    }

    @Test
    @DisplayName("$SHELL is used only when it names an absolute, executable, real shell; otherwise /bin/sh")
    void shellFallsBackToBinSh() {
        assertThat(argv("", host(Os.LINUX, Map.of(), List.of(), Map.of())))
                .as("unset").containsExactly("/bin/sh", "-c", LINE);
        assertThat(argv("", host(Os.LINUX, Map.of("SHELL", "zsh"), List.of("zsh"), Map.of())))
                .as("not absolute").containsExactly("/bin/sh", "-c", LINE);
        assertThat(argv("", host(Os.LINUX, Map.of("SHELL", "/usr/bin/zsh"), List.of(), Map.of())))
                .as("absolute but not an executable file").containsExactly("/bin/sh", "-c", LINE);
        assertThat(argv("", host(Os.LINUX, Map.of("SHELL", "/bin/false"), List.of("/bin/false"), Map.of())))
                .as("the refusing placeholder").containsExactly("/bin/sh", "-c", LINE);
        assertThat(argv("", host(Os.LINUX, Map.of("SHELL", "/usr/sbin/nologin"),
                List.of("/usr/sbin/nologin"), Map.of())))
                .as("nologin").containsExactly("/bin/sh", "-c", LINE);
    }

    @Test
    @DisplayName("options.shell.executable: that shell with exactly its args — VS Code adds no flag once the shell is named")
    void namedShellIsHonouredWithItsArgs() {
        Host linux = host(Os.LINUX, Map.of("SHELL", "/usr/bin/zsh"), List.of("/usr/bin/zsh", "/bin/bash"),
                Map.of("bash", "/usr/local/bin/bash"));
        assertThat(argv("{\"shell\": {\"executable\": \"/bin/bash\", \"args\": [\"-O\", \"extglob\", \"-c\"]}}", linux))
                .containsExactly("/bin/bash", "-O", "extglob", "-c", LINE);
        assertThat(argv("{\"shell\": {\"executable\": \"bash\", \"args\": [\"-c\"]}}", linux))
                .as("a bare name is found on the search path")
                .containsExactly("/usr/local/bin/bash", "-c", LINE);
        assertThat(argv("{\"shell\": {\"executable\": \"/bin/bash\"}}", linux))
                .as("no args named, none added — VS Code runs exactly this")
                .containsExactly("/bin/bash", LINE);
        Host mac = host(Os.MAC, Map.of("SHELL", "/bin/zsh"), List.of("/bin/zsh"), Map.of());
        assertThat(argv("{\"shell\": {\"executable\": \"/bin/zsh\", \"args\": [\"-c\"]}}", mac))
                .as("a named shell never gets the default profile's -l")
                .containsExactly("/bin/zsh", "-c", LINE);
    }

    @Test
    @DisplayName("options.shell.args alone: the default shell with those args, -c appended when missing")
    void argsAloneKeepTheDefaultShell() {
        Host linux = host(Os.LINUX, Map.of("SHELL", "/bin/bash"), List.of("/bin/bash"), Map.of());
        assertThat(argv("{\"shell\": {\"args\": [\"-e\"]}}", linux)).containsExactly("/bin/bash", "-e", "-c", LINE);
        assertThat(argv("{\"shell\": {\"args\": [\"-e\", \"-c\"]}}", linux)).containsExactly("/bin/bash", "-e", "-c", LINE);
        Host mac = host(Os.MAC, Map.of("SHELL", "/bin/zsh"), List.of("/bin/zsh"), Map.of());
        assertThat(argv("{\"shell\": {\"args\": [\"-f\"]}}", mac))
                .as("the args replace the profile's -l, as VS Code's do").containsExactly("/bin/zsh", "-f", "-c", LINE);
    }

    @Test
    @DisplayName("a named shell that is nowhere is refused by name; nothing runs")
    void missingShellIsRefused() {
        Host linux = host(Os.LINUX, Map.of(), List.of(), Map.of());
        Resolved absolute = resolve("{\"shell\": {\"executable\": \"/no/such/sh\", \"args\": [\"-c\"]}}", linux);
        assertThat(absolute).isEqualTo(new Refused(Reason.SHELL_MISSING, "/no/such/sh"));
        Resolved bare = resolve("{\"shell\": {\"executable\": \"xonsh\", \"args\": [\"-c\"]}}", linux);
        assertThat(bare).isEqualTo(new Refused(Reason.SHELL_MISSING, "xonsh"));
    }

    @Test
    @DisplayName("variables in options.shell are substituted, and one only VS Code can fill refuses the task")
    void shellOptionsTakeVariables() {
        Host linux = host(Os.LINUX, Map.of("MYSH", "/opt/sh"), List.of("/opt/sh"), Map.of());
        assertThat(argv("{\"shell\": {\"executable\": \"${env:MYSH}\", \"args\": [\"-c\"]}}", linux))
                .containsExactly("/opt/sh", "-c", LINE);
        assertThat(resolve("{\"shell\": {\"executable\": \"/opt/sh\", \"args\": [\"${input:flag}\"]}}", linux))
                .isEqualTo(new Refused(Reason.VARIABLE, "${input:flag}"));
    }

    @Test
    @DisplayName("Windows: PowerShell (pwsh first) with the exact line as -EncodedCommand; Windows PowerShell as the fallback")
    void windowsDefaultsToPowerShell() {
        Host withPwsh = host(Os.WINDOWS, Map.of(), List.of(), Map.of("pwsh", "C:\\PS\\7\\pwsh.exe"));
        List<String> argv = argv("", withPwsh);
        assertThat(argv).hasSize(3).startsWith("C:\\PS\\7\\pwsh.exe", "-EncodedCommand");
        assertThat(decoded(argv.get(2))).as("PowerShell quoting: strong is single quotes")
                .isEqualTo(LINE);

        String builtIn = new File("C:\\Windows", VsCodeTasks.WINDOWS_POWERSHELL).getPath();
        Host noPwsh = host(Os.WINDOWS, Map.of("SystemRoot", "C:\\Windows"), List.of(builtIn), Map.of());
        assertThat(argv("", noPwsh).subList(0, 2)).containsExactly(builtIn, "-EncodedCommand");

        Host bare = host(Os.WINDOWS, Map.of(), List.of(), Map.of());
        assertThat(argv("", bare).get(0)).as("found by the spawn, or reported by it").isEqualTo("powershell.exe");
    }

    @Test
    @DisplayName("Windows: a named PowerShell keeps its args up to -Command; cmd.exe needs /c; any other shell is refused by name")
    void windowsNamedShells() {
        Host win = host(Os.WINDOWS, Map.of(), List.of("C:\\bin\\cmd.exe", "C:\\bin\\pwsh.exe", "C:\\git\\bash.exe"),
                Map.of());
        List<String> ps = argv("{\"shell\": {\"executable\": \"C:\\\\bin\\\\pwsh.exe\","
                + " \"args\": [\"-NoProfile\", \"-Command\"]}}", win);
        assertThat(ps.subList(0, 3)).containsExactly("C:\\bin\\pwsh.exe", "-NoProfile", "-EncodedCommand");
        assertThat(decoded(ps.get(3))).isEqualTo(LINE);

        assertThat(argv("{\"shell\": {\"executable\": \"C:\\\\bin\\\\cmd.exe\", \"args\": [\"/d\", \"/c\"]}}", win))
                .containsExactly("C:\\bin\\cmd.exe", "/d", "/s", "/c",
                        "\"source .venv/bin/activate && pytest -k \"two words\"\"");

        assertThat(resolve("{\"shell\": {\"executable\": \"C:\\\\bin\\\\cmd.exe\"}}", win))
                .as("cmd.exe with no /c would open an interactive shell that never ends")
                .isEqualTo(new Refused(Reason.SHELL_UNSUPPORTED, "C:\\bin\\cmd.exe"));
        assertThat(resolve("{\"shell\": {\"executable\": \"C:\\\\git\\\\bash.exe\", \"args\": [\"-c\"]}}", win))
                .as("no faithful way to hand bash.exe a line through Java's Windows quoting")
                .isEqualTo(new Refused(Reason.SHELL_UNSUPPORTED, "C:\\git\\bash.exe"));
        assertThat(resolve("{\"shell\": {\"executable\": \"C:\\\\bin\\\\pwsh.exe\", \"args\": [\"-File\"]}}", win))
                .isEqualTo(new Refused(Reason.SHELL_UNSUPPORTED, "C:\\bin\\pwsh.exe"));
    }

    /**
     * The standard Windows command-line split (the MSVC runtime since 2008,
     * CommandLineToArgvW): 2n backslashes then a quote are n backslashes and
     * a delimiter, 2n+1 are n and a literal quote, backslashes elsewhere are
     * literal, and {@code ""} inside quotes is a literal quote.
     */
    static List<String> windowsSplit(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        boolean any = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\') {
                int n = 0;
                while (i < line.length() && line.charAt(i) == '\\') {
                    n++;
                    i++;
                }
                if (i < line.length() && line.charAt(i) == '"') {
                    cur.append("\\".repeat(n / 2));
                    if (n % 2 == 1) {
                        cur.append('"');
                        i++;
                    }
                } else {
                    cur.append("\\".repeat(n));
                }
                any = true;
                continue;
            }
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i += 2;
                    continue;
                }
                inQuotes = !inQuotes;
                any = true;
                i++;
                continue;
            }
            if (!inQuotes && (c == ' ' || c == '\t')) {
                if (any) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    any = false;
                }
                i++;
                continue;
            }
            cur.append(c);
            any = true;
            i++;
        }
        if (any) {
            out.add(cur.toString());
        }
        return out;
    }

    @Test
    @DisplayName("cmd.exe: an argument ending in a backslash reaches the program intact — the review's finding 7")
    void cmdQuotingSurvivesTrailingBackslashes() {
        assertThat(VsCodeTasks.cmdQuote("out\\", null)).isEqualTo("\"out\\\\\"");
        for (String arg : List.of("out\\", "C:\\My Project\\", "a\\\"b", "x\"y", "two words", "a\\b c", "end\\\\")) {
            String line = "prog " + VsCodeTasks.cmdQuote(arg, null) + " next";
            assertThat(windowsSplit(line)).as(arg + " in " + line).containsExactly("prog", arg, "next");
        }
        assertThat(windowsSplit("prog \"out\\\" next")).as("the old quoting: the backslash ate the quote")
                .containsExactly("prog", "out\" next");
    }

    @Test
    @DisplayName("cmd.exe: a line ending in a backslash gets a space before cmd's closing quote")
    void cmdLineNeverEndsInBackslashQuote() {
        Host win = host(Os.WINDOWS, Map.of(), List.of("C:\\bin\\cmd.exe"), Map.of());
        List<VsCodeTasks.TaskDef> tasks = VsCodeTasks.parse("""
                {"tasks": [{"label": "d", "type": "shell", "command": "dir C:\\\\",
                  "options": {"shell": {"executable": "C:\\\\bin\\\\cmd.exe", "args": ["/d", "/c"]}}}]}""",
                Os.WINDOWS);
        Launch launch = (Launch) VsCodeTasks.resolve(tasks.get(0), project.toFile(), win);
        assertThat(launch.argv().get(launch.argv().size() - 1)).isEqualTo("\"dir C:\\ \"");
    }

    @Test
    @DisplayName("PowerShell quoting: every single-quote delimiter doubled, weak escapes with the backtick, a backslash needs nothing")
    void powerShellQuoting() {
        assertThat(VsCodeTasks.psQuote("out\\", null)).isEqualTo("out\\");
        assertThat(VsCodeTasks.psQuote("it's", null)).isEqualTo("'it''s'");
        assertThat(VsCodeTasks.psQuote("it\u2019s", null)).isEqualTo("'it\u2019\u2019s'");
        assertThat(VsCodeTasks.psQuote("a,b", null)).as("a comma builds an array").isEqualTo("'a,b'");
        assertThat(VsCodeTasks.psQuote("@all", null)).as("an at sign splats").isEqualTo("'@all'");
        assertThat(VsCodeTasks.psQuote("$x \"y\"", "weak")).isEqualTo("\"$x `\"y`\"\"");
        assertThat(VsCodeTasks.psQuote("a b", "escape")).isEqualTo("a` b");
        assertThat(VsCodeTasks.psQuote("safe", "strong")).isEqualTo("'safe'");
    }

    /** Runs {@code argv} and returns its standard output, or fails after ten seconds. */
    private static String run(List<String> argv) throws Exception {
        Process p = new ProcessBuilder(argv).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        assertThat(p.waitFor(10, TimeUnit.SECONDS)).isTrue();
        return new String(out, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("live: the encoded PowerShell line prints every argument exactly (when pwsh is installed)")
    void powerShellRoundTripsLive() throws Exception {
        String pwsh = Host.of(Os.current(), System::getenv).onPath().apply("pwsh");
        assumeTrue(pwsh != null, "pwsh is not installed here");
        // ASCII only: a Windows console may re-encode a typographic quote on
        // the way out; the doubling of those is pinned in powerShellQuoting
        List<String> args = List.of("it's", "a b", "out\\", "x\"y", "$HOME", "a,b", "@all", "(p)");
        StringBuilder json = new StringBuilder("{\"tasks\": [{\"label\": \"w\", \"type\": \"shell\","
                + " \"command\": \"Write-Output\", \"args\": [");
        for (int i = 0; i < args.size(); i++) {
            json.append(i == 0 ? "" : ", ").append(org.json.JSONObject.quote(args.get(i)));
        }
        json.append("], \"options\": {\"shell\": {\"executable\": ").append(org.json.JSONObject.quote(pwsh))
                .append(", \"args\": [\"-NoProfile\", \"-Command\"]}}}]}");
        Host win = new Host(Os.WINDOWS, name -> null, f -> f.isFile() && f.canExecute(), name -> null);
        Launch launch = (Launch) VsCodeTasks.resolve(VsCodeTasks.parse(json.toString(), Os.WINDOWS).get(0),
                project.toFile(), win);
        assertThat(run(launch.argv())).isEqualTo(String.join("\n", args) + "\n");
    }

    @Test
    @DisplayName("live: the user's shell prints every argument exactly (POSIX)")
    void posixShellRoundTripsLive() throws Exception {
        assumeTrue(Os.current() != Os.WINDOWS, "a POSIX shell");
        String shell = new File("/bin/zsh").canExecute() ? "/bin/zsh" : "/bin/bash";
        assumeTrue(new File(shell).canExecute(), "no zsh or bash");
        List<String> args = List.of("it's", "a b", "out\\", "x\"y", "$HOME", "*", "a;b");
        StringBuilder json = new StringBuilder("{\"tasks\": [{\"label\": \"p\", \"type\": \"shell\","
                + " \"command\": \"printf '%s\\\\n'\", \"args\": [");
        for (int i = 0; i < args.size(); i++) {
            json.append(i == 0 ? "" : ", ").append(org.json.JSONObject.quote(args.get(i)));
        }
        json.append("]}]}");
        UnaryOperator<String> env = name -> "SHELL".equals(name) ? shell : null;
        Host linux = new Host(Os.LINUX, env, f -> f.isFile() && f.canExecute(), name -> null);
        Launch launch = (Launch) VsCodeTasks.resolve(VsCodeTasks.parse(json.toString(), Os.LINUX).get(0),
                project.toFile(), linux);
        assertThat(launch.argv().get(0)).isEqualTo(shell);
        assertThat(run(launch.argv())).isEqualTo(String.join("\n", args) + "\n");
    }
}
