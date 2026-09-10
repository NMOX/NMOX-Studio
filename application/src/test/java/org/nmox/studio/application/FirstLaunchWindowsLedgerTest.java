package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a first launch shows is a decision, and every window states it
 * (v2.118.0, David's call after the coherence pass).
 *
 * <p>Before this, the first-launch set was whatever each window had chosen
 * on the day it was written. It had reached thirteen: ten editor tabs and
 * three docked panes, opened before the product knew anything about the
 * project. Walking an ordinary Node/Express project, four of the ten were
 * for technologies that project cannot use — DB Studio with no database,
 * Contract Studio announcing "0 contract artifacts" for a bakery API, the
 * Infra Designer, the Docker Panel with no Dockerfile — a fifth was a chat
 * client, a sixth an empty kanban board whose first touch writes a file
 * into someone else's repository, and the file the user came to read
 * arrived eleventh in the strip.
 *
 * <p>Seven closed. Discovery keeps the three surfaces that cost nothing:
 * the Welcome's TOOLING column lists every window with its chord, the
 * Window menu lists them again, and the ⌥⌘ chords open them directly. The
 * change reaches only a userdir with no saved layout — the window system
 * persists what an existing install has, so nobody's arrangement moves.
 *
 * <p>This gate does not re-argue the decision; it makes it <b>stated</b>.
 * The population is derived from every {@code @TopComponent.Registration}
 * in the product's own sources, so a new window fails the build until it is
 * classified here with a reason. The count of editor tabs a newcomer meets
 * carries its own assertion, because that number is the whole point.
 */
class FirstLaunchWindowsLedgerTest {

    /** The product's own modules, in cluster order. */
    private static final List<String> MODULES = List.of(
            "core", "editor", "tools", "rack", "project", "ui",
            "infra", "apiclient", "dbstudio", "web3");

    private static final Pattern REGISTRATION = Pattern.compile(
            "@TopComponent\\.Registration\\(\\s*mode = \"(\\w+)\"\\s*,\\s*openAtStartup = (true|false)");

    /**
     * The ledger. OPEN means a newcomer meets this window before doing
     * anything; CLOSED means it waits for a chord, the Welcome or the
     * Window menu. Every entry carries the reason it is on its side.
     */
    private static final Map<String, String> OPEN = new LinkedHashMap<>();
    private static final Map<String, String> CLOSED = new LinkedHashMap<>();

    static {
        OPEN.put("MainWindow",
                "the Welcome launchpad — the one surface that explains the others");
        OPEN.put("RackTopComponent",
                "the Task Rack: the product's signature window, and where a run reports");
        OPEN.put("WebBrowserTopComponent",
                "a Run arms OpenOnServe and the served page lands here (v1.212.0)");
        OPEN.put("ProjectStudioTopComponent",
                "docked: the project's file tree");
        OPEN.put("ProjectExplorerTopComponent",
                "docked: the Workbench — recent files, projects, the tooling shelf");
        OPEN.put("NpmExplorerTopComponent",
                "docked: the project's own scripts and dependencies");

        CLOSED.put("DbStudioTopComponent",
                "a project with no database does not need a database suite (⌥⌘7)");
        CLOSED.put("Web3StudioTopComponent",
                "Contract Studio announced \"0 contract artifacts\" for a bakery API (⌥⌘6)");
        CLOSED.put("InfraDesignerTopComponent",
                "a project that deploys nowhere does not need a cloud canvas (⌥⌘9)");
        CLOSED.put("ApiClientTopComponent",
                "API Studio opened on a seeded sample request nobody asked for (⌥⌘8)");
        CLOSED.put("IrcTopComponent",
                "a chat client is not the first thing a work IDE shows (⌥⌘3)");
        CLOSED.put("TasksTopComponent",
                "an empty board on a project that has none, and its first touch "
                        + "writes .nmoxtasks.json into someone else's repository (⌥⌘1)");
        CLOSED.put("DockerPanelTopComponent",
                "a project with no Dockerfile does not need the Docker Panel (⌘8)");
        CLOSED.put("BlockStudioTopComponent",
                "the component composer waits to be asked (⌥⌘5)");
        CLOSED.put("TestsExplorerTopComponent",
                "the Tests window indexes on show, and opens itself when tests run (⌥⌘2)");
    }

    /** preferredID → (mode, openAtStartup), read from the product's own sources. */
    private static Map<String, String[]> registrations() throws IOException {
        Map<String, String[]> found = new TreeMap<>();
        for (String module : MODULES) {
            Path root = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(root)) {
                for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
                    Matcher m = REGISTRATION.matcher(src);
                    if (!m.find()) {
                        continue;
                    }
                    String name = p.getFileName().toString().replace(".java", "");
                    found.put(name, new String[] {m.group(1), m.group(2)});
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("every window the product ships is classified, and matches its classification")
    void everyWindowStatesItsFirstLaunchDecision() throws IOException {
        Map<String, String[]> found = registrations();
        assertThat(found)
                .as("windows found in the product's sources — an empty scan would prove nothing")
                .hasSizeGreaterThanOrEqualTo(15);

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String[]> e : found.entrySet()) {
            String window = e.getKey();
            boolean opens = "true".equals(e.getValue()[1]);
            boolean listedOpen = OPEN.containsKey(window);
            boolean listedClosed = CLOSED.containsKey(window);
            if (!listedOpen && !listedClosed) {
                problems.add(window + ": a new window — classify it in this ledger, with the "
                        + "reason it does or does not meet a newcomer before they do anything");
            } else if (opens && !listedOpen) {
                problems.add(window + ": opens at first launch, but the ledger says it should not ("
                        + CLOSED.get(window) + ")");
            } else if (!opens && !listedClosed) {
                problems.add(window + ": does not open at first launch, but the ledger says it should ("
                        + OPEN.get(window) + ")");
            }
        }
        assertThat(problems).as("windows whose first-launch behaviour is unstated or contradicts the ledger")
                .isEmpty();
    }

    @Test
    @DisplayName("a newcomer meets three editor tabs, not ten")
    void theTabStripIsShort() throws IOException {
        Map<String, String[]> found = registrations();
        List<String> tabs = found.entrySet().stream()
                .filter(e -> "editor".equals(e.getValue()[0]) && "true".equals(e.getValue()[1]))
                .map(Map.Entry::getKey).sorted().toList();
        assertThat(tabs)
                .as("the editor-area tabs open before the user has opened a file — their own "
                        + "file should not arrive eleventh in the strip (ledger 96a)")
                .containsExactly("MainWindow", "RackTopComponent", "WebBrowserTopComponent");
    }
}
