package org.nmox.studio.rack.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.nmox.studio.core.spi.LiveRuns;
import org.openide.util.NbBundle.Messages;

/**
 * The processes a run started that outlive it (3.4).
 *
 * <p>{@code sh -c "server & echo started"}: the root exits at once, the
 * JDK closes the run's output pipe at that exit, the run reports its exit
 * and leaves every registry — and the server it started, reparented to
 * init, outlived Stop, quitting the IDE and the JVM-exit reaper (measured:
 * a {@code sleep} still alive after all three). A process the root started
 * is invisible once the root is gone ({@code descendants()} walks parent
 * links, and the orphan's parent is now init), so the family has to be
 * seen WHILE the root lives: a sampler records every descendant it meets,
 * densely right after the spawn (a shell that forks and exits takes a
 * millisecond or two) and then every {@value #SAMPLE_MILLIS} ms.
 *
 * <p><b>The rule, including for real daemons.</b> Whatever a run starts,
 * the IDE accounts for. The run itself ends when its root exits, so a
 * chain's DONE, a device's verdict and a build that leaves a Gradle daemon
 * behind keep their meaning. Its survivors get a row of their own in the
 * ■'s registry — "still running in the background (pid …)" — which the ■,
 * the Workbench and ⌘I list and Stop ends; the Output tab says so; and
 * quitting the IDE ends them with everything else it started (a Gradle or
 * Kotlin daemon the IDE started is restarted by the next build; one the
 * user started from a terminal is not the IDE's and is never touched). A
 * program that must outlive the IDE belongs outside it: a terminal, a
 * service, a container. A descendant the sampler never saw — forked and
 * orphaned between two samples — cannot be found afterwards, and nothing
 * pretends otherwise.
 */
@Messages({
    "# {0} = the run's tab name, {1} = process ids",
    "RunSurvivors_row={0} \u2014 still running in the background (pid {1})",
    "# printed in the run's Output tab when the run ends and something it started has not",
    "RunSurvivors_line={0,choice,1#[a process this run started is still running in the background: pid {1} \u2014 the \u25a0 stops it]|1<[{0} processes this run started are still running in the background: pids {1} \u2014 the \u25a0 stops them]}"
})
final class RunSurvivors {

    /** The steady sampling period once the dense start is over. */
    static final long SAMPLE_MILLIS = 50;
    /** How long a survivor may take to exit on its own before it counts as left running. */
    static final long SETTLE_MILLIS = 1_000;
    /** Bound on the tracked family: a fork bomb must not grow this set. */
    static final int MAX_TRACKED = 256;

    /**
     * Survivors the JVM-exit reaper ends with every live run (they are not
     * in {@link CommandExecutor}'s process set: their root has exited).
     */
    private static final Set<ProcessHandle> ORPHANS = ConcurrentHashMap.newKeySet();

    private final Process root;
    private final Set<ProcessHandle> seen = ConcurrentHashMap.newKeySet();

    RunSurvivors(Process root) {
        this.root = root;
    }

    /** Samples the root's family until the root exits; run on a daemon thread. */
    void sample() {
        long denseUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);
        try {
            while (root.isAlive()) {
                sampleNow();
                if (System.nanoTime() < denseUntil) {
                    Thread.onSpinWait();
                    Thread.sleep(1);
                } else {
                    Thread.sleep(SAMPLE_MILLIS);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** One look at the family: every descendant alive right now joins what was seen. */
    void sampleNow() {
        root.descendants().forEach(h -> {
            if (seen.size() < MAX_TRACKED) {
                seen.add(h);
            }
        });
    }

    /** Everything seen that is still alive: the tree a Stop must end. */
    List<ProcessHandle> alive() {
        List<ProcessHandle> out = new ArrayList<>();
        for (ProcessHandle h : seen) {
            if (h.isAlive()) {
                out.add(h);
            }
        }
        return out;
    }

    /**
     * The root exited on its own. Survivors get a short settle (a child
     * finishing a write a few milliseconds after its parent is not a
     * background process), then a row of their own and a line in the
     * Output tab. Returns the line to print, or null when nothing survived.
     */
    String rootExited(String tabName) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SETTLE_MILLIS);
        List<ProcessHandle> left = alive();
        for (ProcessHandle h : left) {
            long rest = deadline - System.nanoTime();
            if (rest <= 0) {
                break;
            }
            try {
                h.onExit().get(rest, TimeUnit.NANOSECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException stillUp) {
                // counted below
            }
        }
        List<ProcessHandle> survivors = alive();
        if (survivors.isEmpty()) {
            return null;
        }
        StringBuilder pids = new StringBuilder();
        for (ProcessHandle h : survivors) {
            if (pids.length() > 0) {
                pids.append(", ");
            }
            pids.append(h.pid());
            ORPHANS.add(h);
            h.onExit().thenRun(() -> ORPHANS.remove(h));
        }
        String id = "background:" + tabName + "#" + root.pid();
        LiveRuns.add(new LiveRuns.Run(id, Bundle.RunSurvivors_row(tabName, pids.toString()),
                () -> endAll(survivors)));
        CompletableFuture.allOf(survivors.stream().map(ProcessHandle::onExit)
                .toArray(CompletableFuture[]::new)).thenRun(() -> LiveRuns.remove(id));
        return Bundle.RunSurvivors_line(survivors.size(), pids.toString());
    }

    /** TERM every survivor, one grace, then KILL — off the caller's thread. */
    static void endAll(List<ProcessHandle> survivors) {
        survivors.forEach(ProcessHandle::destroy);
        org.nmox.studio.core.util.Threads.startDaemon(() -> {
            long deadline = System.nanoTime() + CommandExecutor.KILL_GRACE_NANOS;
            for (ProcessHandle h : survivors) {
                long rest = deadline - System.nanoTime();
                if (rest > 0 && h.isAlive()) {
                    try {
                        h.onExit().get(rest, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (java.util.concurrent.ExecutionException
                            | java.util.concurrent.TimeoutException stillUp) {
                        // forced below
                    }
                }
                if (h.isAlive()) {
                    h.destroyForcibly();
                }
            }
        }, "nmox-rack-survivors-kill");
    }

    /** The JVM-exit reaper's view of survivors; it TERMs and KILLs them with the live runs. */
    static List<ProcessHandle> orphans() {
        return new ArrayList<>(ORPHANS);
    }
}
