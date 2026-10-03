package org.nmox.studio.tools.vscode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.Host;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.Launch;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;
import org.nmox.studio.tools.vscode.VsCodeTasks.Reason;
import org.nmox.studio.tools.vscode.VsCodeTasks.Refused;
import org.nmox.studio.tools.vscode.VsCodeTasks.Resolved;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.TasksFile;
import org.nmox.studio.tools.vscode.VsCodeTasks.Vars;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a hostile review of the task resolver found (3.6.0), each held by
 * the case that showed it: a file name pasted unquoted into a shell line,
 * the user's environment printed in the launch line, an environment entry
 * no process can take, a symlinked project, a batch file's second parse,
 * and secrets in a record's own {@code toString}.
 */
class VsCodeTaskReviewTest {

    @TempDir
    Path project;

    /** A POSIX machine whose shell is /bin/sh and whose environment holds a token. */
    private static final Host LINUX = new Host(Os.LINUX,
            name -> switch (name) {
                case "SHELL" -> "/bin/sh";
                case "TOKEN" -> "ghp_SECRET_TOKEN";
                default -> null;
            },
            f -> f.getPath().equals("/bin/sh"), name -> null);

    private static TaskDef task(String json) {
        return VsCodeTasks.parse("{\"tasks\":[" + json + "]}", Os.LINUX).get(0);
    }

    private Vars editing(Path file) {
        return new Vars(new EditorContext(file, 1, 1, null), "/home/dev", Map.of(), Map.of());
    }

    /* ---------------------------------------- a value in the command line */

