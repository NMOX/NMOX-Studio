package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.tools.vscode.VsCodeTasks.Launch;
import org.nmox.studio.tools.vscode.VsCodeTasks.NpmLaunch;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;
import org.nmox.studio.tools.vscode.VsCodeTasks.Reason;
import org.nmox.studio.tools.vscode.VsCodeTasks.Refused;
import org.nmox.studio.tools.vscode.VsCodeTasks.Resolved;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pure half of VS Code tasks (v3.1.0): what a {@code .vscode/tasks.json}
 * declares, and for one task exactly what would run — or why not.
 */
class VsCodeTasksTest {

    @TempDir
    Path project;

    private static final UnaryOperator<String> NO_ENV = name -> null;

    @BeforeEach
    void freshCache() {
        VsCodeTasks.clearCache();
    }

    private static TaskDef only(String json, Os os) {
        List<TaskDef> tasks = VsCodeTasks.parse(json, os);
        assertThat(tasks).hasSize(1);
        return tasks.get(0);
    }

    private Resolved resolve(String json, Os os) {
        return VsCodeTasks.resolve(only(json, os), project.toFile(), os, NO_ENV);
    }

    @Test
    @DisplayName("JSONC: line and block comments and trailing commas go; the same characters inside strings stay")
    void jsoncIsStripped() {
        String json = """
                // the file VS Code wrote
                {
                  "version": "2.0.0", /* schema */
                  "tasks": [
                    {
                      "label": "build // not a comment",
                      "type": "shell",
                      "command": "echo \\"/* kept */\\" http://x",
                      "args": ["a,", "b",],
                    },
                  ],
                }
                """;
        TaskDef task = only(json, Os.LINUX);
        assertThat(task.label()).isEqualTo("build // not a comment");
        assertThat(task.command().text()).isEqualTo("echo \"/* kept */\" http://x");
        assertThat(task.args()).extracting(VsCodeTasks.Value::text).containsExactly("a,", "b");
        assertThat(VsCodeTasks.stripJsonc("{\"a\": 1, /* x\ny */ }"))
                .as("a block comment keeps its line breaks, so a parse error names the real line")
                .isEqualTo("{\"a\": 1 \n }");
    }

    @Test
    @DisplayName("an entry without a label is skipped; a malformed file throws, which read() turns into an empty list")
    void labelsAndMalformed() throws Exception {
        List<TaskDef> tasks = VsCodeTasks.parse("""
                {"version": "2.0.0", "tasks": [
                  {"type": "shell", "command": "make"},
                  {"label": "   ", "command": "make"},
                  "not an object",
                  {"label": "test", "command": "make", "args": ["test"]}
                ]}""", Os.LINUX);
        assertThat(tasks).extracting(TaskDef::label).containsExactly("test");
        assertThat(tasks.get(0).type()).as("VS Code's default type").isEqualTo("process");

        assertThatThrownBy(() -> VsCodeTasks.parse("{ \"tasks\": [ ", Os.LINUX)).isNotNull();
        assertThat(VsCodeTasks.parse("{\"version\": \"2.0.0\"}", Os.LINUX)).isEmpty();

        Files.createDirectories(project.resolve(".vscode"));
        Files.writeString(project.resolve(".vscode/tasks.json"), "{ \"tasks\": [ {\"label\": ");
        assertThat(VsCodeTasks.read(project.toFile(), Os.LINUX)).as("malformed lists nothing").isEmpty();
        assertThat(VsCodeTasks.read(project.toFile(), Os.LINUX)).as("and again, from the cache").isEmpty();
        assertThat(VsCodeTasks.read(null, Os.LINUX)).isEmpty();
        assertThat(VsCodeTasks.read(project.resolve("nowhere").toFile(), Os.LINUX)).isEmpty();
    }

    @Test
    @DisplayName("read() is bounded: a tasks.json over the cap is refused before it is read, and lists nothing")
    void oversizedIsRefused() throws Exception {
        Files.createDirectories(project.resolve(".vscode"));
        String head = "{\"version\":\"2.0.0\",\"tasks\":[{\"label\":\"build\",\"command\":\"make\"}],\"pad\":\"";
        String pad = "x".repeat((int) VsCodeTasks.MAX_BYTES);
        Files.writeString(project.resolve(".vscode/tasks.json"), head + pad + "\"}");
        assertThat(VsCodeTasks.read(project.toFile(), Os.LINUX)).isEmpty();

        Files.writeString(project.resolve(".vscode/tasks.json"), head + "\"}");
        assertThat(VsCodeTasks.read(project.toFile(), Os.LINUX))
                .as("the same file under the cap parses — the cap, not the content, refused it")
                .extracting(TaskDef::label).containsExactly("build");
    }

