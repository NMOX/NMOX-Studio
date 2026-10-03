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

    @Test
    @DisplayName("${file} in a shell task's COMMAND line, naming a file the shell would read as code, is refused naming the variable")
    void unquotedFileNameInTheCommandLine() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "the fixture's paths are POSIX paths, which a POSIX shell line holds as plain words");
        Path hostile = Files.createTempDirectory("downloads").resolve("x$(touch PWNED).js");
        TaskDef lint = task("{\"label\":\"lint\",\"type\":\"shell\",\"command\":\"eslint ${file}\"}");
        assertThat(VsCodeTasks.resolve(lint, project.toFile(), LINUX, editing(hostile)))
                .isEqualTo(new Refused(Reason.UNQUOTED_VALUE, "${file}"));

        // the same file in ARGS is quoted for the shell: it runs, as one word
        TaskDef quoted = task("{\"label\":\"lint\",\"type\":\"shell\",\"command\":\"eslint\",\"args\":[\"${file}\"]}");
        Launch launch = (Launch) VsCodeTasks.resolve(quoted, project.toFile(), LINUX, editing(hostile));
        assertThat(launch.argv().get(2)).isEqualTo("eslint '" + hostile.toAbsolutePath().normalize() + "'");

        // a file name that is one plain word runs in the command line as it always did
        Path plain = project.resolve("src").resolve("app.js");
        Launch ok = (Launch) VsCodeTasks.resolve(lint, project.toFile(), LINUX, editing(plain));
        assertThat(ok.argv()).containsExactly("/bin/sh", "-c", "eslint " + plain.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("an input's answer and the selection in the command line are judged the same way; the file's own text is not")
    void answersAndSelectionInTheCommandLine() {
        Map<String, InputDef> inputs = Map.of("who", new InputDef("who", "promptString", "Who", null, false, List.of(), null));
        TaskDef greet = task("{\"label\":\"g\",\"type\":\"shell\",\"command\":\"echo ${input:who} && ls\"}");
        Vars evil = new Vars(EditorContext.NONE, "/h", inputs, Map.of("who", "a; rm -rf ~"));
        assertThat(VsCodeTasks.resolve(greet, project.toFile(), LINUX, evil))
                .isEqualTo(new Refused(Reason.UNQUOTED_VALUE, "${input:who}"));
        Vars fine = new Vars(EditorContext.NONE, "/h", inputs, Map.of("who", "world"));
        assertThat(((Launch) VsCodeTasks.resolve(greet, project.toFile(), LINUX, fine)).argv().get(2))
                .as("the && is the FILE's, and stays the shell's").isEqualTo("echo world && ls");

        TaskDef sel = task("{\"label\":\"s\",\"type\":\"shell\",\"command\":\"grep ${selectedText} .\"}");
        Vars selected = new Vars(new EditorContext(project.resolve("a.js"), 1, 1, "two words"), "/h", Map.of(), Map.of());
        assertThat(VsCodeTasks.resolve(sel, project.toFile(), LINUX, selected))
                .isEqualTo(new Refused(Reason.UNQUOTED_VALUE, "${selectedText}"));
    }

    @Test
    @DisplayName("on Windows a drive path is one plain word; a cmd % or a space is not")
    void windowsWords() {
        Host win = new Host(Os.WINDOWS, name -> null, f -> true, name -> "pwsh".equals(name) ? "C:\\PS\\pwsh.exe" : null);
        TaskDef lint = task("{\"label\":\"lint\",\"type\":\"shell\",\"command\":\"eslint ${relativeFile}\"}");
        assertThat(VsCodeTasks.resolve(lint, project.toFile(), win, editing(project.resolve("src").resolve("a.js"))))
                .isInstanceOf(Launch.class);
        assertThat(VsCodeTasks.resolve(lint, project.toFile(), win, editing(project.resolve("100%PATH%.js"))))
                .isEqualTo(new Refused(Reason.UNQUOTED_VALUE, "${relativeFile}"));
    }

    @Test
    @DisplayName("the plan refuses the hostile file name before anything is asked or run")
    void refusedBeforeTrust() throws Exception {
        Path hostile = Files.createTempDirectory("downloads").resolve("a b.js");
        TaskDef lint = task("{\"label\":\"lint\",\"type\":\"shell\",\"command\":\"eslint ${file}\"}");
        VsCodeTaskPlan.Outcome outcome = VsCodeTaskPlan.check(lint, new TasksFile(List.of(lint), Map.of()),
                project.toFile(), LINUX, new EditorContext(hostile, 1, 1, null), "/h");
        assertThat(outcome).isEqualTo(new VsCodeTaskPlan.Refusal("lint", new Refused(Reason.UNQUOTED_VALUE, "${file}")));
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
    @DisplayName("the refusal for a value in the command line names the variable and says where it belongs")
    void refusalSentence() {
        assertThat(VsCodeTaskSearchProvider.refusal("lint", new Refused(Reason.UNQUOTED_VALUE, "${file}")))
                .isEqualTo("Task \"lint\" puts ${file} in its command line, where the shell would read its value as "
                        + "more than plain text; nothing was run. Move ${file} into \"args\", which are quoted for the shell.");
    }
}
