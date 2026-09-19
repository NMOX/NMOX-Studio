package org.nmox.studio.rack.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * An empty directory for a rack nobody will use — the community-rack
 * judge, the import dry run, the preset builder. A fresh {@link Rack}
 * aims at {@code user.home}, and devices that read the project on
 * attach (BLACKBOX's changed-since scan, REFLEX's watcher baseline)
 * would then walk the user's whole home directory just to answer a
 * question about a patch.
 *
 * <p>The healing rule is the reason this is one home. Each caller kept
 * its own cached {@code scratchDir}, and two of the three re-created
 * the directory when it had vanished while the third tested only for
 * null — so when the OS reaped its temp directory mid-session (macOS
 * sweeps {@code /var/folders} under disk pressure) that one went on
 * handing out a dead path while its siblings healed. They agreed about
 * everything except the thing that mattered, which is the v2.131.0 law
 * exactly: the defect is the second home, not the disagreement.
 */
public final class ScratchDirs {

    private ScratchDirs() {
    }

    /**
     * A cached empty directory under {@code holder}, created on first
     * use and RE-created whenever it is no longer a directory. The
     * holder is the caller's own one-element cache, so each caller keeps
     * its own named temp directory and only the rule is shared.
     */
    public static File cached(java.util.concurrent.atomic.AtomicReference<File> holder,
            String prefix) {
        File dir = holder.get();
        // a cached path that no longer names a directory is as useless as
        // no path at all: check what it IS, never just that we have one
        if (dir == null || !dir.isDirectory()) {
            dir = create(prefix);
            holder.set(dir);
        }
        return dir;
    }

    /** A fresh temp directory, falling back to the temp root when one cannot be made. */
    static File create(String prefix) {
        try {
            File dir = Files.createTempDirectory(prefix).toFile();
            dir.deleteOnExit();
            return dir;
        } catch (IOException ex) {
            return new File(System.getProperty("java.io.tmpdir"));
        }
    }
}
