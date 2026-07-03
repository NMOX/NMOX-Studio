package org.nmox.studio.rack.devices;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three gold-standard devices do what their faceplates claim:
 * SOLDER tokenizes like a shell without being one, VITALS reads real
 * Lighthouse JSON and closes the gate below the floor.
 * (TAIL's polling loop is Swing-timer driven; its line-splitting and
 * rotation reset are exercised through the running app.)
 */
class GoldStandardDevicesTest {

    @Test
    @DisplayName("SOLDER splits argv with quotes, never invokes a shell")
    void solderTokenizes() {
        assertThat(CommandLineDevice.splitArgs("make seed-db"))
                .containsExactly("make", "seed-db");
        assertThat(CommandLineDevice.splitArgs("./run.sh --name \"My App\" -v"))
                .containsExactly("./run.sh", "--name", "My App", "-v");
        assertThat(CommandLineDevice.splitArgs("echo 'a  b'   c"))
                .containsExactly("echo", "a  b", "c");
        assertThat(CommandLineDevice.splitArgs("  ")).isEmpty();
        assertThat(CommandLineDevice.splitArgs(null)).isEmpty();
        // empty quoted arg survives as an argument
        assertThat(CommandLineDevice.splitArgs("cmd \"\" tail"))
                .containsExactly("cmd", "", "tail");
    }

    @Test
    @DisplayName("SOLDER with an empty LCD builds no command")
    void solderEmptyIsNull() {
        CommandLineDevice solder = new CommandLineDevice();
        assertThat(solder.buildCommand()).isNull();
    }

    @Test
    @DisplayName("VITALS parses the four category scores from Lighthouse JSON")
    void vitalsParsesScores() {
        String report = """
                {"lighthouseVersion":"12.0.0","categories":{\
                "performance":{"score":0.97},"accessibility":{"score":0.88},\
                "best-practices":{"score":1.0},"seo":{"score":0.75}}}""";
        VitalsDevice.Scores s = VitalsDevice.parseScores(report);
        assertThat(s).isNotNull();
        assertThat(VitalsDevice.pct(s.performance())).isEqualTo(97);
        assertThat(VitalsDevice.pct(s.accessibility())).isEqualTo(88);
        assertThat(VitalsDevice.pct(s.bestPractices())).isEqualTo(100);
        assertThat(VitalsDevice.pct(s.seo())).isEqualTo(75);

        assertThat(VitalsDevice.parseScores("not json")).isNull();
        assertThat(VitalsDevice.parseScores("{\"a\":1}")).isNull();
    }

    @Test
    @DisplayName("VITALS builds a headless lighthouse command for the dialed URL")
    void vitalsBuildsRealCommand() {
        VitalsDevice vitals = new VitalsDevice();
        List<String> cmd = vitals.buildCommand();
        assertThat(cmd).startsWith("npx", "lighthouse", "http://localhost:5173")
                .contains("--output=json");
    }

    @Test
    @DisplayName("VITALS floor: knob off means exit code decides; dialed floor gates")
    void vitalsFloorParsing() {
        VitalsDevice vitals = new VitalsDevice();
        assertThat(vitals.minimum()).isZero(); // factory position: off
        vitals.applyState(java.util.Map.of("min", "4")); // "90"
        assertThat(vitals.minimum()).isEqualTo(90);
    }

    @Test
    @DisplayName("VITALS GATE knob: the floor can hold perf, a11y (WCAG), or both")
    void vitalsGateChoosesStandard() {
        VitalsDevice vitals = new VitalsDevice();
        vitals.applyState(java.util.Map.of("min", "4")); // floor 90
        vitals.scoresForTest(new VitalsDevice.Scores(0.95, 0.60, 1.0, 1.0)); // fast, inaccessible

        // default gate = perf: only performance holds the floor
        org.assertj.core.api.Assertions.assertThat(vitals.gate()).isEqualTo("perf");
        org.assertj.core.api.Assertions.assertThat(vitals.overallSuccess(0)).isTrue();

        vitals.applyState(java.util.Map.of("gate", "1")); // a11y
        org.assertj.core.api.Assertions.assertThat(vitals.overallSuccess(0))
                .as("a11y 60 < 90: WCAG closes the gate").isFalse();

        vitals.applyState(java.util.Map.of("gate", "2")); // both
        org.assertj.core.api.Assertions.assertThat(vitals.overallSuccess(0)).isFalse();

        vitals.scoresForTest(new VitalsDevice.Scores(0.95, 0.97, 1.0, 1.0));
        org.assertj.core.api.Assertions.assertThat(vitals.overallSuccess(0))
                .as("both standards above the floor").isTrue();
    }
}
