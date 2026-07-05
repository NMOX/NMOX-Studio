package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.nmox.studio.rack.engine.Coalescer;
import org.nmox.studio.rack.engine.FileWatcher;

/**
 * The manifest pulse: one watcher over the aimed project that notices
 * when a MANIFEST — the files devices configure themselves from —
 * changes on disk, coalesces the burst (a kit writing ten files is one
 * event, not ten), and hands the batch to its dispatcher. RackService
 * owns exactly one, re-created when the rack re-aims; the dispatcher
 * fans out to {@code Rack.manifestChanged} (router thread) and the
 * studio-facing manifest listeners (off-EDT).
 */
public final class ManifestPulse {

    /**
     * Exact filenames that count as manifests — name match, never
     * extension (an extension rule would sweep every .json in the tree).
     */
    static final Set<String> MANIFEST_NAMES = Set.of(
            "package.json", "package-lock.json", "bower.json",
            "composer.json", "composer.lock",
            "foundry.toml", ".gas-snapshot", ".env",
            "Gruntfile.js", "Gruntfile.coffee",
            "gulpfile.js", "gulpfile.babel.js", "gulpfile.mjs",
            "webpack.config.js", "webpack.config.cjs", "webpack.config.mjs");

    /** Production cadence: poll each second, merge half-second bursts. */
    private static final long POLL_INTERVAL_MS = 1_000;
    private static final long COALESCE_WINDOW_MS = 500;

    private final FileWatcher watcher;
    private final Coalescer<Path> coalescer;

    ManifestPulse(File root, Consumer<List<Path>> dispatch) {
        this(root, POLL_INTERVAL_MS, COALESCE_WINDOW_MS, dispatch);
    }

    /** Test seam: cadence under the test's control. */
    ManifestPulse(File root, long intervalMs, long windowMs, Consumer<List<Path>> dispatch) {
        this.coalescer = new Coalescer<>(windowMs, dispatch);
        this.watcher = FileWatcher.forFilenames(root, intervalMs, MANIFEST_NAMES,
                coalescer::offer);
    }

    void start() {
        watcher.start();
    }

    void stop() {
        watcher.stop();
        coalescer.close();
    }
}
