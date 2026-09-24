package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.rack.service.ServingRegistry;
import org.nmox.studio.tools.vscode.VsCodeTasks.Launch;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VS Code tasks in Quick Search (v3.1.0): "build" in ⌘I lists
 * {@code Run task: build — make all}; Enter asks Workspace Trust and hands
 * exactly the resolved argv, directory and environment to the spawn — or
 * refuses out loud and spawns nothing.
 */
class VsCodeTaskSearchProviderTest {

    private static final String TASKS = """
            {
              // a real-world shaped file: comments, trailing commas, an input
              "version": "2.0.0",
              "tasks": [
                { "label": "build", "type": "shell", "command": "make", "args": ["all"],
                  "group": { "kind": "build", "isDefault": true } },
                { "label": "test", "type": "process", "command": "cargo", "args": ["test", "--quiet"],
                  "options": { "cwd": "crates", "env": { "RUST_LOG": "${workspaceFolderBasename}" } },
                  "group": "test" },
                { "label": "deploy", "type": "shell", "command": "./deploy.sh", "args": ["${input:env}"] },
                { "label": "release", "type": "shell", "command": "make release", "dependsOn": ["build", "test"] },
                { "label": "npm: lint", "type": "npm", "script": "lint" },
              ],
              "inputs": [ { "id": "env", "type": "pickString", "options": ["dev", "prod"] } ],
            }
            """;

    @TempDir
    Path project;

    private final Predicate<File> realTrust = VsCodeTaskSearchProvider.trustCheck;
    private final VsCodeTaskSearchProvider.Spawner realSpawner = VsCodeTaskSearchProvider.spawner;
    private final BiConsumer<File, String> realNpm = VsCodeTaskSearchProvider.npmRunner;
    private final Consumer<String> realStatus = VsCodeTaskSearchProvider.statusSink;
    private final VsCodeTasks.Host realHost = VsCodeTaskSearchProvider.host;

    private final List<String> said = Collections.synchronizedList(new ArrayList<>());
    private final List<Object[]> spawned = Collections.synchronizedList(new ArrayList<>());
    private final List<String> asked = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void seams() throws Exception {
        VsCodeTasks.clearCache();
        Files.createDirectories(project.resolve(".vscode"));
        Files.createDirectories(project.resolve("crates"));
        Files.writeString(project.resolve(".vscode/tasks.json"), TASKS);
        VsCodeTaskSearchProvider.statusSink = said::add;
        VsCodeTaskSearchProvider.spawner = (label, launch, dir) -> {
            spawned.add(new Object[] {label, launch, dir});
            return CompletableFuture.completedFuture(0);
        };
        VsCodeTaskSearchProvider.trustCheck = dir -> {
            asked.add(dir.getPath());
            return true;
        };
    }

    @AfterEach
    void restore() {
        VsCodeTaskSearchProvider.trustCheck = realTrust;
        VsCodeTaskSearchProvider.spawner = realSpawner;
        VsCodeTaskSearchProvider.npmRunner = realNpm;
        VsCodeTaskSearchProvider.statusSink = realStatus;
        VsCodeTaskSearchProvider.host = realHost;
        LiveRuns.stopAll();
    }

    private static List<String> labels(List<VsCodeTaskSearchProvider.Item> items) {
        return items.stream().map(i -> i.task().label()).toList();
    }

    private TaskDef task(String label) {
        return VsCodeTasks.read(project.toFile()).stream()
                .filter(t -> t.label().equals(label)).findFirst().orElseThrow();
    }

    private void enter(String label) {
        VsCodeTaskSearchProvider.run(project.toFile(), task(label)).waitFinished();
    }

