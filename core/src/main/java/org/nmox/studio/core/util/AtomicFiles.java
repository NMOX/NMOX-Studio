package org.nmox.studio.core.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Whole-file text writes that are atomic at the filesystem level.
 *
 * {@code Files.writeString} truncates the target and then writes, so any
 * concurrent reader — our own mtime pollers (FilePulse,
 * ArtifactPulse, FileWatcher), an external tool, or the same studio in
 * another IDE instance — can observe an empty or half-written file. Worse,
 * a poll landing between the truncate and the SelfWriteTracker stamp reads
 * a torn file AND classifies it as a foreign edit, triggering a reload of
 * garbage. Writing to a sibling temp file and renaming it into place makes
 * the swap a single directory operation: readers see the old bytes or the
 * new bytes, never a mixture.
 *
 * The temp file lives in the target's own directory (rename is only atomic
 * within a filesystem), and the fallback for filesystems without atomic
 * move keeps the plain-move semantics rather than failing the save.
 *
 * <p><b>A save killed half-way leaves nothing behind (3.4).</b> Five
 * {@code kill -9}s during a save left five {@code .nmoxrack.json<random>.tmp}
 * files (0–16 MB, mode 0600) that nothing ever deleted: git listed them as
 * untracked (a {@code git add .} would commit them) and REFLEX with FILTER
 * all fired on each. A temp is now named {@code .nmox-save-<target>.<n>.tmp}
 * — hidden, and recognisably the IDE's own ({@link IdeWorkspaceFiles}) —
 * and each write first sweeps the stale temps of the SAME target: only
 * names matching this class's exact pattern (the old spelling included),
 * only in that directory, only regular files, only older than a minute, so
 * a save running concurrently in another instance never loses its temp.
 */
public final class AtomicFiles {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(AtomicFiles.class.getName());

    /** Every temp this class makes starts with this; {@link IdeWorkspaceFiles} knows it. */
    static final String TEMP_PREFIX = ".nmox-save-";
    static final String TEMP_SUFFIX = ".tmp";
    /** A temp younger than this may belong to a save in progress elsewhere. */
    static final long STALE_MILLIS = 60_000;
    /** A directory listing the sweep will read at most: a sweep is housekeeping, never a scan. */
    static final int SWEEP_LIMIT = 10_000;
    /** The target's name as embedded in a temp name, clipped so the temp name stays a legal name. */
    private static final int EMBED_MAX = 180;

    private AtomicFiles() {
    }

    /** The target's name as its temps carry it. */
    static String embedded(String targetName) {
        return targetName.length() > EMBED_MAX ? targetName.substring(0, EMBED_MAX) : targetName;
    }

    /** Whether {@code name} is one of this class's temps for {@code targetName}, in either spelling. */
    static boolean isTempOf(String name, String targetName) {
        String current = TEMP_PREFIX + embedded(targetName) + ".";
        if (name.startsWith(current) && name.endsWith(TEMP_SUFFIX)) {
            return digits(name, current.length(), name.length() - TEMP_SUFFIX.length());
        }
        // before 3.4: createTempFile(dir, "<target>", ".tmp")
        if (name.startsWith(targetName) && name.endsWith(TEMP_SUFFIX)) {
            return digits(name, targetName.length(), name.length() - TEMP_SUFFIX.length());
        }
        return false;
    }

    private static boolean digits(String s, int from, int to) {
        if (to <= from) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deletes the stale temps a killed save of {@code target} left behind.
     * Best effort and bounded: a sweep that cannot finish never stops the
     * save that asked for it.
     */
    static int sweepStaleTemps(Path target) {
        Path dir = target.toAbsolutePath().getParent();
        if (dir == null) {
            return 0;
        }
        String targetName = target.getFileName().toString();
        long cutoff = System.currentTimeMillis() - STALE_MILLIS;
        int deleted = 0;
        int seen = 0;
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path p : entries) {
                if (++seen > SWEEP_LIMIT) {
                    break;
                }
                String name = p.getFileName().toString();
                if (!isTempOf(name, targetName)) {
                    continue;
                }
                java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(p,
                        java.nio.file.attribute.BasicFileAttributes.class,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS);
                if (attrs.isRegularFile() && attrs.lastModifiedTime().toMillis() < cutoff
                        && Files.deleteIfExists(p)) {
                    deleted++;
                }
            }
        } catch (IOException | RuntimeException bestEffort) {
            LOG.log(java.util.logging.Level.FINE, "sweeping stale temps beside " + target, bestEffort);
        }
        return deleted;
    }

    /**
     * Writes UTF-8 text so that readers never observe a partial file.
     *
     * <p>Permissions: {@code createTempFile} makes the temp owner-only
     * (0600) and the move carries that onto the target, silently narrowing
     * a previously shared file — so an EXISTING target's POSIX permissions
     * are captured first and re-applied after the move (ledger 57). A file
     * created fresh by this method stays 0600: tighter is the safe default
     * and consistent with the keyring-only secrets posture. Non-POSIX
     * filesystems (Windows) skip both steps.
     */
    public static void writeString(Path target, String content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        java.util.Set<java.nio.file.attribute.PosixFilePermission> keep = null;
        try {
            if (Files.exists(target)) {
                keep = Files.getPosixFilePermissions(target);
            }
        } catch (UnsupportedOperationException | IOException nonPosixOrGone) {
            keep = null; // Windows, or the target vanished — nothing to preserve
        }
        sweepStaleTemps(target);
        Path tmp = Files.createTempFile(dir,
                TEMP_PREFIX + embedded(target.getFileName().toString()) + ".", TEMP_SUFFIX);
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException fsCannotAtomicMove) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (keep != null) {
                try {
                    Files.setPosixFilePermissions(target, keep);
                } catch (UnsupportedOperationException | IOException bestEffort) {
                    // the write itself succeeded; perms stay at the temp's 0600
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
