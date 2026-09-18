package org.nmox.studio.rack.sharing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.json.JSONObject;
import org.nmox.studio.core.util.AtomicFiles;
import org.nmox.studio.rack.projectstudio.UserPresets;

/**
 * Keeping a rack and letting one go. {@code ~/.nmox/presets.d} has fed the
 * Presets menu since v1.294.0, but the only way in was a file chooser aimed at
 * a hidden directory and there was no way out at all — a creator with no
 * inverse, the organize-gesture finding (v1.263.0–v1.289.0) one surface over.
 *
 * <p>Plain file work: callers run it off the EDT.
 */
public final class MyRacks {

    private MyRacks() {
    }

    /** A rack of that name is already kept: refused by name, never overwritten (the v1.284.0 collision law). */
    public static final class AlreadyKeptException extends IOException {
        private final transient File existing;

        AlreadyKeptException(File existing) {
            super(existing.getName());
            this.existing = existing;
        }

        public File existing() {
            return existing;
        }
    }

    /** Keeps {@code shared} under a filename made from {@code name}; returns the file written. */
    public static File keep(JSONObject shared, String name) throws IOException {
        return keepIn(UserPresets.dropInDir(), shared, name);
    }

    static File keepIn(File dir, JSONObject shared, String name) throws IOException {
        Files.createDirectories(dir.toPath());
        File target = new File(dir, ShareCards.fileStem(name) + ".nmoxrack.json");
        if (target.exists()) {
            throw new AlreadyKeptException(target);
        }
        AtomicFiles.writeString(target.toPath(), shared.toString(2));
        return target;
    }

    /**
     * Removes a kept rack. Only a regular {@code .json} file DIRECTLY inside the
     * drop-in directory goes — compared on real paths, so neither a {@code ..}
     * nor a symlink planted there can aim this at anything else (the
     * discard-a-learning-space guard, v1.289.0).
     */
    public static void remove(File file) throws IOException {
        removeFrom(UserPresets.dropInDir(), file);
    }

    static void removeFrom(File dir, File file) throws IOException {
        if (file == null || !file.getName().endsWith(".json")) {
            throw new IOException("not a kept rack: " + (file == null ? "null" : file.getName()));
        }
        if (Files.isSymbolicLink(file.toPath()) || !Files.isRegularFile(file.toPath())) {
            throw new IOException("not a kept rack: " + file.getName());
        }
        File realDir = dir.getCanonicalFile();
        File realParent = file.getCanonicalFile().getParentFile();
        if (!realDir.equals(realParent)) {
            throw new IOException("not inside " + dir.getName() + ": " + file.getName());
        }
        Files.delete(file.toPath());
    }
}