    @Test
    @DisplayName("\"build\" lists the build task first, labelled with its command as the file wrote it")
    void buildFindsTheBuildTask() {
        List<VsCodeTaskSearchProvider.Item> items = VsCodeTaskSearchProvider.itemsFor("build", project.toFile());
        assertThat(labels(items)).as("the label hit outranks the release task that depends on build")
                .startsWith("build");
        assertThat(items.get(0).label()).isEqualTo("Run task: build — make all");
        assertThat(labels(VsCodeTaskSearchProvider.itemsFor("cargo", project.toFile()))).containsExactly("test");
        assertThat(labels(VsCodeTaskSearchProvider.itemsFor("vscode", project.toFile())))
                .as("the vocabulary lists every task, label order")
                .containsExactly("build", "deploy", "npm: lint", "release", "test");
        assertThat(VsCodeTaskSearchProvider.itemsFor("postgres", project.toFile())).isEmpty();
        assertThat(VsCodeTaskSearchProvider.itemsFor("build", null)).isEmpty();
        assertThat(VsCodeTaskSearchProvider.itemsFor(" ", project.toFile())).isEmpty();
        assertThat(VsCodeTaskSearchProvider.itemsFor("build", project.resolve("crates").toFile()))
                .as("no tasks.json: the category stays silent").isEmpty();
    }

    @Test
    @DisplayName("labels are escaped for the HTML renderer and clipped by code points")
    void labels() {
        assertThat(VsCodeTaskSearchProvider.label("<b>x</b>", "echo <i> && ls"))
                .isEqualTo("Run task: &lt;b&gt;x&lt;/b&gt; — echo &lt;i&gt; &amp;&amp; ls");
        assertThat(VsCodeTaskSearchProvider.label("bare", "")).isEqualTo("Run task: bare");
        String longCommand = "x".repeat(200);
        assertThat(VsCodeTaskSearchProvider.label("l", longCommand)).endsWith("…")
                .hasSize("Run task: l — ".length() + VsCodeTaskSearchProvider.MAX_COMMAND);
    }

    @Test
    @DisplayName("Enter asks trust on the project, then hands the spawn exactly the resolved argv, dir and env")
    void enterSpawnsTheResolvedTask() throws Exception {
        enter("test");

        assertThat(asked).containsExactly(project.toFile().getPath());
        assertThat(spawned).hasSize(1);
        assertThat(spawned.get(0)[0]).isEqualTo("test");
        Launch launch = (Launch) spawned.get(0)[1];
        assertThat(launch.argv()).containsExactly("cargo", "test", "--quiet");
        assertThat(launch.dir().getCanonicalFile()).isEqualTo(project.resolve("crates").toFile().getCanonicalFile());
        assertThat(launch.env()).containsExactlyEntriesOf(Map.of("RUST_LOG", project.getFileName().toString()));
        assertThat(spawned.get(0)[2]).isEqualTo(project.toFile());
        assertThat(said).containsExactly("Running task \"test\"…");
    }

    @Test
    @DisplayName("Keep Safe spawns nothing")
    void keepSafeSpawnsNothing() {
        VsCodeTaskSearchProvider.trustCheck = dir -> {
            asked.add(dir.getPath());
            return false;
        };
        enter("build");
        assertThat(asked).as("the question was asked").hasSize(1);
        assertThat(spawned).as("and the answer was honoured").isEmpty();
        assertThat(said).as("nothing claims to be running").isEmpty();
    }

    @Test
    @DisplayName("a refused task says why on the status line, spawns nothing and asks nothing")
    void refusalsSpeak() throws Exception {
        enter("deploy");
        enter("release");
        Files.writeString(project.resolve(".vscode/tasks.json"),
                "{\"tasks\":[{\"label\":\"up\",\"command\":\"make\",\"options\":{\"cwd\":\"..\"}},"
                + "{\"label\":\"gone\",\"command\":\"make\",\"options\":{\"cwd\":\"missing\"}},"
                + "{\"label\":\"g\",\"type\":\"gulp\"},{\"label\":\"empty\",\"type\":\"shell\"}]}");
        VsCodeTasks.clearCache();
        enter("up");
        enter("gone");
        enter("g");
        enter("empty");

        assertThat(said).containsExactly(
                "This task asks VS Code for ${input:env}; NMOX Studio cannot supply it.",
                "Task \"release\" runs after build, test in VS Code; run that first.",
                "Task \"up\" runs in .., outside the project; NMOX Studio will not run it there.",
                "Task \"gone\" runs in missing, which does not exist.",
                "Task \"g\" is a gulp task, which a VS Code extension provides; NMOX Studio cannot run it.",
                "Task \"empty\" names no command to run.");
        assertThat(spawned).isEmpty();
        assertThat(asked).as("a refusal never raises the trust prompt").isEmpty();
    }

