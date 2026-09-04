package org.nmox.studio.rack.devices;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rack walk pressed STOP on a node http-server and the faceplate read
 * "OK 37.985s": the server traps TERM and exits 0, and the verdict read
 * only the exit code. The user's gesture is the truth (v2.69.15).
 */
class StopVerdictTest {

    @Test
    @DisplayName("A run the user stopped is STOPPED whatever its exit code; a signal exit code is STOPPED too; a clean unstopped exit is not")
    void verdict() {
        assertThat(CommandDevice.stoppedByUserOrSignal(true, 0)).as("STOP pressed, process exited 0").isTrue();
        assertThat(CommandDevice.stoppedByUserOrSignal(false, 143)).as("TERM without a press: still a stop").isTrue();
        assertThat(CommandDevice.stoppedByUserOrSignal(false, 0)).as("a clean exit nobody stopped: OK").isFalse();
        assertThat(CommandDevice.stoppedByUserOrSignal(false, 1)).as("a failure: FAIL").isFalse();
    }

    @Test
    @DisplayName("The STOP button sets the flag before the kill, and every launch clears it (source law)")
    void wiring() throws Exception {
        String src = Files.readAllLines(Path.of("src/main/java/org/nmox/studio/rack/devices/CommandDevice.java"))
                .stream().filter(l -> !l.strip().startsWith("//") && !l.strip().startsWith("*")).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(src).contains("stopRequested = true;\n        stopProcess();");
        assertThat(src).as("the internal cancel stays unflagged: no stopProcess override in CommandDevice").doesNotContain("protected void stopProcess()");
        assertThat(src.split("stopRequested = false;").length - 1).as("cleared at both launch sites and after each verdict").isGreaterThanOrEqualTo(4);
        assertThat(src.split("stoppedByUserOrSignal\\(stopRequested, code\\)").length - 1).as("both exit handlers consult the flag").isEqualTo(2);
    }

    @Test
    @DisplayName("Every STOP button and patched STOP jack in the device catalog goes through stopByUser (source law)")
    void everyStopButtonIsAUserStop() throws Exception {
        java.util.List<String> offenders = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.list(Path.of("src/main/java/org/nmox/studio/rack/devices"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f);
                if (src.contains("e -> stopProcess()") || src.contains("case \"stop\" -> stopProcess()")
                        || src.contains("if (\"stop\".equals(in.getId())) {\n            stopProcess();")) {
                    offenders.add(f.getFileName().toString());
                }
            }
        }
        assertThat(offenders).as("a STOP button wired to the internal cancel reads OK after a clean-exit server").isEmpty();
    }
}
