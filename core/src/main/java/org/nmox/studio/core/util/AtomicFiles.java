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
 * concurrent reader — our own mtime pollers (WorkspaceFilePulse,
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
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /** Writes UTF-8 text so that readers never observe a partial file. */
    public static void writeString(Path target, String content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Path tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException fsCannotAtomicMove) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