    @Test
    @DisplayName("an npm-type task goes to the npm lane (which asks trust itself), never to this spawn")
    void npmTaskGoesToTheNpmLane() {
        AtomicReference<File> dir = new AtomicReference<>();
        AtomicReference<String> script = new AtomicReference<>();
        VsCodeTaskSearchProvider.npmRunner = (d, s) -> {
            dir.set(d);
            script.set(s);
        };
        enter("npm: lint");
        assertThat(dir.get()).isEqualTo(project.toFile());
        assertThat(script.get()).isEqualTo("lint");
        assertThat(spawned).isEmpty();
        assertThat(asked).as("the question the npm lane will ask, asked first on the same folder, so its own ask is silent")
                .containsExactly(project.toFile().getPath());
        assertThat(said).containsExactly("Running task \"npm: lint\"…");
    }

    @Test
    @DisplayName("an npm-type task under Keep Safe says nothing is running and hands nothing to the lane")
    void npmTaskKeepSafeSaysNothing() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        VsCodeTaskSearchProvider.statusSink = s -> events.add("said: " + s);
        VsCodeTaskSearchProvider.npmRunner = (d, s) -> events.add("ran: " + s);
        VsCodeTaskSearchProvider.trustCheck = dir -> {
            events.add("asked");
            return false;
        };
        enter("npm: lint");
        assertThat(events).as("the review's finding (v3.1.0): \"Running …\" stood over a task that never ran")
                .containsExactly("asked");

