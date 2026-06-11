package org.nmox.studio.rack.projectstudio;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * File CRUD for the Project Studio tree. Plain java.nio operations
 * with the guard rails a tree UI needs: name validation, no clobbering,
 * and deletion that prefers the system Trash over rm -rf.
 */
public final class FileOps {

    private FileOps() {
    }

    /** Creates an empty file under {@code parent} and returns it. */
    public static File createFile(File parent, String name) throws IOException {
        File target = validateNew(parent, name);
        Files.createFile(target.toPath());
        return target;
    }

    /** Creates a directory under {@code parent} and returns it. */
    public static File createDirectory(File parent, String name) throws IOException {
        File target = validateNew(parent, name);
        Files.createDirectory(target.toPath());
        return target;
    }

    /** Renames in place; returns the renamed file. */
    public static File rename(File file, String newName) throws IOException {
        File target = validateNew(file.getParentFile(), newName);
        Files.move(file.toPath(), target.toPath());
        return target;
    }

    /**
     * Deletes a file or directory tree, via the system Trash when the
     * platform supports it (recoverable beats gone).
     */
    public static void delete(File file) throws IOException {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
                    && Desktop.getDesktop().moveToTrash(file)) {
                return;
            }
        } catch (RuntimeException ignored) {
            // headless or Trash unavailable; fall through to hard delete
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
