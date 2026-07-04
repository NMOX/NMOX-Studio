package org.nmox.studio.rack.devices;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.projectstudio.LearningCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The REPL's missing-interpreter story: the persisted install param a
 * learning space seeds, the INSTALL button's enablement rule, the
 * launch-failure message that points at it, and the ENGINE knob — the
 * synth preset pattern that seeds command/snippets/install from the
 * learning catalog's repl drivers.
 */
class ReplDeviceTest {

    private static void flushEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // interrupted — the assertion will tell
        }
    }

    // ---------------- INSTALL: the persisted param ----------------

    @Test
    @DisplayName("The install param defaults blank and round-trips through applyState")
    void installParamRoundTrips() {
        ReplDevice repl = new ReplDevice();
        try {
            assertThat(repl.getState().get("install")).isEmpty();
            repl.applyState(Map.of("install", "brew install clisp"));
            assertThat(repl.getState()).containsEntry("install", "brew install clisp");
        } finally {
            repl.dispose();
        }
    }

    @Test
    @DisplayName("INSTALL enables only with a seeded command, no live REPL, no install in flight")
    void installEnablementRule() {
        assertThat(ReplDevice.installEnabled("brew install clisp", false, false)).isTrue();
        assertThat(ReplDevice.installEnabled("", false, false))
                .as("nothing seeded").isFalse();
        assertThat(ReplDevice.installEnabled("   ", false, false))
                .as("blank seeded").isFalse();
        assertThat(ReplDevice.installEnabled(null, false, false)).isFalse();
        assertThat(ReplDevice.installEnabled("brew install clisp", true, false))
                .as("REPL live").isFalse();
        assertThat(ReplDevice.installEnabled("brew install clisp", false, true))
                .as("install already running").isFalse();
    }

    @Test
    @DisplayName("The device's INSTALL availability follows the seeded state")
    void installAvailabilityFollowsSeededState() {
        ReplDevice repl = new ReplDevice();
        try {
            assertThat(repl.installActionAvailable()).as("fresh device, nothing seeded").isFalse();
            repl.applyState(Map.of("install", "brew install clisp"));
            assertThat(repl.installActionAvailable()).as("seeded, not live").isTrue();
        } finally {
            repl.dispose();
        }
    }

    @Test
    @DisplayName("A failed launch offers INSTALL when a command is seeded — and doesn't when not")
    void launchFailureMentionsInstallOnlyWhenSeeded() {
        ReplDevice seeded = new ReplDevice();
        try {
            seeded.applyState(Map.of(
                    "command", "definitely-not-a-real-interpreter-xyz",
                    "install", "brew install xyz"));
            seeded.startRepl();
            flushEdt();
            assertThat(seeded.screenText()).contains("would not start")
                    .contains("— or press INSTALL");
        } finally {
            seeded.dispose();
        }

        ReplDevice bare = new ReplDevice();
        try {
            bare.applyState(Map.of("command", "definitely-not-a-real-interpreter-xyz"));
            bare.startRepl();
            flushEdt();
            assertThat(bare.screenText()).contains("would not start")
                    .doesNotContain("press INSTALL");
        } finally {
            bare.dispose();
        }
    }

    // ---------------- ENGINE: the preset knob ----------------

    @Test
    @DisplayName("Dialing an engine seeds the full force-interactive command, snippets, and install")
    void engineSeedsKnownInterpreters() {
        ReplDevice repl = new ReplDevice();
        try {
            repl.applyState(Map.of("engine", "python"));
            assertThat(repl.getState().get("command")).isEqualTo("python3 -i -q");

            repl.applyState(Map.of("engine", "node"));
            assertThat(repl.getState().get("command")).isEqualTo("node -i");

            repl.applyState(Map.of("engine", "lisp"));
            assertThat(repl.getState().get("command")).isEqualTo("clisp");
            assertThat(repl.getState().get("snippets"))
                    .as("HINTS works outside learning spaces").isNotBlank();
            assertThat(repl.getState().get("install"))
                    .as("INSTALL is armed for the picked engine").isNotBlank();
        } finally {
            repl.dispose();
        }
    }

    @Test
    @DisplayName("An explicit command/snippets in the same state map wins over the engine's seeding")
    void explicitStateWinsOverEngineSeeding() {
        ReplDevice repl = new ReplDevice();
        try {
            repl.applyState(Map.of(
                    "engine", "python",
                    "command", "python3 -i -q -B",
                    "snippets", "print(42)"));
            assertThat(repl.getState().get("command"))
                    .as("a saved patch's custom command survives").isEqualTo("python3 -i -q -B");
            assertThat(repl.getState().get("snippets")).isEqualTo("print(42)");
            assertThat(repl.getState().get("engine")).isEqualTo("python");
        } finally {
            repl.dispose();
        }
    }

    @Test
    @DisplayName("A hand edit of the COMMAND LCD flips the engine to CUSTOM, keeping the command")
    void manualCommandEditFlipsToCustom() {
        ReplDevice repl = new ReplDevice();
        try {
            repl.applyState(Map.of("engine", "haskell"));
            assertThat(repl.getState().get("command")).isEqualTo("ghci");

            repl.commandEdited();
            assertThat(repl.getState().get("engine")).isEqualTo(ReplDevice.CUSTOM_ENGINE);
            assertThat(repl.getState().get("command"))
                    .as("CUSTOM keeps whatever the LCD says").isEqualTo("ghci");
        } finally {
            repl.dispose();
        }
    }

    @Test
    @DisplayName("The engine persists by NAME and reseeds its command on a fresh device")
    void enginePersistsByName() {
        ReplDevice repl = new ReplDevice();
        ReplDevice clone = new ReplDevice();
        try {
            repl.applyState(Map.of("engine", "haskell"));
            Map<String, String> state = repl.getState();
            assertThat(state.get("engine")).as("name, not index").isEqualTo("haskell");

            clone.applyState(state);
            assertThat(clone.getState().get("command")).isEqualTo("ghci");
            assertThat(clone.getState()).isEqualTo(state);
        } finally {
            repl.dispose();
            clone.dispose();
        }
    }

    @Test
    @DisplayName("The engine list derives from the catalog: CUSTOM first, then lisp/python/node in catalog order")
    void engineCatalogDerivation() {
        String[] options = ReplDevice.engineOptions();
        assertThat(options[0]).isEqualTo(ReplDevice.CUSTOM_ENGINE);

        List<String> labels = ReplDevice.engineCatalog().stream()
                .map(ReplDevice.Engine::label).toList();
        assertThat(labels).contains("lisp", "python", "node");
        assertThat(labels).doesNotHaveDuplicates();
        assertThat(ReplDevice.engineCatalog()).extracting(ReplDevice.Engine::command)
                .doesNotHaveDuplicates();
        // catalog order pinned: the worked example leads, python and node follow
        assertThat(labels.indexOf("lisp")).isLessThan(labels.indexOf("python"));
        assertThat(labels.indexOf("python")).isLessThan(labels.indexOf("node"));
    }

    @Test
    @DisplayName("Engine derivation dedups by command: first catalog entry wins")
    void deriveEnginesDedupsByCommand() {
        LearningCatalog.Driver python = new LearningCatalog.Driver(
                LearningCatalog.DriverKind.REPL, List.of("python3"), ">>>",
                List.of("import numpy"));
        LearningCatalog.Space first = new LearningCatalog.Space("numpy", "NumPy",
                LearningCatalog.Category.LIBRARY, "Python data", "b", python,
                Map.of(), List.of(), "t");
        LearningCatalog.Space duplicate = new LearningCatalog.Space("pandas", "pandas",
                LearningCatalog.Category.LIBRARY, "Python data", "b", python,
                Map.of(), List.of(), "t");
        LearningCatalog.Space runKind = new LearningCatalog.Space("react", "React",
                LearningCatalog.Category.FRAMEWORK, "JS", "b",
                new LearningCatalog.Driver(LearningCatalog.DriverKind.RUN,
                        List.of("npm", "run", "dev"), "", List.of()),
                Map.of(), List.of(), "t");

        List<ReplDevice.Engine> engines =
                ReplDevice.deriveEngines(List.of(first, duplicate, runKind));
        assertThat(engines).hasSize(1);
        assertThat(engines.get(0).label()).isEqualTo("numpy");
        assertThat(engines.get(0).command()).isEqualTo("python3");
    }

    @Test
    @DisplayName("The engine label is the shortest hyphen-segment of the slug")
    void engineLabelRule() {
        assertThat(ReplDevice.engineLabel("lisp-clisp")).isEqualTo("lisp");
        assertThat(ReplDevice.engineLabel("javascript-node")).isEqualTo("node");
        assertThat(ReplDevice.engineLabel("python")).isEqualTo("python");
        assertThat(ReplDevice.engineLabel("r")).isEqualTo("r");
    }
}
