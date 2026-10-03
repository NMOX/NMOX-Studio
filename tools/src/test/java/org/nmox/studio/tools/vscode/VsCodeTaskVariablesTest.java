package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.Host;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputOption;
import org.nmox.studio.tools.vscode.VsCodeTasks.Launch;
import org.nmox.studio.tools.vscode.VsCodeTasks.NpmLaunch;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;
import org.nmox.studio.tools.vscode.VsCodeTasks.Reason;
import org.nmox.studio.tools.vscode.VsCodeTasks.Refused;
import org.nmox.studio.tools.vscode.VsCodeTasks.Resolved;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.TasksFile;
import org.nmox.studio.tools.vscode.VsCodeTasks.Vars;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The variables a project cannot fill itself: the editor's ({@code
 * ${file}} and its family, the caret, the selection), {@code
 * ${userHome}}, and {@code ${input:id}} — each with the meaning VS Code's
 * Variables Reference gives it, and each refused by name when the value
 * is not there to give.
 */
class VsCodeTaskVariablesTest {

    @TempDir
    Path project;

    private static final Host LINUX = new Host(Os.LINUX, name -> null, f -> false, name -> null);

    private static final String ARGS = "{\"tasks\":[{\"label\":\"t\",\"type\":\"process\",\"command\":\"run\",\"args\":[%s]}]}";

    private static TaskDef args(String... args) {
        StringBuilder list = new StringBuilder();
        for (String a : args) {
            list.append(list.length() == 0 ? "" : ",").append('"').append(a).append('"');
        }
        return VsCodeTasks.parse(ARGS.formatted(list), Os.LINUX).get(0);
    }

    private Vars editor(Path file, int line, int column, String selected) {
        return new Vars(new EditorContext(file, line, column, selected), "/home/dev", Map.of(), Map.of());
    }

    private Resolved resolve(TaskDef task, Vars vars) {
        return VsCodeTasks.resolve(task, project.toFile(), LINUX, vars);
    }

    private List<String> argv(Vars vars, String... args) {
        Resolved resolved = resolve(args(args), vars);
        assertThat(resolved).isInstanceOf(Launch.class);
        List<String> argv = ((Launch) resolved).argv();
        return argv.subList(1, argv.size());
    }

    @Test
    @DisplayName("the file variables are VS Code's: the Variables Reference's own example, value for value")
    void fileVariables() {
        // the reference: workspace /home/your-username/your-project, file …/folder/file.ext, line 5
        Path file = project.resolve("folder").resolve("file.ext");
        Vars vars = editor(file, 5, 9, "selected");
        String root = project.toAbsolutePath().normalize().toString();
        String sep = File.separator;
        assertThat(argv(vars, "${file}", "${relativeFile}", "${relativeFileDirname}", "${fileBasename}",
                "${fileBasenameNoExtension}", "${fileExtname}", "${fileDirname}", "${fileDirnameBasename}",
                "${lineNumber}", "${columnNumber}", "${selectedText}", "${userHome}", "${workspaceFolderBasename}",
                "${pathSeparator}${/}"))
                .containsExactly(root + sep + "folder" + sep + "file.ext", "folder" + sep + "file.ext", "folder",
                        "file.ext", "file", ".ext", root + sep + "folder", "folder",
                        "5", "9", "selected", "/home/dev", project.getFileName().toString(), sep + sep);
    }

    @Test
    @DisplayName("a file at the project root is in \".\"; a file outside the project is named by the way to it")
    void relativeToTheWorkspaceFolder() {
        assertThat(argv(editor(project.resolve("README.md"), 1, 1, null), "${relativeFile}", "${relativeFileDirname}"))
                .as("VS Code: an empty relative directory is \".\"").containsExactly("README.md", ".");
        Path outside = project.resolveSibling("elsewhere").resolve("notes.txt");
        String sep = File.separator;
        assertThat(argv(editor(outside, 1, 1, null), "${file}", "${relativeFile}", "${relativeFileDirname}"))
                .as("the user may have any file open: Node's path.relative walks up to it")
                .containsExactly(outside.toAbsolutePath().normalize().toString(),
                        ".." + sep + "elsewhere" + sep + "notes.txt", ".." + sep + "elsewhere");
    }

