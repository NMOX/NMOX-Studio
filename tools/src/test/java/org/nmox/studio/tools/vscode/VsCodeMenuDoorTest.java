package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A press of Run ▸ Run Task… or Debug ▸ Start Debugging…: what is read
 * when, what is shown, and what is started.
 */
class VsCodeMenuDoorTest {

    private final List<String> said = Collections.synchronizedList(new ArrayList<>());
    private final List<String> started = Collections.synchronizedList(new ArrayList<>());
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<List<String>> shown = new AtomicReference<>();
    private final File project = new File("storefront").getAbsoluteFile();
    private volatile File aimed = project;
    private volatile List<String> offered = List.of("build", "test", "deploy");
    private volatile OptionalInt choice = OptionalInt.of(1);
    private final EditorContext editor = new EditorContext(Path.of("src", "server.js").toAbsolutePath(), 3, 1, null);

    private VsCodeMenuDoor<String> door;

    @BeforeEach
    void door() {
        door = new VsCodeMenuDoor<>(new VsCodeMenuDoor.Source<>() {
            @Override
            public List<String> read(File dir) {
                events.add("read on " + (java.awt.EventQueue.isDispatchThread() ? "EDT" : "lane"));
                return offered;
            }

            @Override
            public String row(String item) {
                return item + " row";
            }

            @Override
            public void start(File dir, String item, EditorContext ctx) {
                started.add(item + " in " + dir.getName() + " with " + ctx.file().getFileName());
            }
        }, () -> new VsCodeMenuDoor.Words("Run Task", "Run", "no project", "nothing to run"), said::add);
        door.aimed = () -> aimed;
        door.editorProbe = () -> {
            events.add("editor read");
            return editor;
        };
        door.onEventThread = Runnable::run;
        door.picker = (title, start, rows) -> {
            events.add("picker");
            shown.set(rows);
            return choice;
        };
    }

    @Test
    @DisplayName("the chosen row is started in the aimed project with the editor read BEFORE the list was shown")
    void theChosenRowStarts() {
        door.press().waitFinished();
        assertThat(shown.get()).containsExactly("build row", "test row", "deploy row");
        assertThat(started).containsExactly("test in storefront with server.js");
        assertThat(said).isEmpty();
        assertThat(events).as("the editor first, the file on the lane, then the list")
                .containsExactly("editor read", "read on lane", "picker");
    }

    @Test
    @DisplayName("Cancel starts nothing and says nothing")
    void cancelStartsNothing() {
        choice = OptionalInt.empty();
        door.press().waitFinished();
        assertThat(started).isEmpty();
        assertThat(said).isEmpty();
    }

    @Test
    @DisplayName("no project, and a project whose file offers nothing, each say so and show no list")
    void refusalsSpeak() {
        offered = List.of();
        door.press().waitFinished();
        aimed = null;
        door.press().waitFinished();
        assertThat(said).containsExactly("nothing to run", "no project");
        assertThat(shown.get()).as("no list was shown").isNull();
        assertThat(started).isEmpty();
    }

    @Test
    @DisplayName("a list read for one project is not shown once another is aimed")
    void aResultBelongsToItsProject() {
        List<Runnable> queued = new ArrayList<>();
        door.onEventThread = queued::add;
        door.press().waitFinished();
        aimed = new File("another").getAbsoluteFile();
        queued.forEach(Runnable::run);
        assertThat(shown.get()).isNull();
        assertThat(started).isEmpty();
    }

    @Test
    @DisplayName("the two menu rows are registered where the Run and Debug menus have room, and start through the providers")
    void registered() throws Exception {
        String run = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/vscode/RunTaskAction.java"),
                StandardCharsets.UTF_8);
        String debug = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/vscode/StartDebuggingAction.java"),
                StandardCharsets.UTF_8);
        assertThat(run).contains("@ActionReference(path = \"Menu/BuildProject\", position = 30)")
                .contains("VsCodeTaskSearchProvider.run(project, task, editor)");
        assertThat(debug).contains("@ActionReference(path = \"Menu/RunProject\", position = 280)")
                .contains("VsCodeLaunchSearchProvider.run(project, config, editor)");
        // no spawn and no trust question of their own: both are the providers'
        for (String src : List.of(run, debug, Files.readString(
                Path.of("src/main/java/org/nmox/studio/tools/vscode/VsCodeMenuDoor.java"), StandardCharsets.UTF_8))) {
            assertThat(src).doesNotContain("CommandExecutor").doesNotContain("ProcessBuilder")
                    .doesNotContain("requestTrust");
        }
    }

    @Test
    @DisplayName("a row is the label and what it runs, on one line; a group with no command is its label alone")
    void rows() throws Exception {
        Path dir = Files.createTempDirectory("nmox-menu-door");
        try {
            Files.createDirectories(dir.resolve(".vscode"));
            Files.writeString(dir.resolve(".vscode/tasks.json"), """
                    {"tasks":[{"label":"build","type":"shell","command":"make all"},
                              {"label":"all","dependsOn":["build"]}]}
                    """);
            Files.writeString(dir.resolve(".vscode/launch.json"), """
                    {"configurations":[{"type":"node","request":"launch","name":"Launch Program",
                                        "program":"${workspaceFolder}/server.js"}]}
                    """);
            VsCodeTasks.clearCache();
            VsCodeLaunch.clearCache();
            assertThat(VsCodeTasks.read(dir.toFile()).stream().map(RunTaskAction::row))
                    .containsExactly("build — make all", "all");
            assertThat(VsCodeLaunch.read(dir.toFile()).stream().map(StartDebuggingAction::row))
                    .containsExactly("Launch Program — ${workspaceFolder}/server.js");
        } finally {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
