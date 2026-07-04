package org.nmox.studio.rack.projectstudio;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackIO;

import static org.assertj.core.api.Assertions.assertThat;

class RackPresetsTest {

    @TempDir
    Path projectDir;

    @Test
    @DisplayName("Should build a mountable patch from every preset")
    void shouldBuildEveryPreset() {
        for (RackPresets preset : RackPresets.values()) {
            Rack rack = new Rack();
            rack.setProjectDir(projectDir.toFile());
            try {
                RackIO.fromJson(rack, preset.buildPatch());
                assertThat(rack.getDevices()).as(preset + " devices").isNotEmpty();
                assertThat(rack.getCables()).as(preset + " cables").isNotEmpty();
            } finally {
                rack.shutdown();
            }
        }
    }

    @Test
    @DisplayName("Ship Gate chains build through every quality gate into the armed deploy")
    void shipGateChainsEveryGate() {
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        try {
            RackIO.fromJson(rack, RackPresets.SHIP_GATE.buildPatch());
            assertThat(rack.getDevices()).extracting(d -> d.getTypeId()).containsExactly(
                    "master", "build", "vitals", "bundle-size", "preflight", "deploy", "console");
            assertThat(rack.getCables()).hasSize(7);
            // each gate's OK feeds the next; PREFLIGHT's OK is the only way into DEPLOY
            assertThat(wired(rack, "master", "trig1", "build", "run")).isTrue();
            assertThat(wired(rack, "build", "ok", "vitals", "run")).isTrue();
            assertThat(wired(rack, "vitals", "ok", "bundle-size", "run")).isTrue();
            assertThat(wired(rack, "bundle-size", "ok", "preflight", "run")).isTrue();
            assertThat(wired(rack, "preflight", "ok", "deploy", "run")).isTrue();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Dev Intelligence surrounds the dev server with the awareness devices")
    void devIntelligenceWiresAwareness() {
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        try {
            RackIO.fromJson(rack, RackPresets.DEV_INTELLIGENCE.buildPatch());
            assertThat(rack.getDevices()).extracting(d -> d.getTypeId()).containsExactly(
                    "dev-server", "tempo", "http", "sonar", "blackbox", "tail", "console");
            assertThat(rack.getCables()).hasSize(8);
            // health probes only while serving, like Uptime Watch
            assertThat(wired(rack, "dev-server", "running", "tempo", "enable")).isTrue();
            assertThat(wired(rack, "tempo", "tick", "http", "send")).isTrue();
            // the clock also sweeps the ports; every recorder lands on the console
            assertThat(wired(rack, "tempo", "bar", "sonar", "run")).isTrue();
            assertThat(wired(rack, "sonar", "out", "console", "in")).isTrue();
            assertThat(wired(rack, "blackbox", "out", "console", "in")).isTrue();
            assertThat(wired(rack, "tail", "out", "console", "in")).isTrue();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("LAMP Bench fans composer install out to the PHP quality lane")
    void lampBenchWiresPhpLane() {
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        try {
            RackIO.fromJson(rack, RackPresets.LAMP_BENCH.buildPatch());
            assertThat(rack.getDevices()).extracting(d -> d.getTypeId()).containsExactly(
                    "package-manager", "test", "typecheck", "format", "run", "console");
            assertThat(rack.getCables()).hasSize(8);
            // one green install fans out to phpunit, phpstan and Pint
            assertThat(wired(rack, "package-manager", "ok", "test", "run")).isTrue();
            assertThat(wired(rack, "package-manager", "ok", "typecheck", "run")).isTrue();
            assertThat(wired(rack, "package-manager", "ok", "format", "run")).isTrue();
            // every lane lands on the console, IGNITION's serve log included
            assertThat(wired(rack, "test", "out", "console", "in")).isTrue();
            assertThat(wired(rack, "typecheck", "out", "console", "in")).isTrue();
            assertThat(wired(rack, "format", "out", "console", "in")).isTrue();
            assertThat(wired(rack, "run", "out", "console", "in")).isTrue();
            // the knobs are pinned to the PHP lane: phpunit runner, php target
            assertThat(stateOf(rack, "test").get("framework")).isEqualTo("11");
            assertThat(stateOf(rack, "run").get("target")).isEqualTo("19");
            assertThat(stateOf(rack, "format").get("write"))
                    .as("a bench verifies, it doesn't rewrite").isEqualTo("false");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Web3 Bench chains build → test → gas gate, with ANVIL free-running on the console")
    void web3BenchWiresFoundryLane() {
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        try {
            RackIO.fromJson(rack, RackPresets.WEB3_BENCH.buildPatch());
            assertThat(rack.getDevices()).extracting(d -> d.getTypeId()).containsExactly(
                    "master", "build", "test", "gas-budget", "anvil", "console");
            assertThat(rack.getCables()).hasSize(7);
            // a gas regression stops the train before anything ships
            assertThat(wired(rack, "master", "trig1", "build", "run")).isTrue();
            assertThat(wired(rack, "build", "ok", "test", "run")).isTrue();
            assertThat(wired(rack, "test", "ok", "gas-budget", "run")).isTrue();
            // every lane lands on the console, the chain's banner included
            assertThat(wired(rack, "build", "out", "console", "in")).isTrue();
            assertThat(wired(rack, "test", "out", "console", "in")).isTrue();
            assertThat(wired(rack, "gas-budget", "out", "console", "in")).isTrue();
            assertThat(wired(rack, "anvil", "out", "console", "in")).isTrue();
            // VERITAS pinned to the forge runner
            assertThat(stateOf(rack, "test").get("framework")).isEqualTo("25");
        } finally {
            rack.shutdown();
        }
    }

    /** The state map of the first device with the given type id. */
    private static java.util.Map<String, String> stateOf(Rack rack, String typeId) {
        return rack.getDevices().stream()
                .filter(d -> d.getTypeId().equals(typeId))
                .findFirst().orElseThrow().getState();
    }

    /** True when a cable runs from (fromType, fromPort) to (toType, toPort). */
    private static boolean wired(Rack rack, String fromType, String fromPort,
            String toType, String toPort) {
        return rack.getCables().stream().anyMatch(c ->
                c.getFrom().getDevice().getTypeId().equals(fromType)
                && c.getFrom().getId().equals(fromPort)
                && c.getTo().getDevice().getTypeId().equals(toType)
                && c.getTo().getId().equals(toPort));
    }
}