    @Test
    @DisplayName("an extension is Node's path.extname: from the last dot, and a leading dot is not one")
    void extname() {
        assertThat(VsCodeTasks.extname("file.ext")).isEqualTo(".ext");
        assertThat(VsCodeTasks.extname("archive.tar.gz")).isEqualTo(".gz");
        assertThat(VsCodeTasks.extname("Makefile")).isEmpty();
        assertThat(VsCodeTasks.extname(".bashrc")).isEmpty();
        assertThat(VsCodeTasks.extname(".eslintrc.json")).isEqualTo(".json");
        assertThat(VsCodeTasks.extname("trailing.")).isEqualTo(".");
        assertThat(argv(editor(project.resolve(".bashrc"), 1, 1, null), "${fileBasenameNoExtension}", "[${fileExtname}]"))
                .containsExactly(".bashrc", "[]");
        assertThat(argv(editor(project.resolve("a.tar.gz"), 1, 1, null), "${fileBasenameNoExtension}"))
                .containsExactly("a.tar");
    }

    @Test
    @DisplayName("with no file open, every file variable refuses naming itself — nothing runs with a blank")
    void noFileOpenRefuses() {
        for (String variable : new String[] {"${file}", "${relativeFile}", "${relativeFileDirname}",
                "${fileBasename}", "${fileBasenameNoExtension}", "${fileDirname}", "${fileDirnameBasename}",
                "${fileExtname}"}) {
            assertThat(resolve(args("pre-" + variable), editor(null, 3, 1, "text"))).as(variable)
                    .isEqualTo(new Refused(Reason.NEEDS_FILE, variable));
        }
        // a buffer that is not on disk still has a caret and a selection
        assertThat(argv(editor(null, 3, 7, "text"), "${lineNumber}:${columnNumber}", "${selectedText}"))
                .containsExactly("3:7", "text");
        // no editor at all: nothing has a caret
        for (String variable : new String[] {"${lineNumber}", "${columnNumber}", "${selectedText}"}) {
            assertThat(resolve(args(variable), editor(null, 0, 0, null))).as(variable)
                    .isEqualTo(new Refused(Reason.NEEDS_FILE, variable));
        }
        // wherever it is written: the working folder, the environment, the command
        TaskDef inCwd = VsCodeTasks.parse(
                "{\"tasks\":[{\"label\":\"t\",\"command\":\"make\",\"options\":{\"cwd\":\"${fileDirname}\"}}]}", Os.LINUX).get(0);
        assertThat(resolve(inCwd, Vars.none())).isEqualTo(new Refused(Reason.NEEDS_FILE, "${fileDirname}"));
        TaskDef inEnv = VsCodeTasks.parse(
                "{\"tasks\":[{\"label\":\"t\",\"command\":\"make\",\"options\":{\"env\":{\"F\":\"${file}\"}}}]}", Os.LINUX).get(0);
        assertThat(resolve(inEnv, Vars.none())).isEqualTo(new Refused(Reason.NEEDS_FILE, "${file}"));
    }

    @Test
    @DisplayName("${selectedText}: no selection refuses, as VS Code does; a selection over the cap refuses; the cap itself runs")
    void selectedText() {
        Path file = project.resolve("a.js");
        assertThat(resolve(args("${selectedText}"), editor(file, 1, 1, null)))
                .isEqualTo(new Refused(Reason.NEEDS_SELECTION, "${selectedText}"));
        assertThat(resolve(args("${selectedText}"), editor(file, 1, 1, "")))
                .isEqualTo(new Refused(Reason.NEEDS_SELECTION, "${selectedText}"));
        String atCap = "x".repeat(VsCodeTasks.MAX_SELECTED_TEXT);
        assertThat(argv(editor(file, 1, 1, atCap), "${selectedText}")).containsExactly(atCap);
        assertThat(resolve(args("${selectedText}"), editor(file, 1, 1, atCap + "y")))
                .as("one character more: refused, never run with a part of it")
                .isEqualTo(new Refused(Reason.SELECTION_TOO_LONG, "${selectedText}"));
        assertThat(argv(editor(file, 1, 1, "x".repeat(50_000)), "${file}"))
                .as("a long selection refuses only the task that uses it").hasSize(1);
    }

