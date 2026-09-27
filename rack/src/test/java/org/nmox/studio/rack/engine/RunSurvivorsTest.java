package org.nmox.studio.rack.engine;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.nmox.studio.core.spi.LiveRuns;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3.4, "when something goes wrong": {@code sh -c "sleep 1105 & echo bg"} —
 * the root exits at once, the run leaves every registry, and the background
 * child outlived Stop, quitting the IDE and the JVM-exit reaper. Two shapes,
 * both covered: a survivor that let go of the run's output (the run ends; the
 * survivor gets a row of its own that Stop ends) and one that still holds it
 * (the run is not over; its own Stop ends the whole family). Either way the
 * JVM-exit reaper ends them.
 */
@DisabledOnOs(OS.WINDOWS) // POSIX shells and reparenting: the Windows PID chain is ledger 38
class RunSurvivorsTest {

    /** A unique sleep length marks this test's child among every process on the machine. */
    private final String mark = "1105." + (System.nanoTime() % 100_000);

    @AfterEach
    void cleanUp() {
        mine().ifPresent(ProcessHandle::destroyForcibly);
        LiveRuns.clearForTest();
    }

    private Optional<ProcessHandle> mine() {
        return ProcessHandle.allProcesses()
                // the sleep itself, not a shell whose own command line names it
                .filter(h -> h.info().commandLine().orElse("").endsWith("sleep " + mark))
                .findFirst();
    }

    /** Runs {@code script}, whose root lives long enough to be sampled on any runner. */
    private CommandExecutor.Handle run(String tab, String script, List<String> lines,
            CompletableFuture<Integer> exit) {
        return CommandExecutor.run(tab, new File("."), Map.of(), List.of("sh", "-c", script),
                lines::add, exit::complete);
    }

    /** Waits until the run printed "started" and its root has had time to exit. */
    private ProcessHandle survivorOnceRootIsGone(List<String> lines) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline && !lines.contains("started")) {
            Thread.sleep(20);
        }
        assertThat(lines).contains("started");
        Thread.sleep(800); // the root exits right after its echo
        return mine().orElseThrow(() -> new AssertionError("the background sleep is running"));
    }

    @Test
    @DisplayName("A child that let go of the output outlives the run: it gets a row Stop ends")
    void survivorIsListedAndStopEndsIt() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        CompletableFuture<Integer> exit = new CompletableFuture<>();
        run("survivors", "sleep " + mark + " >/dev/null 2>&1 & sleep 0.3; echo started", lines, exit);
        int code = exit.get(20, TimeUnit.SECONDS);
        assertThat(code).as("the run itself ends when its root does").isZero();

        ProcessHandle child = mine().orElseThrow(() -> new AssertionError("the background sleep is running"));
        String rowId = null;
        for (LiveRuns.Run r : LiveRuns.live()) {
            if (r.id().startsWith("background:survivors#")) {
                rowId = r.id();
                assertThat(r.label()).contains("still running in the background").contains(String.valueOf(child.pid()));
            }
        }
        assertThat(rowId).as("the survivor has a row the ■ lists").isNotNull();
        assertThat(RunSurvivors.orphans()).as("the JVM-exit reaper will end it").contains(child);

        assertThat(LiveRuns.stop(rowId)).isNotNull();
        child.onExit().get(15, TimeUnit.SECONDS);
        assertThat(child.isAlive()).isFalse();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        String id = rowId;
        while (System.nanoTime() < deadline && LiveRuns.live().stream().anyMatch(r -> r.id().equals(id))) {
            Thread.sleep(20);
        }
        assertThat(LiveRuns.live()).as("the row leaves when the survivor has exited")
                .noneMatch(r -> r.id().equals(id));
    }

    @Test
    @DisplayName("A child that still holds the output: the run's own Stop ends it, and the run then ends")
    void heldOutputStopEndsTheFamily() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        CompletableFuture<Integer> exit = new CompletableFuture<>();
        CommandExecutor.Handle h = run("survivors-held", "sleep " + mark + " & sleep 0.3; echo started", lines, exit);
        ProcessHandle child = survivorOnceRootIsGone(lines);
        h.kill();
        child.onExit().get(15, TimeUnit.SECONDS);
        assertThat(child.isAlive()).as("a Stop ends everything the run started").isFalse();
        exit.get(15, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Quitting the IDE ends a survivor whose root is long gone")
    void reaperEndsSurvivors() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        run("survivors-reap", "sleep " + mark + " & sleep 0.3; echo started", lines, new CompletableFuture<>());
        ProcessHandle child = survivorOnceRootIsGone(lines);
        CommandExecutor.reapLiveNow();
        child.onExit().get(10, TimeUnit.SECONDS);
        assertThat(child.isAlive()).isFalse();
    }

    @Test
    @DisplayName("A Stop of the run while its root lives ends the child an intermediate shell orphaned")
    void stopReachesAnOrphanedGrandchild() throws Exception {
        CompletableFuture<Integer> exit = new CompletableFuture<>();
        // the root stays alive; the inner shell backgrounds the sleep, lives long
        // enough to be sampled, and exits — the sleep is reparented to init and
        // is no longer a descendant of anything the run owns
        CommandExecutor.Handle h = CommandExecutor.run("survivors-stop", new File("."), Map.of(),
                List.of("sh", "-c", "sh -c 'sleep " + mark + " & sleep 0.3'; sleep 30"),
                line -> { }, exit::complete);
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline && mine().isEmpty()) {
            Thread.sleep(20);
        }
        ProcessHandle child = mine().orElseThrow(() -> new AssertionError("the grandchild is running"));
        Thread.sleep(800); // its shell has exited: the sleep is now an orphan
        h.kill();
        child.onExit().get(15, TimeUnit.SECONDS);
        assertThat(child.isAlive()).as("a Stop ends everything the run started").isFalse();
        exit.get(15, TimeUnit.SECONDS);
    }
}
