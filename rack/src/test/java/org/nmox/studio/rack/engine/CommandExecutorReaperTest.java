package org.nmox.studio.rack.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JVM-exit reaper (2026-08-15 orphan audit): every CommandExecutor
 * spawn joins a live-set that one shutdown hook reaps — TERM to every
 * tree, bounded grace, then KILL. The Rack's own reaper only panics
 * DEVICES, so lanes holding a Handle outside any rack (the IDE Run
 * button, NPM Explorer run-script) orphaned their dev servers on every
 * JVM exit; {@code python3 -m http.server} processes were found still
 * serving after their host app had been pkill-stopped (SIGTERM).
 *
 * Two-proof law (v1.321.0): the seam diverges (reapLiveNow kills a live
 * tree, grandchild included) AND the call sites exist (run() registers
 * the spawn; the static init registered the hook).
 */
class CommandExecutorReaperTest {

    @Test
    @DisplayName("The shutdown hook is registered the moment the executor class loads")
    void hookIsRegistered() {
        assertThat(CommandExecutor.reaperHookRegistered)
                .as("static init must register the nmox-exec-reaper shutdown hook")
                .isTrue();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("run() registers the spawn in the live-set and the set shrinks on exit")
    void runRegistersAndDeregisters() throws Exception {
        Set<Process> before = CommandExecutor.liveSnapshot();
        CountDownLatch exited = new CountDownLatch(1);
        CommandExecutor.Handle h = CommandExecutor.run("reaper-reg", new File("."), Map.of(),
                List.of("sh", "-c", "sleep 30"), l -> { }, c -> exited.countDown());

        Process mine = awaitNewTracked(before);
        assertThat(mine.isAlive()).isTrue();

        h.killAndWait(5_000);
        assertThat(exited.await(10, TimeUnit.SECONDS)).isTrue();
        long deadline = System.currentTimeMillis() + 10_000;
        while (CommandExecutor.liveSnapshot().contains(mine)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(CommandExecutor.liveSnapshot())
                .as("onExit must drop the process from the reaper's set")
                .doesNotContain(mine);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("reapLiveNow kills a tracked process tree, grandchild included")
    void reapKillsWholeTree() throws Exception {
        Set<Process> before = CommandExecutor.liveSnapshot();
        AtomicInteger exit = new AtomicInteger(Integer.MIN_VALUE);
        CountDownLatch exited = new CountDownLatch(1);
        // "sleep 60 & wait" forces a grandchild on every OS (the v1.36.0
        // dash-vs-bash lesson): TERMing only the shell must not pass
        CommandExecutor.run("reaper-tree", new File("."), Map.of(),
                List.of("sh", "-c", "sleep 60 & wait"), l -> { },
                c -> { exit.set(c); exited.countDown(); });

        Process mine = awaitNewTracked(before);
        long deadline = System.currentTimeMillis() + 10_000;
        while (mine.descendants().count() == 0
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        List<ProcessHandle> grandchildren = mine.descendants().toList();
        assertThat(grandchildren).as("the shell must have forked its sleep").isNotEmpty();

        CommandExecutor.reapLiveNow();

        assertThat(exited.await(10, TimeUnit.SECONDS))
                .as("the tracked shell must die").isTrue();
        assertThat(exit.get()).as("a reaped process exits abnormally").isNotEqualTo(0);
        for (ProcessHandle g : grandchildren) {
            long d2 = System.currentTimeMillis() + 10_000;
            while (g.isAlive() && System.currentTimeMillis() < d2) {
                Thread.sleep(50);
            }
            assertThat(g.isAlive())
                    .as("grandchild pid %s must not survive the reap", g.pid())
                    .isFalse();
        }
    }

    /**
     * The real thing, end to end: a child JVM spawns a server tree through
     * CommandExecutor, the test SIGTERMs the child JVM (Process.destroy on
     * POSIX — exactly what a walk's {@code pkill -f <userdir>} sends), and
     * every grandchild must be gone once the child JVM has exited. Windows
     * is out honestly: TerminateProcess runs no shutdown hooks, so this
     * guarantee is POSIX-only by construction.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("SIGTERM to the JVM reaps every CommandExecutor child — no orphans")
    void sigtermReapsSpawnedTree() throws Exception {
        String java = System.getProperty("java.home") + File.separator
                + "bin" + File.separator + "java";
        Process child = new ProcessBuilder(java,
                "-cp", System.getProperty("java.class.path"),
                "-Djava.awt.headless=true",
                ServeMain.class.getName())
                .redirectErrorStream(true)
                .start();
        try {
            boolean ready = false;
            long deadline = System.currentTimeMillis() + 60_000;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    child.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (System.currentTimeMillis() < deadline
                        && (line = r.readLine()) != null) {
                    if (line.contains("REAPER-E2E-READY")) {
                        ready = true;
                        break;
                    }
                }
                assertThat(ready).as("child JVM must report its serve tree up").isTrue();

                List<ProcessHandle> tree = child.descendants().toList();
                assertThat(tree).as("the child JVM must have spawned a tree").isNotEmpty();

                child.destroy(); // SIGTERM — the walk's pkill, precisely
                assertThat(child.waitFor(30, TimeUnit.SECONDS))
                        .as("the child JVM must exit on SIGTERM").isTrue();

                for (ProcessHandle g : tree) {
                    long d2 = System.currentTimeMillis() + 10_000;
                    while (g.isAlive() && System.currentTimeMillis() < d2) {
                        Thread.sleep(100);
                    }
                    assertThat(g.isAlive())
                            .as("pid %s outlived its JVM — the orphan bug", g.pid())
                            .isFalse();
                }
            }
        } finally {
            child.descendants().forEach(ProcessHandle::destroyForcibly);
            child.destroyForcibly();
        }
    }

    /** Waits for the one process this test just added to the live-set. */
    private static Process awaitNewTracked(Set<Process> before) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Set<Process> now = new HashSet<>(CommandExecutor.liveSnapshot());
            now.removeAll(before);
            if (!now.isEmpty()) {
                return now.iterator().next();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("spawn never appeared in the reaper's live-set");
    }

    /**
     * The child JVM for the SIGTERM proof: spawns a shell-with-grandchild
     * through the production CommandExecutor lane WITHOUT any Rack (the
     * IDE-Run-button shape — the lane the Rack reaper never covered),
     * announces readiness, then sleeps until the TERM arrives.
     */
    public static final class ServeMain {
        public static void main(String[] args) throws Exception {
            CommandExecutor.run("reaper-e2e", new File("."), Map.of(),
                    List.of("sh", "-c", "sleep 60 & wait"), l -> { }, c -> { });
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && ProcessHandle.current().descendants().count() < 2) {
                Thread.sleep(100);
            }
            System.out.println("REAPER-E2E-READY");
            System.out.flush();
            Thread.sleep(120_000);
        }
    }
}