        events.clear();
        VsCodeTaskSearchProvider.trustCheck = dir -> {
            events.add("asked");
            return true;
        };
        enter("npm: lint");
        assertThat(events).as("a yes: asked, then said, then handed over — in that order")
                .containsExactly("asked", "said: Running task \"npm: lint\"…", "ran: lint");
    }

    @Test
    @DisplayName("a shell task's argv is resolved against the provider's host seam: the user's shell, not /bin/sh")
    void shellTaskUsesTheHostShell() throws Exception {
        VsCodeTaskSearchProvider.host = new VsCodeTasks.Host(VsCodeTasks.Os.LINUX,
                name -> "SHELL".equals(name) ? "/usr/bin/zsh" : null,
                f -> f.getPath().equals("/usr/bin/zsh"), name -> null);
        enter("build");
        assertThat(spawned).hasSize(1);
        assertThat(((Launch) spawned.get(0)[1]).argv()).containsExactly("/usr/bin/zsh", "-c", "make all");
    }

    @Test
    @DisplayName("Enter runs off the calling (EDT) thread, on the provider's own lane")
    void enterIsOffTheCaller() {
        AtomicReference<String> thread = new AtomicReference<>();
        VsCodeTaskSearchProvider.trustCheck = dir -> {
            thread.set(Thread.currentThread().getName());
            return false;
        };
        enter("build");
        assertThat(thread.get()).isNotEqualTo(Thread.currentThread().getName()).contains("vscode-task");
    }

    @Test
    @DisplayName("the real spawn: a task that prints a local address announces it, joins LiveRuns, and its exit withdraws both")
    void realSpawnJoinsLiveRunsAndServings() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "the fixture is a POSIX shell (ledger 38 keeps the tree-kill exit POSIX-only)");
        Files.writeString(project.resolve("keepalive"), "");
        Launch launch = new Launch(List.of("/bin/sh", "-c",
                "echo \"  Local:   http://localhost:45699/\"; while [ -e keepalive ]; do :; done"),
                project.toFile(), Map.of("FROM_TASK", "1"));
        CompletableFuture<Integer> exit = VsCodeTaskSearchProvider.launch("serve", launch, project.toFile());

        assertThat(poll(() -> ServingRegistry.getDefault().snapshot().stream()
                .anyMatch(s -> s.url().equals("http://localhost:45699/")), 5_000))
                .as("the printed address is a serving").isTrue();
        assertThat(LiveRuns.live()).as("the toolbar ■ can see the run")
                .anyMatch(r -> r.id().startsWith("vscode-task:")
                        && r.label().equals("serve — " + project.getFileName()));

        LiveRuns.stopAll();
        Files.deleteIfExists(project.resolve("keepalive"));
        exit.get(10, TimeUnit.SECONDS);
        assertThat(poll(() -> ServingRegistry.getDefault().snapshot().stream()
                .noneMatch(s -> s.url().equals("http://localhost:45699/")), 5_000))
                .as("the serving died with the process").isTrue();
        assertThat(LiveRuns.live()).noneMatch(r -> r.id().startsWith("vscode-task:"));
    }

    @Test
    @DisplayName("a task whose program is not on PATH says so on the status line")
    void launchFailureSpeaks() throws Exception {
        Launch launch = new Launch(List.of("nmox-no-such-tool-" + System.nanoTime()), project.toFile(), Map.of());
        int code = VsCodeTaskSearchProvider.launch("ghost", launch, project.toFile()).get(10, TimeUnit.SECONDS);
        assertThat(code).isEqualTo(-1);
        assertThat(said).containsExactly("Task \"ghost\" did not start — the Output window says why.");
        assertThat(LiveRuns.live()).noneMatch(r -> r.id().startsWith("vscode-task:"));
    }

    @Test
    @DisplayName("the trust gate is the default, and it sits before the spawn in execute()")
    void trustGateIsWired() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/tools/vscode/VsCodeTaskSearchProvider.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
        assertThat(code).contains("trustCheck = dir -> WorkspaceTrust.requestTrust(dir)")
                .contains("spawner = VsCodeTaskSearchProvider::launch");
        int m = code.indexOf("static void execute(");
        String body = code.substring(m, code.indexOf("\n    }\n", m));
        int refuse = body.indexOf("instanceof Refused");
        int gate = body.indexOf("trustCheck.test(project)");
        int spawn = body.indexOf("spawner.spawn(");
        assertThat(refuse).isPositive();
        assertThat(gate).as("the trust gate is present").isGreaterThan(refuse);
        assertThat(spawn).as("the spawn comes after the gate").isGreaterThan(gate);
        assertThat(code).as("the lane posts execute(), so the EDT never waits on a dialog or a fork")
                .contains("RP.post(() -> execute(project, task))");
    }

    @Test
    @DisplayName("registered under QuickSearch/VsCodeTasks at 278, with its name in every shipped language")
    void registered() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        NodeList folders = f.newDocumentBuilder()
                .parse(Path.of("src/main/resources/org/nmox/studio/tools/layer.xml").toFile())
                .getElementsByTagName("folder");
        Element folder = null;
        for (int i = 0; i < folders.getLength(); i++) {
            if (((Element) folders.item(i)).getAttribute("name").equals("VsCodeTasks")) {
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
        assertThat(values).containsEntry("position", "278")
                .containsEntry("SystemFileSystem.localizingBundle", "org.nmox.studio.tools.vscode.Bundle");
        String instance = ((Element) folder.getElementsByTagName("file").item(0)).getAttribute("name");
        Class<?> provider = Class.forName(instance.substring(0, instance.length() - ".instance".length())
                .replace('-', '.'));
        assertThat(SearchProvider.class.isAssignableFrom(provider)).isTrue();
        assertThat(provider.getConstructor().newInstance()).isNotNull();

        java.util.Properties base = bundle("");
        for (String lang : new String[] {"_es", "_fr", "_de", "_ru", "_hi", "_uk", "_pl", "_pt",
                "_id", "_tl", "_vi", "_zh", "_he", "_ar"}) {
            java.util.Properties p = bundle(lang);
            assertThat(p.stringPropertyNames()).as("Bundle" + lang).isEqualTo(base.stringPropertyNames());
            assertThat(p.getProperty("QuickSearch/VsCodeTasks")).as("the product's name is not translated")
                    .contains("VS Code");
            for (String key : p.stringPropertyNames()) {
                assertThat(p.getProperty(key)).as("Bundle" + lang + " " + key + ": no ASCII apostrophe in a MessageFormat value")
                        .doesNotContain("'");
            }
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

    private static boolean poll(java.util.function.BooleanSupplier ok, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (ok.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return ok.getAsBoolean();
    }
}
