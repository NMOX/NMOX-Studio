package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.nmox.studio.core.spi.DebugLauncher;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Config;
import org.nmox.studio.tools.vscode.VsCodeTaskSearchProvider.RunEnd;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VS Code launch configurations in Quick Search (v3.1.0): "launch" in ⌘I
 * lists {@code Debug: Launch Program — ${workspaceFolder}/server.js}; Enter
 * asks Workspace Trust on the project and hands exactly the resolved file
 * and working folder (or page and web root) to the debugger facade — or
 * refuses out loud and hands over nothing.
 */
class VsCodeLaunchSearchProviderTest {

    private static final String LAUNCH = """
            {
              "version": "0.2.0",
              "configurations": [
                { "type": "node", "request": "launch", "name": "Launch Program",
                  "skipFiles": ["<node_internals>/**"], "program": "${workspaceFolder}/server.js" },
                { "type": "node", "request": "launch", "name": "Launch with args",
                  "program": "server.js", "args": ["--port", "3000"], "env": { "ROOT": "${workspaceFolder}" } },
                { "type": "node", "request": "launch", "name": "Launch with a runtime",
                  "program": "server.js", "runtimeExecutable": "nodemon", "runtimeArgs": ["--inspect"] },
                { "type": "node", "request": "launch", "name": "npm run dev",
                  "runtimeExecutable": "npm", "runtimeArgs": ["run", "dev"], "envFile": "${workspaceFolder}/.env",
                  "env": { "MODE": "explicit" } },
                { "type": "node", "request": "launch", "name": "Current File", "program": "${file}" },
                { "type": "node", "request": "launch", "name": "Launch after a build",
                  "program": "server.js", "preLaunchTask": "build" },
                { "type": "chrome", "request": "launch", "name": "Launch Chrome",
                  "url": "http://localhost:8080", "webRoot": "${workspaceFolder}/web" },
                { "type": "node", "request": "attach", "name": "Attach", "port": 9229,
                  "cwd": "${workspaceFolder}/web" },
                { "type": "node", "request": "attach", "name": "Attach to the build box",
                  "port": 9229, "address": "10.0.0.5" },
              ],
              "compounds": [ { "name": "Full stack", "configurations": ["Launch Program", "Launch Chrome"] } ],
            }
            """;

    @TempDir
    Path project;

    private final Predicate<File> realTrust = VsCodeLaunchSearchProvider.trustCheck;
    private final Supplier<DebugLauncher> realLauncher = VsCodeLaunchSearchProvider.launcher;
    private final Consumer<String> realStatus = VsCodeLaunchSearchProvider.statusSink;
    private final Supplier<EditorContext> realEditor = VsCodeLaunchSearchProvider.editorProbe;
    private final VsCodeLaunchSearchProvider.TaskRunner realTasks = VsCodeLaunchSearchProvider.taskRunner;
    /** What the fake task runner was asked to run, and how it answers. */
    private final List<String> tasksRun = Collections.synchronizedList(new ArrayList<>());
    private volatile RunEnd taskEnds = RunEnd.DONE;
    private volatile Runnable duringTask = () -> { };

    private final List<String> said = Collections.synchronizedList(new ArrayList<>());
    private final List<String> asked = Collections.synchronizedList(new ArrayList<>());
    private final List<Object[]> handed = Collections.synchronizedList(new ArrayList<>());

    /** A debugger that records what it was handed and starts nothing. */
    private final class FakeLauncher implements DebugLauncher {
        @Override
        public boolean supports(File file) {
            return true;
        }

        @Override
        public void debug(File file) {
            handed.add(new Object[] {"file-only", file});
        }

        @Override
        public boolean debug(File file, File workingDir) {
            handed.add(new Object[] {"file", file, workingDir});
            return true;
        }

        @Override
        public boolean debug(File file, File workingDir, List<String> args, java.util.Map<String, String> env) {
            if (args.isEmpty() && env.isEmpty()) {
                return debug(file, workingDir);
            }
            handed.add(new Object[] {"file+", file, workingDir, args, env});
            return true;
        }

