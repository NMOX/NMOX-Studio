package org.nmox.studio.web3.engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

/**
 * The studio's own 1.5 s poller over the aimed project's compiled
 * artifacts (Foundry {@code out/}, Hardhat {@code artifacts/}) and its
 * workspace file ({@code .nmoxweb3.json}) — so a forge build from the
 * rack, a terminal, or CI refreshes the Contracts tree without a manual
 * Rescan, and a hand-edited workspace file reloads.
 *
 * <p>Deliberately NOT the rack's {@code FileWatcher}: its SKIP_DIRS set
 * prunes any directory named {@code out} (also {@code build},
 * {@code dist}, {@code target}) — including the watch root itself — so
 * Foundry's artifact tree is structurally invisible to it. This poller
 * scans only the two artifact roots, {@code .json} files only,
 * depth- and count-capped.
 *
 * <p>Coalescing is by construction: however many files a build writes,
 * one tick sees one diff and fires one callback. The first tick primes
 * the baseline and fires nothing. {@link #tick()} is synchronous so
 * tests drive it deterministically; {@link #start} merely loops it on a
 * daemon thread. Callbacks arrive on the pulse's own thread — callers
 * marshal to the EDT themselves.
 */
public final class ArtifactPulse {

    /** Both callbacks arrive on the pulse thread, never the EDT. */
    public interface Sink {

        /** Some artifact JSON appeared, changed, or vanished this tick. */
        void artifactsChanged();

        /**
         * The workspace file's stamp changed this tick; {@code mtime}
         * and {@code size} are -1 when the file is gone.
         */
        void workspaceChanged(long mtime, long size);
    }

    public static final long DEFAULT_INTERVAL_MS = 1500;
    /** Foundry nests one dir per source file; 8 levels is generous. */
    private static final int MAX_DEPTH = 8;
    private static final int MAX_FILES = 20_000;
    private static final String[] ARTIFACT_DIRS = {"out", "artifacts"};

    private final File projectDir;
    private final File workspaceFile;
    private final Sink sink;

    private Map<Path, Long> artifactBaseline = Map.of();
    private long lastMtime;
    private long lastSize;
    private boolean primed;

    private volatile boolean running;
    private Thread thread;

    public ArtifactPulse(File projectDir, File workspaceFile, Sink sink) {
        this.projectDir = projectDir;
        this.workspaceFile = workspaceFile;
        this.sink = sink;
    }

    public synchronized void start(long intervalMs) {
        if (running) {
            return;
        }
        running = true;
        long interval = Math.max(200, intervalMs);
        thread = new Thread(() -> {
            while (running) {
                tick();
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException stopped) {
                    return;
                }
            }
        }, "nmox-web3-pulse");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    /**
     * One synchronous poll: diff the artifact snapshot and the workspace
     * file stamp against the previous tick; fire at most one callback
     * each. The first tick only establishes the baseline.
     */
    void tick() {
        Map<Path, Long> current = scanArtifacts(projectDir.toPath());
        long mtime = workspaceFile.isFile() ? workspaceFile.lastModified() : -1;
        long size = workspaceFile.isFile() ? workspaceFile.length() : -1;
        if (!primed) {
            primed = true;
            artifactBaseline = current;
            lastMtime = mtime;
            lastSize = size;
            return;
        }
        boolean artifactsMoved = !current.equals(artifactBaseline);
        artifactBaseline = current;
        boolean workspaceMoved = mtime != lastMtime || size != lastSize;
        lastMtime = mtime;
        lastSize = size;
        if (artifactsMoved) {
            sink.artifactsChanged();
        }
        if (workspaceMoved) {
            sink.workspaceChanged(mtime, size);
        }
    }

    /**
     * Every {@code .json} under {@code out/} and {@code artifacts/}
     * mapped to its mtime — the pure seam the diff runs on. Missing
     * directories contribute nothing; scan errors degrade to a partial
     * snapshot (the next tick catches up).
     */
    public static Map<Path, Long> scanArtifacts(Path projectDir) {
        Map<Path, Long> result = new HashMap<>();
        for (String dirName : ARTIFACT_DIRS) {
            Path root = projectDir.resolve(dirName);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try {
                Files.walkFileTree(root, java.util.Set.of(), MAX_DEPTH,
                        new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (result.size() >= MAX_FILES) {
                            return FileVisitResult.TERMINATE;
                        }
                        if (file.getFileName().toString().endsWith(".json")) {
                            result.put(file, attrs.lastModifiedTime().toMillis());
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {
                // partial snapshot is fine; the next tick catches up
            }
        }
        return result;
    }
}
