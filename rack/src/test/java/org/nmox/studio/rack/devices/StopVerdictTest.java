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
        // v2.75.0: the flag half is its own hook (Stop All marks, then panics)
        src = src.replace("\r\n", "\n");
        assertThat(src).contains("markStoppedByUser();\n        stopProcess();")
                .contains("protected void markStoppedByUser() {\n        stopRequested = true;");
        assertThat(src).as("the internal cancel stays unflagged: no stopProcess override in CommandDevice").doesNotContain("protected void stopProcess()");
        assertThat(src.split("stopRequested = false;").length - 1).as("cleared at both launch sites and after each verdict").isGreaterThanOrEqualTo(4);
        assertThat(src.split("stoppedByUserOrSignal\\(stopRequested, code\\)").length - 1).as("both exit handlers consult the flag").isEqualTo(2);
    }

    @Test
    @DisplayName("Only the engine's own cancels call stopProcess() — every STOP button, patched STOP jack, SURGE shutdown and SPI stop goes through stopByUser (allowlist law, v2.69.17)")
    void onlyTheEngineCallsTheInternalCancel() throws Exception {
        java.util.Map<String, Integer> allowed = java.util.Map.of(
                "CommandDevice.java", 1,   // stopByUser() itself
                "ExtensionDevice.java", 2, // dispose() + the SPI's stop(): RackDevice-based, the SPI reports its own status
                "PreflightDevice.java", 1); // RackDevice-based: its own checklist loop and its own stopRequested verdict (v2.74.0: one stopByUser both doors call; v2.75.0: markStoppedByUser is the flag half)
        java.util.List<String> offenders = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.list(Path.of("src/main/java/org/nmox/studio/rack/devices"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readAllLines(f).stream()
                        .filter(l -> !l.strip().startsWith("//") && !l.strip().startsWith("*") && !l.strip().startsWith("/*"))
                        .collect(java.util.stream.Collectors.joining("\n"));
                int calls = src.split("stopProcess\\(\\);").length - 1;
                int permitted = allowed.getOrDefault(f.getFileName().toString(), 0);
                if (calls > permitted) {
                    offenders.add(f.getFileName() + " calls stopProcess() " + calls + "x (allowed " + permitted + ")");
                }
            }
        }
        assertThat(offenders).as("a user-facing stop wired to the internal cancel reads OK after a clean-exit server").isEmpty();
        // v2.74.0: the outside stop (■ / RUNNING row / ⌘I) is the user's stop on
        // every device that keeps its own verdict — CommandDevice and PREFLIGHT
        for (String f : java.util.List.of("CommandDevice.java", "PreflightDevice.java")) {
            // CRLF folded: the windows lane's checkout carries \r\n and a two-line
            // literal never matched there (the v1.42.0 folding law, relearned on PR #686)
            String src = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/devices/" + f)).replace("\r\n", "\n");
            assertThat(src).as(f + ": stopFromOutside routes through stopByUser").contains("protected void stopFromOutside() {\n        stopByUser();");
        }
    }
}