        @Override
        public boolean debug(Launch launch) {
            if (launch.plain()) {
                // the doors a plain launch always took, through the facade's own default
                return DebugLauncher.super.debug(launch);
            }
            handed.add(new Object[] {"launch", launch});
            return true;
        }

        @Override
        public boolean attachNode(String name, String address, int port, File workingDir, File workspace) {
            handed.add(new Object[] {"attach", name, address, port, workspace, workingDir});
            return true;
        }

        @Override
        public boolean debugPage(String url, File webRoot) {
            handed.add(new Object[] {"page", url, webRoot});
            return true;
        }
    }

    @BeforeEach
    void seams() throws Exception {
        VsCodeLaunch.clearCache();
        Files.createDirectories(project.resolve(".vscode"));
        Files.createDirectories(project.resolve("web"));
        Files.writeString(project.resolve("server.js"), "require('http');\n");
        Files.writeString(project.resolve(".vscode/launch.json"), LAUNCH);
        VsCodeLaunchSearchProvider.statusSink = said::add;
        VsCodeLaunchSearchProvider.trustCheck = dir -> {
            asked.add(dir.getPath());
            return true;
        };
        FakeLauncher fake = new FakeLauncher();
        VsCodeLaunchSearchProvider.launcher = () -> fake;
        VsCodeLaunchSearchProvider.editorProbe = () -> EditorContext.NONE;
        VsCodeLaunchSearchProvider.taskRunner = (dir, task, editor, then) -> {
            tasksRun.add(task.label());
            duringTask.run();
            then.accept(taskEnds);
        };
    }

    @AfterEach
    void restore() {
        VsCodeLaunchSearchProvider.trustCheck = realTrust;
        VsCodeLaunchSearchProvider.launcher = realLauncher;
        VsCodeLaunchSearchProvider.statusSink = realStatus;
        VsCodeLaunchSearchProvider.editorProbe = realEditor;
        VsCodeLaunchSearchProvider.taskRunner = realTasks;
    }