    private static void assumePosix() {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "the fixture is a POSIX shell");
    }

    /** Runs {@code argv} in {@code dir} and answers with what it printed. */
    private static String run(List<String> argv, Path dir) throws Exception {
        Process p = new ProcessBuilder(argv).directory(dir.toFile()).redirectErrorStream(true).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        return out;
    }

    @Test
    @DisplayName("the real spawn: ${file} in a shell task's COMMAND line reaches the program as its exact name — a space, $(…) and a quote included — and nothing in the name runs")
    void fileNamesAreQuotedInTheCommandLine() throws Exception {
        assumePosix();
        Path downloads = Files.createTempDirectory("downloads");
        Path cwd = Files.createTempDirectory("nmox-cwd");
        TaskDef show = task("{\"label\":\"show\",\"type\":\"shell\",\"command\":\"printf '[%s]' ${file}\"}");
        for (String name : new String[] {"x$(touch PWNED).js", "my notes.js", "it's `touch PWNED2`; touch PWNED3.js"}) {
            Path file = downloads.resolve(name);
            Launch launch = (Launch) VsCodeTasks.resolve(show, project.toFile(), LINUX, editing(file));
            String exact = file.toAbsolutePath().normalize().toString();
            assertThat(run(launch.argv(), cwd)).as(name + ": one argument, the exact name").isEqualTo("[" + exact + "]");
            assertThat(launch.shown()).as("the reader is shown the line that runs, quoting and all").isNull();
        }
        try (var listed = Files.list(cwd)) {
            assertThat(listed.toList()).as("no command in a file name ran").isEmpty();
        }

        // a name that is one plain word stays as written: an ordinary line reads as it did
        Path plain = project.resolve("src").resolve("app.js");
        Launch ok = (Launch) VsCodeTasks.resolve(show, project.toFile(), LINUX, editing(plain));
        assertThat(ok.argv()).containsExactly("/bin/sh", "-c", "printf '[%s]' " + plain.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("an input's answer and the selection are quoted the same way; the file's own text is the file's")
    void answersAndSelectionInTheCommandLine() throws Exception {
        assumePosix();
        Map<String, InputDef> inputs = Map.of("who", new InputDef("who", "promptString", "Who", null, false, List.of(), null));
        TaskDef greet = task("{\"label\":\"g\",\"type\":\"shell\",\"command\":\"echo ${input:who} && echo after\"}");
        Vars evil = new Vars(EditorContext.NONE, "/h", inputs, Map.of("who", "a; touch PWNED"));
        Launch launch = (Launch) VsCodeTasks.resolve(greet, project.toFile(), LINUX, evil);
        assertThat(launch.argv().get(2)).as("the && is the FILE's, and stays the shell's")
                .isEqualTo("echo 'a; touch PWNED' && echo after");
        Path cwd = Files.createTempDirectory("nmox-cwd");
        assertThat(run(launch.argv(), cwd)).isEqualTo("a; touch PWNED\nafter\n");
        try (var listed = Files.list(cwd)) {
            assertThat(listed.toList()).isEmpty();
        }

        TaskDef sel = task("{\"label\":\"s\",\"type\":\"shell\",\"command\":\"grep ${selectedText} .\"}");
        Vars selected = new Vars(new EditorContext(project.resolve("a.js"), 1, 1, "two words"), "/h", Map.of(), Map.of());
        Launch grep = (Launch) VsCodeTasks.resolve(sel, project.toFile(), LINUX, selected);
        assertThat(grep.argv().get(2)).isEqualTo("grep 'two words' .");
        assertThat(grep.shown()).as("the selection is still the user's own: the reader sees the variable")
                .isEqualTo("/bin/sh -c grep ${selectedText} .");
    }

    private static final Host POWERSHELL = new Host(Os.WINDOWS, name -> null, f -> true,
            name -> "pwsh".equals(name) ? "C:\\PS\\pwsh.exe" : null);

    private static TaskDef inCmd(String command) {
        return task("{\"label\":\"c\",\"type\":\"shell\",\"command\":\"" + command + "\","
                + "\"options\":{\"shell\":{\"executable\":\"C:\\\\Windows\\\\System32\\\\cmd.exe\",\"args\":[\"/d\",\"/c\"]}}}");
    }

    @Test
    @DisplayName("PowerShell takes a value single-quoted with its quotes doubled; a plain path stays as written")
    void powershellQuoting() {
        TaskDef lint = task("{\"label\":\"lint\",\"type\":\"shell\",\"command\":\"eslint ${fileBasename}\"}");
        assertThat(((Launch) VsCodeTasks.resolve(lint, project.toFile(), POWERSHELL, editing(project.resolve("a.js"))))
                .shown()).isEqualTo("C:\\PS\\pwsh.exe -Command eslint a.js");
        assertThat(((Launch) VsCodeTasks.resolve(lint, project.toFile(), POWERSHELL,
                editing(project.resolve("it's $(calc) 100%.js")))).shown())
                .isEqualTo("C:\\PS\\pwsh.exe -Command eslint 'it''s $(calc) 100%.js'");
    }

    @Test
    @DisplayName("cmd.exe takes a value double-quoted, and refuses by name only what quotes cannot make inert: % ! \" and a line break")
    void cmdQuoting() {
        Launch spaced = (Launch) VsCodeTasks.resolve(inCmd("type ${fileBasename}"), project.toFile(), POWERSHELL,
                editing(project.resolve("my notes & more.txt")));
        assertThat(spaced.argv()).containsExactly("C:\\Windows\\System32\\cmd.exe", "/d", "/s", "/c",
                "\"type \"my notes & more.txt\"\"");
        for (String name : new String[] {"100%PATH%.txt", "wow!.txt", "line\nbreak.txt"}) {
            assertThat(VsCodeTasks.resolve(inCmd("type ${fileBasename}"), project.toFile(), POWERSHELL,
                    editing(project.resolve(name)))).as(name).isEqualTo(new Refused(Reason.UNQUOTED_VALUE, "${fileBasename}"));
        }
        Map<String, InputDef> inputs = Map.of("q", new InputDef("q", "promptString", "Q", null, false, List.of(), null));
        assertThat(VsCodeTasks.resolve(inCmd("echo ${input:q}"), project.toFile(), POWERSHELL,
                new Vars(EditorContext.NONE, "/h", inputs, Map.of("q", "say \"hi\""))))
                .as("a double quote ends cmd's quoted run").isEqualTo(new Refused(Reason.UNQUOTED_VALUE, "${input:q}"));
        assertThat(VsCodeTasks.resolve(inCmd("echo %PATH% ${fileBasename}"), project.toFile(), POWERSHELL,
                editing(project.resolve("a.txt")))).as("the FILE's own % is the file's").isInstanceOf(Launch.class);
    }

    @Test
    @DisplayName("the plan refuses a cmd.exe value before anything is asked or run")
    void refusedBeforeTrust() {
        TaskDef type = inCmd("type ${fileBasename}");
        VsCodeTaskPlan.Outcome outcome = VsCodeTaskPlan.check(type, new TasksFile(List.of(type), Map.of()),
                project.toFile(), POWERSHELL, new EditorContext(project.resolve("50%.txt"), 1, 1, null), "/h");
        assertThat(outcome).isEqualTo(new VsCodeTaskPlan.Refusal("c", new Refused(Reason.UNQUOTED_VALUE, "${fileBasename}")));
    }

    /* ------------------------------------------ the user's own environment */

    @Test
    @DisplayName("${env:NAME} reaches the process and never the line a reader is shown")
    void environmentNeverShown() {
        TaskDef curl = task("{\"label\":\"c\",\"type\":\"shell\",\"command\":\"curl\","
                + "\"args\":[\"-H\",\"Authorization: ${env:TOKEN}\",\"x\"]}");
        Launch launch = (Launch) VsCodeTasks.resolve(curl, project.toFile(), LINUX, Vars.none());
        assertThat(launch.argv().get(2)).contains("ghp_SECRET_TOKEN");
        assertThat(launch.shown()).as("supplied whenever the argv carries the environment")
                .isEqualTo("/bin/sh -c curl -H 'Authorization: ${env:TOKEN}' x")
                .doesNotContain("ghp_SECRET_TOKEN");

        TaskDef process = task("{\"label\":\"p\",\"type\":\"process\",\"command\":\"deploy\",\"args\":[\"${env:TOKEN}\"]}");
        Launch p = (Launch) VsCodeTasks.resolve(process, project.toFile(), LINUX, Vars.none());
        assertThat(p.argv()).containsExactly("deploy", "ghp_SECRET_TOKEN");
        assertThat(p.shown()).isEqualTo("deploy ${env:TOKEN}");
        assertThat(p.toString()).doesNotContain("ghp_SECRET_TOKEN");

        TaskDef plain = task("{\"label\":\"p\",\"type\":\"process\",\"command\":\"make\",\"args\":[\"all\"]}");
        assertThat(((Launch) VsCodeTasks.resolve(plain, project.toFile(), LINUX, Vars.none())).shown())
                .as("nothing of the user's in it: the argv is the line").isNull();
    }

    @Test
    @DisplayName("on Windows the line a reader is shown is the PowerShell line, not its base64; what runs is unchanged")
    void windowsHeaderIsReadable() {
        Host win = new Host(Os.WINDOWS, name -> null, f -> true, name -> "pwsh".equals(name) ? "C:\\PS\\pwsh.exe" : null);
        TaskDef build = task("{\"label\":\"b\",\"type\":\"shell\",\"command\":\"npm run build\"}");
        Launch launch = (Launch) VsCodeTasks.resolve(build, project.toFile(), win, Vars.none());
        assertThat(launch.argv()).containsExactly("C:\\PS\\pwsh.exe", "-EncodedCommand",
                Base64.getEncoder().encodeToString("npm run build".getBytes(StandardCharsets.UTF_16LE)));
        assertThat(launch.shown()).isEqualTo("C:\\PS\\pwsh.exe -Command npm run build");
    }

    /* ------------------------------------ an environment no process takes */

    @Test
    @DisplayName("an env name with = or NUL, or a value with NUL, is refused by its NAME before any process is asked to take it")
    void environmentNoProcessCanTake() {
        TaskDef equalsSign = task("{\"label\":\"e\",\"command\":\"make\",\"options\":{\"env\":{\"A=B\":\"x\"}}}");
        assertThat(VsCodeTasks.resolve(equalsSign, project.toFile(), LINUX, Vars.none()))
                .isEqualTo(new Refused(Reason.ENV_INVALID, "A=B"));
        TaskDef nul = task("{\"label\":\"e\",\"command\":\"make\",\"options\":{\"env\":{\"X\":\"a\\u0000b\"}}}");
        Resolved refused = VsCodeTasks.resolve(nul, project.toFile(), LINUX, Vars.none());
        assertThat(refused).isEqualTo(new Refused(Reason.ENV_INVALID, "X"));
        TaskDef nulName = task("{\"label\":\"e\",\"command\":\"make\",\"options\":{\"env\":{\"X\\u0000Y\":\"v\"}}}");
        assertThat(VsCodeTasks.resolve(nulName, project.toFile(), LINUX, Vars.none()))
                .isEqualTo(new Refused(Reason.ENV_INVALID, "X\uFFFDY"));
        TaskDef fine = task("{\"label\":\"e\",\"command\":\"make\",\"options\":{\"env\":{\"X\":\"a=b\"}}}");
        assertThat(VsCodeTasks.resolve(fine, project.toFile(), LINUX, Vars.none())).isInstanceOf(Launch.class);
        assertThat(VsCodeTaskSearchProvider.refusal("e", new Refused(Reason.ENV_INVALID, "A=B")))
                .isEqualTo("Task \"e\" sets the environment variable \"A=B\", and a process cannot be given that name "
                        + "or its value (an = sign or a NUL character); nothing was run.");
    }

    /* ------------------------------------------------- a symlinked project */

    @Test
    @DisplayName("a project opened through a symlink accepts a path spelled through its real location, and still refuses outside")
    void symlinkedProject() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "a symbolic link needs a privilege a Windows runner may not hold");
        Path real = project.toRealPath();
        Files.createDirectories(real.resolve("sub"));
        Path link = Files.createTempDirectory("nmox-link").resolve("p");
        Files.createSymbolicLink(link, real);
        assertThat(VsCodeTasks.inside(link.toFile(), real.resolve("sub").toString()))
                .as("the real spelling of a folder inside").isNotNull()
                .satisfies(f -> assertThat(f.getCanonicalFile()).isEqualTo(real.resolve("sub").toFile().getCanonicalFile()));
        assertThat(VsCodeTasks.inside(link.toFile(), link.resolve("sub").toString())).isNotNull();
        assertThat(VsCodeTasks.inside(link.toFile(), real.getParent().toString())).as("the real parent is outside").isNull();
        assertThat(VsCodeTasks.inside(real.toFile(), link.resolve("sub").toString()))
                .as("and the other way round: the project real, the path through the link").isNotNull();
    }

    /* ------------------------------------------ a batch file's second parse */

    @Test
    @DisplayName("Windows: a batch file handed an argument with cmd syntax in it is refused; an .exe, or a plain argument, runs")
    void batchFileArguments() {
        Map<String, String> path = Map.of("npm", "C:\\nodejs\\npm.cmd", "node", "C:\\nodejs\\node.exe");
        Host win = new Host(Os.WINDOWS, name -> null, f -> true, path::get);
        Map<String, InputDef> inputs = Map.of("pkg", new InputDef("pkg", "promptString", "Package", null, false, List.of(), null));
        TaskDef add = task("{\"label\":\"add\",\"type\":\"process\",\"command\":\"npm\",\"args\":[\"install\",\"${input:pkg}\"]}");
        Vars evil = new Vars(EditorContext.NONE, "/h", inputs, Map.of("pkg", "left-pad&calc.exe"));
        assertThat(VsCodeTasks.resolve(add, project.toFile(), win, evil)).isEqualTo(new Refused(Reason.BATCH_ARGUMENT, "npm"));
        Vars fine = new Vars(EditorContext.NONE, "/h", inputs, Map.of("pkg", "left-pad"));
        assertThat(VsCodeTasks.resolve(add, project.toFile(), win, fine)).isInstanceOf(Launch.class);

        TaskDef run = task("{\"label\":\"r\",\"type\":\"process\",\"command\":\"node\",\"args\":[\"-e\",\"${input:pkg}\"]}");
        assertThat(VsCodeTasks.resolve(run, project.toFile(), win, evil)).as("an .exe parses its own arguments")
                .isInstanceOf(Launch.class);
        TaskDef direct = task("{\"label\":\"d\",\"type\":\"process\",\"command\":\"C:\\\\tools\\\\build.BAT\",\"args\":[\"a|b\"]}");
        assertThat(VsCodeTasks.resolve(direct, project.toFile(), win, Vars.none()))
                .isEqualTo(new Refused(Reason.BATCH_ARGUMENT, "C:\\tools\\build.BAT"));
        assertThat(VsCodeTasks.resolve(add, project.toFile(), LINUX, evil)).as("POSIX has no batch files")
                .isInstanceOf(Launch.class);
    }

    /* --------------------------------------------- what toString would print */

    @Test
    @DisplayName("Launch, Vars and EditorContext print no password, no selection and no environment value")
    void recordsPrintNoSecrets() {
        Map<String, InputDef> inputs = Map.of("pw", new InputDef("pw", "promptString", "Pw", null, true, List.of(), null));
        Vars vars = new Vars(new EditorContext(project.resolve("a.js"), 2, 3, "private words"), "/h", inputs,
                Map.of("pw", "hunter2"));
        assertThat(vars.toString()).doesNotContain("hunter2").doesNotContain("private words").contains("pw");
        TaskDef deploy = task("{\"label\":\"d\",\"type\":\"process\",\"command\":\"deploy\",\"args\":[\"${input:pw}\"],"
                + "\"options\":{\"env\":{\"KEY\":\"${input:pw}\"}}}");
        Launch launch = (Launch) VsCodeTasks.resolve(deploy, project.toFile(), LINUX, vars);
        assertThat(launch.argv()).contains("hunter2");
        assertThat(launch.toString()).doesNotContain("hunter2").contains("KEY").contains("${input:pw}");
    }

    @Test
    @DisplayName("the refusal for a cmd.exe value names the variable and the characters, never the value")
    void refusalSentence() {
        assertThat(VsCodeTaskSearchProvider.refusal("lint", new Refused(Reason.UNQUOTED_VALUE, "${file}")))
                .isEqualTo("Task \"lint\" runs in cmd.exe and puts ${file} in its command line, and that value holds "
                        + "a character cmd.exe acts on even inside quotes (a %, a !, a double quote or a line break); "
                        + "nothing was run.");
    }
}