    @Test
    @DisplayName("read() is cached by mtime: an edit to the file is seen")
    void cacheFollowsTheFile() throws Exception {
        Files.createDirectories(project.resolve(".vscode"));
        Path file = project.resolve(".vscode/tasks.json");
        Files.writeString(file, "{\"tasks\":[{\"label\":\"one\",\"command\":\"a\"}]}");
        assertThat(VsCodeTasks.read(project.toFile(), Os.LINUX)).extracting(TaskDef::label).containsExactly("one");
        Files.writeString(file, "{\"tasks\":[{\"label\":\"two\",\"command\":\"a\"},{\"label\":\"three\",\"command\":\"b\"}]}");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(
                Files.getLastModifiedTime(file).toMillis() + 5_000));
        assertThat(VsCodeTasks.read(project.toFile(), Os.LINUX)).extracting(TaskDef::label)
                .containsExactly("two", "three");
    }

    @Test
    @DisplayName("the running OS's override object is merged over the base, and only that OS's")
    void perOsOverride() {
        String json = """
                {"version": "2.0.0",
                 "options": {"env": {"SHARED": "1", "WHO": "file"}},
                 "tasks": [{
                   "label": "open",
                   "type": "process",
                   "command": "xdg-open",
                   "args": ["index.html"],
                   "options": {"cwd": "site", "env": {"WHO": "task"}},
                   "osx": {"command": "open", "options": {"env": {"MAC": "yes"}}},
                   "windows": {"command": "explorer", "args": ["index.html", "/select"]}
                 }]}""";
        TaskDef linux = only(json, Os.LINUX);
        TaskDef mac = only(json, Os.MAC);
        TaskDef windows = only(json, Os.WINDOWS);

        assertThat(linux.command().text()).isEqualTo("xdg-open");
        assertThat(mac.command().text()).isEqualTo("open");
        assertThat(windows.command().text()).isEqualTo("explorer");
        assertThat(windows.args()).extracting(VsCodeTasks.Value::text).containsExactly("index.html", "/select");
        assertThat(mac.args()).as("the base args where the override says nothing")
                .extracting(VsCodeTasks.Value::text).containsExactly("index.html");
        assertThat(mac.cwd()).as("options merge: the override's env did not drop the base cwd").isEqualTo("site");
        assertThat(mac.env()).containsExactlyInAnyOrderEntriesOf(Map.of("SHARED", "1", "WHO", "task", "MAC", "yes"));
        assertThat(linux.env()).as("the file-level options are defaults; the task wins")
                .containsExactlyInAnyOrderEntriesOf(Map.of("SHARED", "1", "WHO", "task"));
    }

    @Test
    @DisplayName("args may be {value, quoting} objects; the value is taken and the quoting honoured by the shell line")
    void argObjects() {
        TaskDef task = only("""
                {"tasks": [{"label": "say", "type": "shell", "command": {"value": "echo"},
                  "args": [{"value": "hello world", "quoting": "strong"}, {"value": ["a", "b"]},
                           "plain", "two words", {"value": "it's", "quoting": "weak"}, 3]}]}""", Os.LINUX);
        assertThat(task.command().text()).isEqualTo("echo");
        assertThat(task.args()).extracting(VsCodeTasks.Value::text)
                .containsExactly("hello world", "a b", "plain", "two words", "it's", "3");

        Launch launch = (Launch) VsCodeTasks.resolve(task, project.toFile(), Os.LINUX, NO_ENV);
        assertThat(launch.argv()).containsExactly("/bin/sh", "-c",
                "echo 'hello world' 'a b' plain 'two words' \"it's\" 3");
        assertThat(VsCodeTasks.shQuote("it's", null)).isEqualTo("'it'\\''s'");
        assertThat(VsCodeTasks.shQuote("a b", "escape")).isEqualTo("a\\ b");
        assertThat(VsCodeTasks.shQuote("safe", "strong")).isEqualTo("'safe'");
    }

    @Test
    @DisplayName("shell: sh -c on POSIX, cmd.exe /d /s /c on Windows; process: the argv as it is")
    void argvShapes() {
        String shell = "{\"tasks\":[{\"label\":\"b\",\"type\":\"shell\",\"command\":\"npm run build && echo done\",\"args\":[\"--x y\"]}]}";
        assertThat(((Launch) resolve(shell, Os.LINUX)).argv())
                .containsExactly("/bin/sh", "-c", "npm run build && echo done '--x y'");
        assertThat(((Launch) resolve(shell, Os.WINDOWS)).argv())
                .containsExactly("cmd.exe", "/d", "/s", "/c", "\"npm run build && echo done \"--x y\"\"");

        String process = "{\"tasks\":[{\"label\":\"p\",\"type\":\"process\",\"command\":\"cargo\",\"args\":[\"build\",\"--release\"]}]}";
        Launch launch = (Launch) resolve(process, Os.LINUX);
        assertThat(launch.argv()).containsExactly("cargo", "build", "--release");
        assertThat(launch.dir()).as("VS Code's default cwd is the workspace folder").isEqualTo(project.toFile());
        assertThat(launch.env()).isEmpty();
    }

    @Test
    @DisplayName("every supported variable is substituted in command, args, cwd and env")
    void variables() throws Exception {
        Files.createDirectories(project.resolve("sub"));
        String json = """
                {"tasks": [{"label": "v", "type": "process", "command": "${workspaceFolder}${/}bin${pathSeparator}run",
                  "args": ["${workspaceFolderBasename}", "${cwd}", "${env:HOME_X}", "${env:UNSET}", "${workspaceRoot}"],
                  "options": {"cwd": "${workspaceFolder}/sub", "env": {"NAME": "${workspaceFolderBasename}-x"}}}]}""";
        TaskDef task = only(json, Os.LINUX);
        Launch launch = (Launch) VsCodeTasks.resolve(task, project.toFile(), Os.LINUX,
                name -> "HOME_X".equals(name) ? "/home/x" : null);
        String root = project.toFile().getAbsolutePath();
        String sep = File.separator;
        assertThat(launch.argv()).containsExactly(root + sep + "bin" + sep + "run",
                project.getFileName().toString(), root, "/home/x", "", root);
        assertThat(launch.dir().getCanonicalFile()).isEqualTo(project.resolve("sub").toFile().getCanonicalFile());
        assertThat(launch.env()).containsExactlyEntriesOf(Map.of("NAME", project.getFileName() + "-x"));
        assertThat(VsCodeTasks.substitute("$ {not} ${", project.toFile(), NO_ENV))
                .as("text that is not a variable passes through").isEqualTo("$ {not} ${");
    }

    @Test
    @DisplayName("a variable only VS Code can fill makes the task refuse, naming the variable as written")
    void unknownVariableRefuses() {
        for (String variable : new String[] {"${input:target}", "${file}", "${config:editor.tabSize}",
                "${command:pickProcess}", "${relativeFile}", "${env:}"}) {
            String json = "{\"tasks\":[{\"label\":\"x\",\"type\":\"shell\",\"command\":\"echo\",\"args\":[\"pre-"
                    + variable + "-post\"]}]}";
            assertThat(resolve(json, Os.LINUX)).as(variable)
                    .isEqualTo(new Refused(Reason.VARIABLE, variable));
        }
        // the same refusal wherever the variable sits
        assertThat(resolve("{\"tasks\":[{\"label\":\"x\",\"command\":\"${input:cmd}\"}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.VARIABLE, "${input:cmd}"));
        assertThat(resolve("{\"tasks\":[{\"label\":\"x\",\"command\":\"make\",\"options\":{\"cwd\":\"${fileDirname}\"}}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.VARIABLE, "${fileDirname}"));
        assertThat(resolve("{\"tasks\":[{\"label\":\"x\",\"command\":\"make\",\"options\":{\"env\":{\"A\":\"${input:a}\"}}}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.VARIABLE, "${input:a}"));
        assertThat(resolve("{\"tasks\":[{\"label\":\"x\",\"command\":\"echo ${unterminated\"}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.VARIABLE, "${unterminated"));
        assertThat(VsCodeTasks.unsupportedVariable("${workspaceFolder} ${env:PATH} ${/}")).isNull();
    }

    @Test
    @DisplayName("dependsOn refuses naming the dependency — string, array, or the npm-object form")
    void dependsOnRefuses() {
        assertThat(resolve("{\"tasks\":[{\"label\":\"deploy\",\"command\":\"make\",\"dependsOn\":\"build\"}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.DEPENDS_ON, "build"));
        assertThat(resolve("{\"tasks\":[{\"label\":\"all\",\"command\":\"make\",\"dependsOn\":[\"lint\",{\"type\":\"npm\",\"script\":\"test\"}]}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.DEPENDS_ON, "lint, test"));
    }

    @Test
    @DisplayName("options.cwd must stay inside the project: .., an absolute path elsewhere and a symlink out all refuse")
    void cwdContainment() throws Exception {
        Path outside = Files.createTempDirectory("vscode-outside");
        try {
            Files.createDirectories(project.resolve("web"));
            String template = "{\"tasks\":[{\"label\":\"x\",\"command\":\"make\",\"options\":{\"cwd\":\"%s\"}}]}";

            assertThat(((Launch) resolve(template.formatted("web"), Os.LINUX)).dir().getCanonicalFile())
                    .isEqualTo(project.resolve("web").toFile().getCanonicalFile());
            assertThat(((Launch) resolve(template.formatted("."), Os.LINUX)).dir()).isEqualTo(project.toFile());
            assertThat(((Launch) resolve(template.formatted("${workspaceFolder}"), Os.LINUX)).dir())
                    .isEqualTo(project.toFile());

            assertThat(resolve(template.formatted("../"), Os.LINUX)).isEqualTo(new Refused(Reason.CWD_OUTSIDE, "../"));
            assertThat(resolve(template.formatted("web/../../x"), Os.LINUX))
                    .isEqualTo(new Refused(Reason.CWD_OUTSIDE, "web/../../x"));
            String abs = outside.toString().replace("\\", "\\\\");
            assertThat(resolve(template.formatted(abs), Os.LINUX))
                    .isEqualTo(new Refused(Reason.CWD_OUTSIDE, outside.toString()));
            assertThat(resolve(template.formatted("missing"), Os.LINUX))
                    .isEqualTo(new Refused(Reason.CWD_MISSING, "missing"));
            try {
                Files.createSymbolicLink(project.resolve("escape"), outside);
                assertThat(resolve(template.formatted("escape"), Os.LINUX))
                        .as("a link out of the project is outside, whatever its name says")
                        .isEqualTo(new Refused(Reason.CWD_OUTSIDE, "escape"));
            } catch (UnsupportedOperationException | java.io.IOException noLinks) {
                // Windows without the symlink privilege; the lexical cases above still ran
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("npm-type tasks hand off to the npm lane, in the optional path folder; extension types refuse by name")
    void npmAndOtherTypes() throws Exception {
        Files.createDirectories(project.resolve("packages/web"));
        assertThat(resolve("{\"tasks\":[{\"label\":\"npm: build\",\"type\":\"npm\",\"script\":\"build\"}]}", Os.LINUX))
                .isEqualTo(new NpmLaunch(project.toFile(), "build"));
        NpmLaunch sub = (NpmLaunch) resolve(
                "{\"tasks\":[{\"label\":\"w\",\"type\":\"npm\",\"script\":\"dev\",\"path\":\"packages/web\"}]}", Os.LINUX);
        assertThat(sub.dir().getCanonicalFile()).isEqualTo(project.resolve("packages/web").toFile().getCanonicalFile());
        assertThat(sub.script()).isEqualTo("dev");
        assertThat(resolve("{\"tasks\":[{\"label\":\"w\",\"type\":\"npm\",\"script\":\"dev\",\"path\":\"../x\"}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.CWD_OUTSIDE, "../x"));
        assertThat(resolve("{\"tasks\":[{\"label\":\"w\",\"type\":\"npm\"}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.NO_COMMAND, ""));

        assertThat(resolve("{\"tasks\":[{\"label\":\"g\",\"type\":\"gulp\",\"task\":\"default\"}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.TYPE, "gulp"));
        assertThat(resolve("{\"tasks\":[{\"label\":\"t\",\"type\":\"TypeScript\"}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.TYPE, "typescript"));
        assertThat(resolve("{\"tasks\":[{\"label\":\"none\",\"type\":\"shell\"}]}", Os.LINUX))
                .isEqualTo(new Refused(Reason.NO_COMMAND, ""));
    }

    @Test
    @DisplayName("group and isBackground are read; display shows the command as the file wrote it")
    void groupAndDisplay() {
        TaskDef task = only("""
                {"tasks":[{"label":"watch","type":"shell","command":"tsc","args":["-w","${workspaceFolder}"],
                  "group":{"kind":"build","isDefault":true},"isBackground":true}]}""", Os.LINUX);
        assertThat(task.group()).isEqualTo("build");
        assertThat(task.background()).isTrue();
        assertThat(VsCodeTasks.display(task)).isEqualTo("tsc -w ${workspaceFolder}");
        assertThat(VsCodeTasks.display(only("{\"tasks\":[{\"label\":\"n\",\"type\":\"npm\",\"script\":\"lint\",\"group\":\"test\"}]}", Os.LINUX)))
                .isEqualTo("npm: lint");
    }
}
