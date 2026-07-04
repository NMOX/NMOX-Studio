package org.nmox.studio.rack.projectstudio;

import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The learning-space generator's load-bearing logic: a REPL space wires
 * a REPL device seeded with the interpreter and snippets; a run space
 * wires SOLDER into a MONITOR; and the tutorial always carries the
 * right-OS install hint.
 */
class LearningSpaceTest {

    private static LearningCatalog.Space repl() {
        return new LearningCatalog.Space("lisp-clisp", "Common Lisp",
                LearningCatalog.Category.LANGUAGE, "Lisp", "blurb",
                new LearningCatalog.Driver(LearningCatalog.DriverKind.REPL,
                        List.of("clisp"), "[1]>", List.of("(+ 1 2)", "(* 3 4)")),
                Map.of("mac", "brew install clisp", "linux", "apt install clisp",
                        "windows", "choco install clisp"),
                List.of(new LearningCatalog.SampleFile("hello.lisp", "(print 1)")),
                "# Common Lisp\n\nbody");
    }

    private static LearningCatalog.Space run() {
        return new LearningCatalog.Space("react", "React",
                LearningCatalog.Category.FRAMEWORK, "JavaScript UI", "blurb",
                new LearningCatalog.Driver(LearningCatalog.DriverKind.RUN,
                        List.of("npm", "run", "dev"), "", List.of()),
                Map.of("mac", "brew install node"),
                List.of(new LearningCatalog.SampleFile("index.html", "<html></html>")),
                "# React\n\nbody");
    }

    private static LearningCatalog.Space replNoInstall() {
        return new LearningCatalog.Space("tcl", "Tcl",
                LearningCatalog.Category.LANGUAGE, "Tcl", "blurb",
                new LearningCatalog.Driver(LearningCatalog.DriverKind.REPL,
                        List.of("tclsh"), "%", List.of("expr 1+1")),
                Map.of(),
                List.of(new LearningCatalog.SampleFile("hello.tcl", "puts 1")),
                "# Tcl\n\nbody");
    }

    @Test
    @DisplayName("A REPL space wires one REPL device seeded with command and snippets")
    void replSpaceWiresSeededRepl() {
        JSONObject patch = RackPresets.buildPatchFrom(rack -> LearningSpace.wire(rack, repl()));
        Rack loaded = new Rack();
        try {
            org.nmox.studio.rack.model.RackIO.fromJson(loaded, patch);
            List<RackDevice> devices = loaded.getDevices();
            assertThat(devices).hasSize(1);
            RackDevice replDevice = devices.get(0);
            assertThat(replDevice.getTypeId()).isEqualTo("repl");
            Map<String, String> state = replDevice.getState();
            assertThat(state.get("command")).isEqualTo("clisp");
            assertThat(state.get("snippets")).contains("(+ 1 2)").contains("(* 3 4)");
        } finally {
            loaded.shutdown();
        }
    }

    @Test
    @DisplayName("A run space wires SOLDER into MONITOR with the run command")
    void runSpaceWiresSolderToMonitor() {
        JSONObject patch = RackPresets.buildPatchFrom(rack -> LearningSpace.wire(rack, run()));
        Rack loaded = new Rack();
        try {
            org.nmox.studio.rack.model.RackIO.fromJson(loaded, patch);
            assertThat(loaded.getDevices()).extracting(RackDevice::getTypeId)
                    .containsExactlyInAnyOrder("cmd", "console");
            RackDevice solder = loaded.getDevices().stream()
                    .filter(d -> d.getTypeId().equals("cmd")).findFirst().orElseThrow();
            assertThat(solder.getState().get("command")).isEqualTo("npm run dev");
            assertThat(loaded.getCables()).as("SOLDER out is patched to MONITOR in").hasSize(1);
        } finally {
            loaded.shutdown();
        }
    }

    @Test
    @DisplayName("A REPL space seeds the OS-appropriate install command into the REPL's install param")
    void replSpaceSeedsInstallParam() {
        JSONObject patch = RackPresets.buildPatchFrom(rack -> LearningSpace.wire(rack, repl()));
        Rack loaded = new Rack();
        try {
            org.nmox.studio.rack.model.RackIO.fromJson(loaded, patch);
            RackDevice replDevice = loaded.getDevices().get(0);
            assertThat(replDevice.getState().get("install"))
                    .isNotBlank()
                    .isEqualTo(LearningSpace.installHint(repl()));
        } finally {
            loaded.shutdown();
        }
    }

    @Test
    @DisplayName("A space whose catalog entry has no install map seeds a blank install param")
    void spaceWithoutInstallSeedsBlank() {
        JSONObject patch = RackPresets.buildPatchFrom(
                rack -> LearningSpace.wire(rack, replNoInstall()));
        Rack loaded = new Rack();
        try {
            org.nmox.studio.rack.model.RackIO.fromJson(loaded, patch);
            RackDevice replDevice = loaded.getDevices().get(0);
            assertThat(replDevice.getState().get("install")).isEmpty();
        } finally {
            loaded.shutdown();
        }
    }

    @Test
    @DisplayName("The OS key: mac/darwin map to mac (before the windows check), win to windows, else linux")
    void osKeyPinning() {
        assertThat(LearningSpace.osKey("Mac OS X")).isEqualTo("mac");
        assertThat(LearningSpace.osKey("Darwin")).as("darwin contains 'win' — mac must win")
                .isEqualTo("mac");
        assertThat(LearningSpace.osKey("Windows 11")).isEqualTo("windows");
        assertThat(LearningSpace.osKey("Linux")).isEqualTo("linux");
        assertThat(LearningSpace.osKey("FreeBSD")).as("fallback").isEqualTo("linux");
        assertThat(LearningSpace.osKey("")).isEqualTo("linux");
    }

    @Test
    @DisplayName("installHint falls back to the mac entry and to blank when the map is empty")
    void installHintFallback() {
        // whatever OS runs this test: a mac-only map yields the mac hint
        assertThat(LearningSpace.installHint(run())).isEqualTo("brew install node");
        assertThat(LearningSpace.installHint(replNoInstall())).isEmpty();
    }

    @Test
    @DisplayName("The tutorial gains the install hint for the running OS")
    void tutorialCarriesInstallHint() {
        String tut = LearningSpace.tutorialWithInstall(repl());
        assertThat(tut).startsWith("# Common Lisp");
        assertThat(tut).contains("## Install");
        String os = LearningSpace.osKey();
        assertThat(tut).contains(repl().install().get(os));
    }

    @Test
    @DisplayName("create() lays down sample files, marker, tutorial, and pre-wired rack on disk")
    void createWritesTheWholeSpace(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home)
            throws Exception {
        String realHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            java.io.File dir = LearningSpace.create(repl());
            assertThat(dir).isDirectory();
            assertThat(dir.getName()).isEqualTo("lisp-clisp");
            assertThat(new java.io.File(dir, LearningSpace.MARKER)).exists();
            assertThat(new java.io.File(dir, "hello.lisp")).exists();
            assertThat(new java.io.File(dir, "TUTORIAL.md")).content()
                    .contains("# Common Lisp").contains("## Install");
            String rack = java.nio.file.Files.readString(
                    new java.io.File(dir, ".nmoxrack.json").toPath());
            assertThat(rack).as("the pre-wired rack seeds a REPL with clisp")
                    .contains("\"repl\"").contains("clisp");
            assertThat(LearningSpace.isLearningSpace(dir)).isTrue();
            assertThat(LearningSpace.list()).extracting(java.io.File::getName)
                    .contains("lisp-clisp");
        } finally {
            System.setProperty("user.home", realHome);
        }
    }
}