    @Test
    @DisplayName("values are substituted once: a selection or an answer that looks like a variable stays the text it is")
    void onePass() {
        Vars vars = new Vars(new EditorContext(project.resolve("a.js"), 1, 1, "${file} $(rm -rf ~) ${input:x}"),
                "/home/dev", Map.of("x", new InputDef("x", "promptString", "X", null, false, List.of(), null)),
                Map.of("x", "${workspaceFolder} ${selectedText}"));
        assertThat(argv(vars, "${selectedText}", "${input:x}"))
                .containsExactly("${file} $(rm -rf ~) ${input:x}", "${workspaceFolder} ${selectedText}");
    }

    @Test
    @DisplayName("a shell task quotes an argument that took the selection; a process task hands it over as one argument")
    void selectionOnAShellLine() {
        Vars vars = editor(project.resolve("a.js"), 1, 1, "two words; rm -rf '~'");
        TaskDef shell = VsCodeTasks.parse("{\"tasks\":[{\"label\":\"t\",\"type\":\"shell\",\"command\":\"grep\","
                + "\"args\":[\"-F\",\"${selectedText}\",\"${file}\"]}]}", Os.LINUX).get(0);
        Launch launch = (Launch) resolve(shell, vars);
        String file = project.resolve("a.js").toAbsolutePath().normalize().toString();
        assertThat(launch.argv()).containsExactly("/bin/sh", "-c",
                "grep -F 'two words; rm -rf '\\''~'\\''' " + VsCodeTasks.shQuote(file, null));
    }

    @Test
    @DisplayName("the working folder keeps its containment: ${fileDirname} of a file outside the project is outside")
    void cwdFromTheEditorStaysInside() throws Exception {
        TaskDef task = VsCodeTasks.parse(
                "{\"tasks\":[{\"label\":\"t\",\"command\":\"make\",\"args\":[\"${file}\"],\"options\":{\"cwd\":\"${fileDirname}\"}}]}",
                Os.LINUX).get(0);
        Files.createDirectories(project.resolve("src"));
        Launch inside = (Launch) resolve(task, editor(project.resolve("src").resolve("a.c"), 1, 1, null));
        assertThat(inside.dir().getCanonicalFile()).isEqualTo(project.resolve("src").toFile().getCanonicalFile());

        Path outside = project.resolveSibling("elsewhere").resolve("a.c");
        assertThat(resolve(task, editor(outside, 1, 1, null)))
                .as("the file is fine as an argument; as the folder a command runs in, it is refused")
                .isEqualTo(new Refused(Reason.CWD_OUTSIDE, outside.getParent().toAbsolutePath().normalize().toString()));
    }

    /* ------------------------------------------------------------------ inputs */

