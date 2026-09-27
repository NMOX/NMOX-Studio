package org.nmox.studio.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Where a studio keeps a copy of a workspace file it could not parse
 * (3.4, question 1).
 *
 * <p>Every studio copies an unparseable {@code .nmox*.json} aside before
 * it starts empty, so its next save cannot destroy the only copy. Until
 * 3.4 that copy was always {@code <name>.bak}, written with
 * {@code REPLACE_EXISTING} — so the SECOND rescue overwrote the first,
 * and a file that went bad twice (a botched merge, then a botched
 * resolution) kept only the later, often emptier, bytes. A kept copy is
 * never overwritten: the first is {@code <name>.bak}, later ones take the
 * first free {@code <name>.2.bak}, {@code <name>.3.bak}, … — still ending
 * in {@code .bak}, so a {@code *.bak} ignore rule covers every one.
 */
public final class KeptCopies {

    /** How many numbered siblings are tried before giving up. */
    static final int MAX_COPIES = 1000;

    private KeptCopies() {
    }

    /**
     * The first name not yet taken beside {@code file}: {@code <name>.bak},
     * then {@code <name>.2.bak}, {@code <name>.3.bak}, …; null when all
     * {@value #MAX_COPIES} are taken.
     */
    public static File nextFree(File file) {
        File dir = file.getParentFile();
        String name = file.getName();
        File first = new File(dir, name + ".bak");
        if (!first.exists()) {
            return first;
        }
        for (int n = 2; n <= MAX_COPIES; n++) {
            File numbered = new File(dir, name + "." + n + ".bak");
            if (!numbered.exists()) {
                return numbered;
            }
        }
        return null;
    }

    /**
     * Copies {@code file} to {@link #nextFree} and returns the copy, never
     * replacing an earlier one.
     *
     * @throws IOException when the copy cannot be made, or every name is taken
     */
    public static File copyAside(File file) throws IOException {
        for (int attempt = 0; attempt < 3; attempt++) {
            File target = nextFree(file);
            if (target == null) {
                throw new IOException("Every kept-copy name beside " + file + " is taken");
            }
            try {
                // no REPLACE_EXISTING: a copy that appeared since nextFree
                // looked is somebody's earlier rescue, and it stays
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                return target;
            } catch (java.nio.file.FileAlreadyExistsException raced) {
                // another rescue took the name between the look and the copy
            }
        }
        throw new IOException("Could not find a free kept-copy name beside " + file);
    }
}
