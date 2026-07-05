package org.nmox.studio.rack.engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Watches a project tree for source changes by polling modification
 * times. Polling (rather than WatchService) is deliberate: it behaves
 * identically on every platform - macOS WatchService can lag multiple
 * seconds and miss bursts - and the scan is cheap because the heavy
 * directories (node_modules, .git, dist...) are skipped outright.
 */
public final class FileWatcher {

    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", "target", "out",
            ".next", ".nuxt", ".svelte-kit", "coverage", ".cache", ".idea");
    private static final int MAX_DEPTH = 12;
    private static final int MAX_FILES = 50_000;

    private final File root;
    private final long intervalMs;
    private final Set<String> extensions;   // lower-case, no dot; null = all files
    private final Set<String> filenames;    // exact names; null = extension rule
    private final Consumer<List<Path>> onChange;

    private volatile boolean running;
    private Thread thread;

    /**
     * @param root       directory to watch
     * @param intervalMs poll interval
     * @param extensions file extensions to track (null = every file)
     * @param onChange   called on the watcher thread with changed paths
     */
    public FileWatcher(File root, long intervalMs, Set<String> extensions, Consumer<List<Path>> onChange) {
        this(root, intervalMs, extensions, null, onChange);
    }

    private FileWatcher(File root, long intervalMs, Set<String> extensions,
            Set<String> filenames, Consumer<List<Path>> onChange) {
        this.root = root;
        this.intervalMs = Math.max(200, intervalMs);
        this.extensions = extensions;
        this.filenames = filenames;
        this.onChange = onChange;
    }

    /**
     * A watcher matching files by EXACT name rather than extension —
     * manifest names like {@code package.json} or {@code .env}, where an
     * extension rule would sweep in every .json in the tree.
     */
    public static FileWatcher forFilenames(File root, long intervalMs,
            Set<String> filenames, Consumer<List<Path>> onChange) {
        return new FileWatcher(root, intervalMs, null, filenames, onChange);
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(() -> {
            Map<Path, Long> baseline = scan();
            while (running) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ex) {
                    return;
                }
                if (!running) {
                    return;
                }
                Map<Path, Long> current = scan();
                List<Path> changed = diff(baseline, current);
                baseline = current;
                if (!changed.isEmpty() && running) {
                    onChange.accept(changed);
                }
            }
        }, "nmox-rack-watcher");
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

    public boolean isRunning() {
        return running;
    }

    private Map<Path, Long> scan() {
        Map<Path, Long> result = new HashMap<>();
        if (!root.isDirectory()) {
            return result;
        }
        try {
            Files.walkFileTree(root.toPath(), Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (SKIP_DIRS.contains(name) || name.startsWith(".") && !dir.equals(root.toPath())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (result.size() >= MAX_FILES) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (matches(file)) {
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
            // partial scan is fine; next poll catches up
        }
        return result;
    }

    private boolean matches(Path file) {
        String name = file.getFileName().toString();
        if (filenames != null) {
            return filenames.contains(name);
        }
        if (extensions == null) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && extensions.contains(name.substring(dot + 1).toLowerCase());
    }

    private static List<Path> diff(Map<Path, Long> before, Map<Path, Long> after) {
        List<Path> changed = new ArrayList<>();
        for (Map.Entry<Path, Long> e : after.entrySet()) {
            Long old = before.get(e.getKey());
            if (old == null || !old.equals(e.getValue())) {
                changed.add(e.getKey());     // new or modified
            }
        }
        for (Path p : before.keySet()) {
            if (!after.containsKey(p)) {
                changed.add(p);              // deleted
            }
        }
        return changed;
    }
}