    @Test
    @DisplayName("inputs[] is read: promptString and pickString, defaults, password, options as strings or {label, value}")
    void inputsAreRead() {
        TasksFile file = VsCodeTasks.parseFile("""
                {"version":"2.0.0","tasks":[],
                 "inputs":[
                   {"id":"name","type":"promptString","description":"Component name","default":"Widget"},
                   {"id":"token","type":"promptString","description":"API token","password":true},
                   {"id":"env","type":"pickString","description":"Environment",
                    "options":["dev",{"label":"Production","value":"prod"},{"value":"stage"}],"default":"prod"},
                   {"type":"promptString","description":"no id"},
                   "not an object",
                   {"id":"pid","type":"command","command":"extension.pickProcess"},
                   {"id":"empty","type":"pickString","description":"x","options":[]},
                   {"id":"bad","type":"pickString","description":"x","options":["a",{"label":"no value"}]},
                   {"id":"none","type":"pickString","description":"x"},
                   {"id":"dup","type":"promptString","description":"first"},
                   {"id":"dup","type":"promptString","description":"second"}
                 ]}""", Os.LINUX);
        Map<String, InputDef> inputs = file.inputs();
        assertThat(inputs.keySet()).containsExactly("name", "token", "env", "pid", "empty", "bad", "none", "dup");
        assertThat(inputs.get("name")).isEqualTo(
                new InputDef("name", "promptString", "Component name", "Widget", false, List.of(), null));
        assertThat(inputs.get("token").password()).isTrue();
        assertThat(inputs.get("token").defaultValue()).isNull();
        assertThat(inputs.get("env").options()).containsExactly(new InputOption(null, "dev"),
                new InputOption("Production", "prod"), new InputOption(null, "stage"));
        assertThat(inputs.get("env").options()).extracting(InputOption::display)
                .as("VS Code shows label: value").containsExactly("dev", "Production: prod", "stage");
        assertThat(inputs.get("env").defaultValue()).isEqualTo("prod");
        assertThat(inputs.get("env").problem()).isNull();
        assertThat(inputs.get("pid").type()).isEqualTo("command");
        assertThat(inputs.get("empty").problem()).isEqualTo("options");
        assertThat(inputs.get("bad").problem()).isEqualTo("value");
        assertThat(inputs.get("none").problem()).isEqualTo("options");
        assertThat(inputs.get("dup").description()).as("of two with one id the later is the one VS Code asks")
                .isEqualTo("second");
        assertThat(VsCodeTasks.parseFile("{\"tasks\":[]}", Os.LINUX).inputs()).isEmpty();
        assertThat(VsCodeTasks.inputIds("${input:a} ${file} ${input:b}${input:a} ${input:}"))
                .containsExactly("a", "b", "a");
    }

    private static final Map<String, InputDef> INPUTS = Map.of(
            "token", new InputDef("token", "promptString", "Token", null, true, List.of(), null),
            "env", new InputDef("env", "pickString", "Env", null, false,
                    List.of(new InputOption(null, "dev"), new InputOption(null, "prod")), null));

    private Vars answered(Map<String, String> answers, String selected) {
        return new Vars(new EditorContext(project.resolve("a.js"), 1, 1, selected), "/home/dev", INPUTS, answers);
    }

    @Test
    @DisplayName("an input with no answer refuses naming it; before the questions are asked a declared input passes")
    void unansweredInput() {
        TaskDef task = args("${input:env}", "${input:token}");
        assertThat(resolve(task, answered(Map.of("env", "dev"), null)))
                .isEqualTo(new Refused(Reason.INPUT_UNANSWERED, "token"));
        assertThat(VsCodeTasks.variableProblem("${input:env} ${input:token}",
                new Vars(EditorContext.NONE, "", INPUTS, null))).as("not asked yet").isNull();
        assertThat(VsCodeTasks.variableProblem("${input:nope}", new Vars(EditorContext.NONE, "", INPUTS, null)))
                .isEqualTo(new Refused(Reason.INPUT_UNDEFINED, "nope"));
    }