    private Config config(String name) {
        return VsCodeLaunch.read(project.toFile()).stream()
                .filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private void enter(String name) {
        VsCodeLaunchSearchProvider.run(project.toFile(), config(name)).waitFinished();
    }

    private static List<String> names(List<VsCodeLaunchSearchProvider.Item> items) {
        return items.stream().map(i -> i.config().name()).toList();
    }

    @Test
    @DisplayName("\"launch\" lists the configurations, labelled with the program or page as the file wrote it")
    void lists() {
        List<VsCodeLaunchSearchProvider.Item> items = VsCodeLaunchSearchProvider.itemsFor("launch program", project.toFile());
        assertThat(names(items)).startsWith("Launch Program");
        assertThat(items.get(0).label()).isEqualTo("Debug: Launch Program — ${workspaceFolder}/server.js");
        assertThat(names(VsCodeLaunchSearchProvider.itemsFor("debug", project.toFile())))
                .as("the vocabulary lists every configuration and compound, name order")
                .containsExactly("Attach", "Attach to the build box", "Current File", "Full stack",
                        "Launch Chrome", "Launch Program", "Launch after a build", "Launch with a runtime",
                        "Launch with args", "npm run dev");
        assertThat(VsCodeLaunchSearchProvider.itemsFor("attach", project.toFile()).get(0).label())
                .as("an attach shows where it attaches").isEqualTo("Debug: Attach — localhost:9229");
        assertThat(VsCodeLaunchSearchProvider.itemsFor("npm run dev", project.toFile()).get(0).label())
                .as("a runtime that is the whole command shows the command").isEqualTo("Debug: npm run dev — npm run dev");
        assertThat(names(VsCodeLaunchSearchProvider.itemsFor("chrome", project.toFile()))).containsExactly("Launch Chrome");
        assertThat(VsCodeLaunchSearchProvider.itemsFor("postgres", project.toFile())).isEmpty();
        assertThat(VsCodeLaunchSearchProvider.itemsFor("debug", null)).isEmpty();
        assertThat(VsCodeLaunchSearchProvider.itemsFor("debug", project.resolve("web").toFile()))
                .as("no launch.json: the category stays silent").isEmpty();
    }

    @Test
    @DisplayName("labels are escaped for the HTML renderer and clipped by code points")
    void labels() {
        assertThat(VsCodeLaunchSearchProvider.label("<b>x</b>", "a<i>&b.js"))
                .isEqualTo("Debug: &lt;b&gt;x&lt;/b&gt; — a&lt;i&gt;&amp;b.js");
        assertThat(VsCodeLaunchSearchProvider.label("Full stack", "")).isEqualTo("Debug: Full stack");
        assertThat(VsCodeLaunchSearchProvider.label("l", "x".repeat(200))).endsWith("…")
                .hasSize("Debug: l — ".length() + VsCodeLaunchSearchProvider.MAX_TARGET);
    }

    @Test
    @DisplayName("Enter asks trust on the project, then hands the debugger exactly the resolved program and working folder")
    void enterHandsTheProgram() throws Exception {
        enter("Launch Program");

        assertThat(asked).containsExactly(project.toFile().getPath());
        assertThat(handed).hasSize(1);
        assertThat(handed.get(0)[0]).isEqualTo("file");
        assertThat(((File) handed.get(0)[1]).getCanonicalFile())
                .isEqualTo(project.resolve("server.js").toFile().getCanonicalFile());
        assertThat(((File) handed.get(0)[2]).getCanonicalFile()).isEqualTo(project.toFile().getCanonicalFile());
        assertThat(said).containsExactly("Starting the debugger for \"Launch Program\"…");
    }

    @Test
    @DisplayName("Enter on a Chrome configuration hands the debugger the page and the web root")
    void enterHandsThePage() throws Exception {
        enter("Launch Chrome");
        assertThat(handed).hasSize(1);
        assertThat(handed.get(0)[0]).isEqualTo("page");
        assertThat(handed.get(0)[1]).isEqualTo("http://localhost:8080");
        assertThat(((File) handed.get(0)[2]).getCanonicalFile())
                .isEqualTo(project.resolve("web").toFile().getCanonicalFile());
    }

    @Test
    @DisplayName("Keep Safe hands the debugger nothing and says nothing more")
    void keepSafeHandsNothing() {
        VsCodeLaunchSearchProvider.trustCheck = dir -> {
            asked.add(dir.getPath());
            return false;
        };
        enter("Launch Program");
        assertThat(asked).hasSize(1);
        assertThat(handed).isEmpty();
        assertThat(said).isEmpty();
    }

    @Test
    @DisplayName("args and env reach the debugger, variables substituted (3.1.0)")
    void argsAndEnvReachTheDebugger() throws Exception {
        enter("Launch with args");
        assertThat(handed).hasSize(1);
        Object[] h = handed.get(0);
        assertThat(h[0]).isEqualTo("file+");
        assertThat(h[3]).isEqualTo(List.of("--port", "3000"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> env = (java.util.Map<String, String>) h[4];
        assertThat(new File(env.get("ROOT")).getCanonicalFile()).isEqualTo(project.toFile().getCanonicalFile());
        assertThat(asked).as("trust asked before the hand-off").hasSize(1);
    }

    @Test
    @DisplayName("refusals speak on the status line, hand over nothing and ask nothing")
    void refusalsSpeak() {
        enter("Attach to the build box");
        enter("Current File");
        enter("npm run dev");
        enter("Full stack");
        assertThat(handed).isEmpty();
        assertThat(asked).as("a refusal asks no trust question").isEmpty();
        assertThat(said).containsExactly(
                "Configuration \"Attach to the build box\" attaches to 10.0.0.5; the NMOX Studio debugger attaches "
                        + "only to a process on this machine (localhost).",
                "Configuration \"Current File\" uses ${file}, and no file is open in the editor; "
                        + "open the file to debug and try again.",
                "Configuration \"npm run dev\" points at ${workspaceFolder}/.env, which is not there.",
                "Compound \"Full stack\" starts several sessions at once; the NMOX Studio debugger starts one at a time.");
    }

    /* ------------------------------------------------------- preLaunchTask */

    private void tasks(String json) throws Exception {
        VsCodeTasks.clearCache(); // the cache is keyed on a modification time a test can rewrite inside
        Files.writeString(project.resolve(".vscode/tasks.json"), json);
    }

    @Test
    @DisplayName("a preLaunchTask runs first, after the trust question, and the debugger starts when it ended well")
    void preLaunchTaskRunsFirst() throws Exception {
        tasks("{\"tasks\":[{\"label\":\"build\",\"type\":\"shell\",\"command\":\"make\"}]}");
        duringTask = () -> {
            assertThat(asked).as("trust before the task").hasSize(1);
            assertThat(handed).as("nothing debugged while the task runs").isEmpty();
        };
        enter("Launch after a build");
        assertThat(tasksRun).containsExactly("build");
        assertThat(handed).hasSize(1);
        assertThat(said).containsExactly(
                "Running task \"build\" before debugging \"Launch after a build\"\u2026",
                "Starting the debugger for \"Launch after a build\"\u2026");
    }

    @Test
    @DisplayName("a preLaunchTask that failed, was stopped or was refused starts no debugger, and says which")
    void preLaunchTaskThatDidNotEndWell() throws Exception {
        tasks("{\"tasks\":[{\"label\":\"build\",\"type\":\"shell\",\"command\":\"make\"}]}");
        taskEnds = RunEnd.FAILED;
        enter("Launch after a build");
        taskEnds = RunEnd.STOPPED;
        enter("Launch after a build");
        taskEnds = RunEnd.NOT_STARTED;
        enter("Launch after a build");
        assertThat(tasksRun).hasSize(3);
        assertThat(handed).isEmpty();
        assertThat(said).containsExactly(
                "Running task \"build\" before debugging \"Launch after a build\"\u2026",
                "Configuration \"Launch after a build\" was not started: its preLaunchTask \"build\" did not succeed. "
                        + "The task\u2019s Output tab says why.",
                "Running task \"build\" before debugging \"Launch after a build\"\u2026",
                "Configuration \"Launch after a build\" was not started: its preLaunchTask \"build\" was stopped.",
                "Running task \"build\" before debugging \"Launch after a build\"\u2026");
    }

    @Test
    @DisplayName("a preLaunchTask nobody defined, defined twice, or that never ends is refused by name before any question")
    void preLaunchTaskRefusals() throws Exception {
        enter("Launch after a build"); // no tasks.json at all
        tasks("{\"tasks\":[{\"label\":\"build\",\"type\":\"shell\",\"command\":\"a\"},"
                + "{\"label\":\"build\",\"type\":\"shell\",\"command\":\"b\"}]}");
        enter("Launch after a build");
        tasks("{\"tasks\":[{\"label\":\"build\",\"type\":\"shell\",\"command\":\"tsc -w\",\"isBackground\":true}]}");
        enter("Launch after a build");
        assertThat(tasksRun).isEmpty();
        assertThat(handed).isEmpty();
        assertThat(asked).as("a refusal asks no trust question").isEmpty();
        assertThat(said).containsExactly(
                "Configuration \"Launch after a build\" names the preLaunchTask \"build\", which .vscode/tasks.json "
                        + "does not define. Nothing was started.",
                "Configuration \"Launch after a build\" names the preLaunchTask \"build\", a label more than one task "
                        + "in .vscode/tasks.json carries. Nothing was started.",
                "Configuration \"Launch after a build\" names the background task \"build\" as its preLaunchTask. "
                        + "VS Code waits for such a task to report that it is ready, which NMOX Studio cannot read; "
                        + "nothing was started.");
    }

    @Test
    @DisplayName("a program the preLaunchTask builds is looked for again after the task; still missing is still a refusal")
    void theTaskBuildsTheProgram() throws Exception {
        tasks("{\"tasks\":[{\"label\":\"build\",\"type\":\"shell\",\"command\":\"make\"}]}");
        Files.delete(project.resolve("server.js"));
        duringTask = () -> {
            try {
                Files.writeString(project.resolve("server.js"), "1;\n");
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };
        enter("Launch after a build");
        assertThat(handed).as("the file the task made is debugged").hasSize(1);

        Files.delete(project.resolve("server.js"));
        duringTask = () -> { };
        enter("Launch after a build");
        assertThat(handed).as("a task that built nothing starts nothing").hasSize(1);
        assertThat(said.get(said.size() - 1))
                .isEqualTo("Configuration \"Launch after a build\" points at server.js, which is not there.");

        enter("Launch Program"); // no task to build it: refused at once, no question
        assertThat(asked).hasSize(2);
    }

    @Test
    @DisplayName("a runtime, its arguments and the env file's variables reach the debugger; the explicit env wins; no value is said")
    void runtimeAndEnvFileReachTheDebugger() throws Exception {
        Files.writeString(project.resolve(".env"), "MODE=from-file\nAPI_TOKEN=hunter2\n");
        enter("npm run dev");
        enter("Launch with a runtime");

        assertThat(handed).hasSize(2);
        DebugLauncher.Launch npm = (DebugLauncher.Launch) handed.get(0)[1];
        assertThat(handed.get(0)[0]).isEqualTo("launch");
        assertThat(npm.language()).isEqualTo(DebugLauncher.Language.NODE);
        assertThat(npm.name()).isEqualTo("npm run dev");
        assertThat(npm.program()).as("the runtime is the whole command").isNull();
        assertThat(npm.runtime()).isEqualTo("npm");
        assertThat(npm.runtimeArgs()).containsExactly("run", "dev");
        assertThat(npm.env()).containsEntry("MODE", "explicit").containsEntry("API_TOKEN", "hunter2");
        assertThat(npm.workspace().getCanonicalFile()).isEqualTo(project.toFile().getCanonicalFile());
        assertThat(npm.workingDir().getCanonicalFile()).isEqualTo(project.toFile().getCanonicalFile());

        DebugLauncher.Launch nodemon = (DebugLauncher.Launch) handed.get(1)[1];
        assertThat(nodemon.program().getName()).isEqualTo("server.js");
        assertThat(nodemon.runtime()).isEqualTo("nodemon");
        assertThat(nodemon.runtimeArgs()).containsExactly("--inspect");

        assertThat(asked).as("trust asked before each hand-off: a runtime is a program the project chose").hasSize(2);
        assertThat(said).hasSize(2).allSatisfy(s -> assertThat(s).doesNotContain("hunter2"));
        assertThat(npm.toString()).contains("API_TOKEN").doesNotContain("hunter2");
    }

    @Test
    @DisplayName("an env file line VS Code reads differently is refused naming the file and the line, never the value")
    void envFileRefusalNamesNoValue() throws Exception {
        Files.writeString(project.resolve(".env"), "MODE=x\nAPI_TOKEN=hunter2 # prod\n");
        enter("npm run dev");
        assertThat(handed).isEmpty();
        assertThat(asked).isEmpty();
        assertThat(said).containsExactly("Configuration \"npm run dev\" reads its environment from "
                + "${workspaceFolder}/.env:2, a line NMOX Studio would not read the way VS Code does; "
                + "write it as a plain NAME=value. Nothing was started.");
    }

    @Test
    @DisplayName("${file} is the file the editor shows when Enter is pressed — read on the caller's thread, before the lane")
    void currentFileComesFromTheEditor() throws Exception {
        Path shown = Files.writeString(project.resolve("worker.js"), "1;\n");
        AtomicReference<String> readOn = new AtomicReference<>();
        VsCodeLaunchSearchProvider.editorProbe = () -> {
            readOn.set(Thread.currentThread().getName());
            return new EditorContext(shown, 1, 1, null);
        };
        enter("Current File");

        assertThat(readOn.get()).as("the editor is asked where Enter was pressed, not on the lane")
                .isEqualTo(Thread.currentThread().getName());
        assertThat(handed).hasSize(1);
        assertThat(handed.get(0)[0]).isEqualTo("file");
        assertThat(((File) handed.get(0)[1]).getCanonicalFile()).isEqualTo(shown.toFile().getCanonicalFile());
        assertThat(said).containsExactly("Starting the debugger for \"Current File\"…");
    }

    @Test
    @DisplayName("Enter on a Node attach asks trust, then hands the debugger the address, the port, the project and the cwd")
    void enterAttaches() throws Exception {
        enter("Attach");
        assertThat(asked).containsExactly(project.toFile().getPath());
        assertThat(handed).hasSize(1);
        Object[] h = handed.get(0);
        assertThat(h[0]).isEqualTo("attach");
        assertThat(h[1]).isEqualTo("Attach");
        assertThat(h[2]).isEqualTo("localhost");
        assertThat(h[3]).isEqualTo(9229);
        assertThat(((File) h[4]).getCanonicalFile()).as("trust and the session belong to the project")
                .isEqualTo(project.toFile().getCanonicalFile());
        assertThat(((File) h[5]).getCanonicalFile()).as("and the working folder is the one the configuration wrote")
                .isEqualTo(project.resolve("web").toFile().getCanonicalFile());
        assertThat(said).containsExactly("Attaching the debugger for \"Attach\"…");
    }

    @Test
    @DisplayName("a launcher that was never taught a runtime or an attach starts nothing and says so")
    void anOlderLauncherRefuses() {
        VsCodeLaunchSearchProvider.launcher = () -> new DebugLauncher() {
            @Override
            public boolean supports(File file) {
                return true;
            }

            @Override
            public void debug(File file) {
                throw new AssertionError("a runtime launch must not fall back to the plain door");
            }
        };
        enter("Launch with a runtime");
        enter("Attach");
        assertThat(said).containsExactly(
                "Configuration \"Launch with a runtime\" did not start: the breakpoint debugger is not installed.",
                "Configuration \"Attach\" did not start: the breakpoint debugger is not installed.");
    }

    @Test
    @DisplayName("without the editor's debugger the configuration says so, before any trust question")
    void noDebugger() {
        VsCodeLaunchSearchProvider.launcher = () -> null;
        enter("Launch Program");
        assertThat(asked).isEmpty();
        assertThat(said).containsExactly(
                "Configuration \"Launch Program\" did not start: the breakpoint debugger is not installed.");
    }

    @Test
    @DisplayName("every refusal reason renders a sentence naming the configuration")
    void everyReasonRenders() {
        for (VsCodeLaunch.Reason reason : VsCodeLaunch.Reason.values()) {
            String s = VsCodeLaunchSearchProvider.refusal("cfg", new VsCodeLaunch.Refused(reason, "D"));
            assertThat(s).as(reason.name()).contains("cfg").doesNotContain("{");
        }
    }

    @Test
    @DisplayName("Enter runs off the calling (EDT) thread, on the provider's own lane")
    void enterIsOffTheCaller() {
        AtomicReference<String> thread = new AtomicReference<>();
        VsCodeLaunchSearchProvider.trustCheck = dir -> {
            thread.set(Thread.currentThread().getName());
            return false;
        };
        enter("Launch Program");
        assertThat(thread.get()).isNotEqualTo(Thread.currentThread().getName()).contains("vscode-launch");
    }

    @Test
    @DisplayName("the trust gate is the default, the facade is the debugger, and the gate sits before the hand-off")
    void trustGateIsWired() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/tools/vscode/VsCodeLaunchSearchProvider.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
        assertThat(code).contains("trustCheck = dir -> WorkspaceTrust.requestTrust(dir)")
                .contains("launcher = DebugLauncher::find")
                .doesNotContain("CommandExecutor")
                .doesNotContain("ProcessBuilder");
        int m = code.indexOf("static void execute(");
        String body = code.substring(m, code.indexOf("\n    }\n", m));
        int refuse = body.indexOf("instanceof Refused");
        int gate = body.indexOf("trustCheck.test(project)");
        assertThat(refuse).isPositive();
        assertThat(gate).as("the trust gate is present").isGreaterThan(refuse);
        assertThat(body.indexOf("handOver(")).as("the hand-off comes after the gate").isGreaterThan(gate);
        assertThat(body.indexOf("taskRunner.run(")).as("the preLaunchTask runs after the gate").isGreaterThan(gate);
        assertThat(body).as("execute() itself hands nothing to the debugger")
                .doesNotContain("debugger.debug(").doesNotContain("debugger.attachNode(")
                .doesNotContain("debugger.debugPage(");
        // the three hand-offs live in handOver(), which only execute() calls
        int h = code.indexOf("private static void handOver(");
        String hand = code.substring(h, code.indexOf("\n    }\n", h));
        assertThat(hand.split("debugger\\.(debug|attachNode|debugPage)\\(", -1)).as("three hand-offs").hasSize(4);
        assertThat(code.split("debugger\\.(debug|attachNode|debugPage)\\(", -1))
                .as("and no fourth anywhere else").hasSize(4);
        assertThat(code.split("[^d ]handOver\\(|[(> ]handOver\\(", -1).length - 1)
                .as("handOver is its declaration and the two calls in execute()").isEqualTo(3);
        assertThat(body.split("handOver\\(", -1)).as("both calls are in execute()").hasSize(3);
        assertThat(code).contains("RP.post(() -> execute(project, config, editor))");
    }

    @Test
    @DisplayName("registered under QuickSearch/VsCodeLaunches at 279, with its keys in every shipped language")
    void registered() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        NodeList folders = f.newDocumentBuilder()
                .parse(Path.of("src/main/resources/org/nmox/studio/tools/layer.xml").toFile())
                .getElementsByTagName("folder");
        Element folder = null;
        for (int i = 0; i < folders.getLength(); i++) {
            if (((Element) folders.item(i)).getAttribute("name").equals("VsCodeLaunches")) {
                folder = (Element) folders.item(i);
            }
        }
        assertThat(folder).isNotNull();
        assertThat(((Element) folder.getParentNode()).getAttribute("name")).isEqualTo("QuickSearch");
        NodeList attrs = folder.getElementsByTagName("attr");
        Map<String, String> values = new java.util.HashMap<>();
        for (int i = 0; i < attrs.getLength(); i++) {
            Element a = (Element) attrs.item(i);
            values.put(a.getAttribute("name"), a.hasAttribute("intvalue")
                    ? a.getAttribute("intvalue") : a.getAttribute("stringvalue"));
        }
        assertThat(values).containsEntry("position", "279")
                .containsEntry("SystemFileSystem.localizingBundle", "org.nmox.studio.tools.vscode.Bundle");
        String instance = ((Element) folder.getElementsByTagName("file").item(0)).getAttribute("name");
        Class<?> provider = Class.forName(instance.substring(0, instance.length() - ".instance".length())
                .replace('-', '.'));
        assertThat(SearchProvider.class.isAssignableFrom(provider)).isTrue();

        java.util.Properties base = bundle("");
        assertThat(base.getProperty("QuickSearch/VsCodeLaunches")).isEqualTo("VS Code Launch Configurations");
        for (String key : base.stringPropertyNames()) {
            assertThat(base.getProperty(key)).as("English " + key + ": no ASCII apostrophe in a MessageFormat value")
                    .doesNotContain("'");
        }
        for (String lang : new String[] {"_es", "_fr", "_de", "_ru", "_hi", "_uk", "_pl", "_pt",
                "_id", "_tl", "_vi", "_zh", "_he", "_ar"}) {
            java.util.Properties p = bundle(lang);
            assertThat(p.stringPropertyNames()).as("Bundle" + lang).isEqualTo(base.stringPropertyNames());
            assertThat(p.getProperty("QuickSearch/VsCodeLaunches")).contains("VS Code");
        }
    }

    private static java.util.Properties bundle(String lang) throws Exception {
        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newBufferedReader(Path.of(
                "src/main/resources/org/nmox/studio/tools/vscode/Bundle" + lang + ".properties"),
                StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }
}
