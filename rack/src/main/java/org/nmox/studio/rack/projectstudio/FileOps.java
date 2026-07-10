package org.nmox.studio.rack.projectstudio;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 * File CRUD for the Project Studio tree, with the guard rails a tree UI
 * needs: name validation, no clobbering, and deletion that prefers the
 * system Trash over rm -rf.
 *
 * Operations go through the platform filesystem (FileObject/DataObject)
 * so open editor buffers follow: a raw java.nio delete/rename leaves an
 * editor holding a stale buffer over a path that no longer exists. Plain
 * nio remains as the fallback for paths masterfs cannot see (no
 * FileObject outside the platform, e.g. plain unit tests without it).
 */
public final class FileOps {

    private FileOps() {
    }

    /** Creates an empty file under {@code parent} and returns it. */
    public static File createFile(File parent, String name) throws IOException {
        File target = validateNew(parent, name);
        FileObject dir = toFileObject(parent);
        if (dir != null) {
            dir.createData(name);
        } else {
            Files.createFile(target.toPath());
        }
        return target;
    }

    /** Creates a directory under {@code parent} and returns it. */
    public static File createDirectory(File parent, String name) throws IOException {
        File target = validateNew(parent, name);
        FileObject dir = toFileObject(parent);
        if (dir != null) {
            dir.createFolder(name);
        } else {
            Files.createDirectory(target.toPath());
        }
        return target;
    }

    /** Renames in place; returns the renamed file. */
    public static File rename(File file, String newName) throws IOException {
        File target = validateNew(file.getParentFile(), newName);
        FileObject fo = toFileObject(file);
        if (fo == null) {
            Files.move(file.toPath(), target.toPath());
            return target;
        }
        // materialize the DataObject so an open editor observes the rename;
        // DataObject.rename itself is loader-dependent (real loaders keep the
        // extension, DefaultDataObject does not), so rename at the FileObject
        // level with an explicit base/ext — the DataObject follows the event
        DataObject.find(fo);
        int dot = newName.lastIndexOf('.');
        String base = (fo.isFolder() || dot <= 0) ? newName : newName.substring(0, dot);
        String ext = (fo.isFolder() || dot <= 0) ? "" : newName.substring(dot + 1);
        FileLock lock = fo.lock();
        try {
            fo.rename(lock, base, ext);
        } finally {
            lock.releaseLock();
        }
        return target;
    }

    /**
     * Deletes a file or directory tree, via the system Trash when the
     * platform supports it (recoverable beats gone).
     */
    public static void delete(File file) throws IOException {
        FileObject fo = toFileObject(file);
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
                    && Desktop.getDesktop().moveToTrash(file)) {
                if (fo != null) {
                    // the Trash move happened behind masterfs' back; refresh so
                    // DataObjects (and their editors) learn the file is gone
                    fo.refresh();
                }
                return;
            }
        } catch (RuntimeException ignored) {
            // headless or Trash unavailable; fall through to hard delete
        }
        if (fo != null) {
            DataObject.find(fo).delete();
            return;
        }
        if (file.isDirectory()) {
            try (Stream<Path> walk = Files.walk(file.toPath())) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        } else {
            Files.delete(file.toPath());
        }
    }

    /** Null when masterfs cannot see the path (then callers fall back to nio). */
    private static FileObject toFileObject(File file) {
        return file == null ? null : FileUtil.toFileObject(FileUtil.normalizeFile(file));
    }

    private static File validateNew(File parent, String name) throws IOException {
        if (parent == null || !parent.isDirectory()) {
            throw new IOException("Not a directory: " + parent);
        }
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")) {
            throw new IOException("Invalid name: \"" + name + "\"");
        }
        File target = new File(parent, name);
        if (target.exists()) {
            throw new IOException("Already exists: " + target.getName());
        }
        return target;
    }
}