    @Test
    @DisplayName("a password's answer and the selection reach the argv and NOT the line that is shown: there they stay as written")
    void whatIsShown() {
        TaskDef task = VsCodeTasks.parse("{\"tasks\":[{\"label\":\"t\",\"type\":\"shell\",\"command\":\"deploy --env ${input:env}\","
                + "\"args\":[\"--token\",\"${input:token}\",\"${selectedText}\"]}]}", Os.LINUX).get(0);
        Launch launch = (Launch) resolve(task, answered(Map.of("env", "prod", "token", "hunter2 with space"), "my own text"));
        assertThat(launch.argv()).containsExactly("/bin/sh", "-c",
                "deploy --env prod --token 'hunter2 with space' 'my own text'");
        assertThat(launch.shown())
                .as("an ordinary answer is shown; the password and the selection are the variables the file wrote")
                .isEqualTo("/bin/sh -c deploy --env prod --token '${input:token}' '${selectedText}'")
                .doesNotContain("hunter2").doesNotContain("my own text");

        Launch plain = (Launch) resolve(args("${input:env}", "${file}"), answered(Map.of("env", "prod"), null));
        assertThat(plain.shown()).as("nothing of the user's own in it: the argv is shown as it is").isNull();

        TaskDef inEnv = VsCodeTasks.parse("{\"tasks\":[{\"label\":\"t\",\"command\":\"deploy\","
                + "\"options\":{\"env\":{\"TOKEN\":\"${input:token}\"}}}]}", Os.LINUX).get(0);
        Launch env = (Launch) resolve(inEnv, answered(Map.of("token", "hunter2"), null));
        assertThat(env.env()).containsEntry("TOKEN", "hunter2");
        assertThat(env.shown()).as("the password is in the environment, not the line: the argv is the line")
                .isNull();
        assertThat(String.join(" ", env.argv())).isEqualTo("deploy");
    }

    @Test
    @DisplayName("a refusal names a folder as a reader may see it: the selection in options.cwd is never in the sentence")
    void refusalsDoNotCarryTheSelection() {
        TaskDef task = VsCodeTasks.parse("{\"tasks\":[{\"label\":\"t\",\"command\":\"make\","
                + "\"options\":{\"cwd\":\"builds/${selectedText}/${input:env}\"}}]}", Os.LINUX).get(0);
        assertThat(resolve(task, answered(Map.of("env", "prod"), "private words")))
                .isEqualTo(new Refused(Reason.CWD_MISSING, "builds/${selectedText}/prod"));
    }

    @Test
    @DisplayName("a password is an argument, never a name: as the program, the shell, the folder or an npm script it is refused")
    void passwordsAreNeverNames() {
        Vars vars = answered(Map.of("token", "hunter2", "env", "prod"), null);
        String[] named = {
            "{\"label\":\"t\",\"type\":\"process\",\"command\":\"${input:token}\"}",
            "{\"label\":\"t\",\"command\":\"make\",\"options\":{\"cwd\":\"builds/${input:token}\"}}",
            "{\"label\":\"t\",\"type\":\"shell\",\"command\":\"make\",\"options\":{\"shell\":{\"executable\":\"/bin/${input:token}\"}}}",
            "{\"label\":\"t\",\"type\":\"npm\",\"script\":\"deploy:${input:token}\"}",
            "{\"label\":\"t\",\"type\":\"npm\",\"script\":\"deploy\",\"path\":\"${input:token}\"}"};
        for (String json : named) {
            TaskDef task = VsCodeTasks.parse("{\"tasks\":[" + json + "]}", Os.LINUX).get(0);
            assertThat(resolve(task, vars)).as(json)
                    .isEqualTo(new Refused(Reason.PASSWORD_SHOWN, "${input:token}"));
        }
        // on a shell LINE, in an argument, in the environment: that is what a password is for
        TaskDef line = VsCodeTasks.parse("{\"tasks\":[{\"label\":\"t\",\"type\":\"shell\","
                + "\"command\":\"deploy --token ${input:token}\"}]}", Os.LINUX).get(0);
        Launch launch = (Launch) resolve(line, vars);
        assertThat(launch.argv()).containsExactly("/bin/sh", "-c", "deploy --token hunter2");
        assertThat(launch.shown()).isEqualTo("/bin/sh -c deploy --token ${input:token}");
        // any other input may name what it likes
        TaskDef picked = VsCodeTasks.parse(
                "{\"tasks\":[{\"label\":\"n\",\"type\":\"npm\",\"script\":\"deploy:${input:env}\"}]}", Os.LINUX).get(0);
        assertThat(resolve(picked, vars)).isEqualTo(new NpmLaunch(project.toFile(), "deploy:prod"));
    }
}
