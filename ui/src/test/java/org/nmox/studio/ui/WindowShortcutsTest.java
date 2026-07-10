package org.nmox.studio.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.38.1 DX pass pressed every shortcut the Welcome launchpad
 * advertises. Four of seven opened the WRONG window: ⌘0 gave the Editor,
 * ⇧⌘6 gave Tasks, ⇧⌘7 gave Properties, ⇧⌘8 gave the Palette. The chords
 * were claimed by the platform's Keymaps profile — a different mechanism
 * from the Shortcuts folder we register in, which is why an audit of layer
 * files alone never saw the collision.
 *
 * This pins both halves of the contract: our windows only claim chords no
 * shipped module owns, and the Welcome screen advertises exactly the chord
 * that is registered. A label that lies is worse than no label at all.
 */
class WindowShortcutsTest {

    /**
     * Chords claimed by modules we ship, verified against their layer files
     * (org-netbeans-modules-defaults, bugtracking, tasklist-ui, api-search,
     * spi-debugger-ui). Taking one of these back means the platform's window
     * opens instead of ours.
     */
    private static final Set<String> RESERVED = Set.of(
            "D-0", "D-6", "D-7",              // Editor, Action Items, ...
            "DS-0", "DS-6", "DS-7", "DS-8",   // search, Tasks, Properties, Palette
            "SO-6", "SO-7", "SO-8", "SO-9",   // debugger step actions
            "CS-8",
            "DS-O", "D-O");                   // Open Project, Open File

    /** Where each window registers its chord, and what Welcome must show for it. */
    private static final Map<String, String[]> WINDOWS = new LinkedHashMap<>();
    static {
        // module source path                                              chord   label
        WINDOWS.put("../dbstudio/src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java",
                new String[] {"DA-7", "DB Studio  ⌥⌘7"});
        WINDOWS.put("../web3/src/main/java/org/nmox/studio/web3/ui/Web3StudioTopComponent.java",
                new String[] {"DA-6", "Contract Studio  ⌥⌘6"});
        WINDOWS.put("../apiclient/src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java",
                new String[] {"DA-8", "API Studio  ⌥⌘8"});
        WINDOWS.put("../infra/src/main/java/org/nmox/studio/infra/InfraDesignerTopComponent.java",
                new String[] {"DA-9", "Infra Designer  ⌥⌘9"});
        WINDOWS.put("../project/src/main/java/org/nmox/studio/project/ProjectExplorerTopComponent.java",
                new String[] {"DA-0", "Workbench  ⌥⌘0"});
        WINDOWS.put("../rack/src/main/java/org/nmox/studio/rack/RackTopComponent.java",
                new String[] {"D-9", "Task Rack  ⌘9"});
        WINDOWS.put("../rack/src/main/java/org/nmox/studio/rack/docker/DockerPanelTopComponent.java",
                new String[] {"D-8", "Docker Panel  ⌘8"});
    }

    private static final Pattern SHORTCUT = Pattern.compile(
            "path\\s*=\\s*\"Shortcuts\"\\s*,\\s*name\\s*=\\s*\"([^\"]+)\"");

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("no window claims a chord the platform already owns")
    void shouldNotCollideWithPlatformChords() throws Exception {
        for (Map.Entry<String, String[]> e : WINDOWS.entrySet()) {
            Matcher m = SHORTCUT.matcher(read(e.getKey()));
            assertThat(m.find()).as(e.getKey() + " registers a shortcut").isTrue();
            String chord = m.group(1);

            assertThat(chord)
                    .as(e.getKey() + ": chord is the one this test expects")
                    .isEqualTo(e.getValue()[0]);
            assertThat(RESERVED)
                    .as(chord + " is claimed by a platform module — it would open "
                            + "that window instead of ours")
                    .doesNotContain(chord);
        }
    }

    @Test
    @DisplayName("Open Folder does not fight the platform's Open Project for ⇧⌘O")
    void openFolderOwnsAFreeChord() throws Exception {
        String action = read("src/main/java/org/nmox/studio/ui/actions/OpenFolderAction.java");
        Matcher m = SHORTCUT.matcher(action);
        assertThat(m.find()).as("Open Folder registers a shortcut").isTrue();
        assertThat(m.group(1)).isEqualTo("DA-O");
        assertThat(RESERVED).doesNotContain(m.group(1));
        assertThat(read("src/main/java/org/nmox/studio/ui/MainWindow.java"))
                .contains("\"Open Folder…  ⌥⌘O\"");
    }

    @Test
    @DisplayName("the Welcome launchpad advertises exactly the chord that is registered")
    void welcomeLabelsMatchRegistrations() throws Exception {
        String welcome = read("src/main/java/org/nmox/studio/ui/MainWindow.java");
        for (String[] v : WINDOWS.values()) {
            assertThat(welcome)
                    .as("Welcome must advertise the real chord for " + v[0])
                    .contains("\"" + v[1] + "\"");
        }
        // and it must not still be advertising any of the chords we lost
        for (String stale : new String[] {"Workbench  ⌘0", "DB Studio  ⇧⌘7",
                "Contract Studio  ⇧⌘6", "API Studio  ⇧⌘8"}) {
            assertThat(welcome).as("stale chord still advertised: " + stale)
                    .doesNotContain("\"" + stale + "\"");
        }
    }
}
