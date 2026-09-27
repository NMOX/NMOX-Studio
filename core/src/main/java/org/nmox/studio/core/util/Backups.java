package org.nmox.studio.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Where a rescued copy of a studio file goes (3.4, question 1): the one
 * home for it, in every studio.
 *
 * <p>Every studio keeps a file it could not parse as {@code <name>.bak}
 * before it falls back (the v1.39.0 law), and until 3.4 each of them wrote
 * that copy with {@code REPLACE_EXISTING}: a second rescue — the next
 * broken merge, the next hand edit — silently replaced the first, so the
 * older bytes were lost exactly as the law exists to prevent. The first
 * rescue is still {@code <name>.bak}, the name every status line and
 * document already speaks; a later one takes the first free
 * {@code <name>.2.bak}, {@code <name>.3.bak}, … — still ending in
 * {@code .bak}, so one {@code *.bak} ignore rule covers every copy, where a
 * {@code .bak.1} suffix would have slipped past it into {@code git add .}.
 * Nothing already there is ever overwritten.
 *
 * <p>A rescue that already holds exactly the bytes being kept is the
 * answer, and nothing is written: a studio re-reads the same broken file
 * on every re-aim, tab show and search, and a numbered copy per read would
 * bury the one that matters.
 *
 * <p>(3.4 was built by two builders at once, and each wrote this class
 * under its own name; the fold kept this one and its callers all use it.)
 */
public final class Backups {

    /** How far the numbering looks before it gives up. */
    static final int MAX_NUMBERED = 1000;

    private Backups() {
    }

    /** The {@code n}th rescue name beside {@code file}: {@code <name>.bak}, then {@code <name>.2.bak}, … */
    static Path sibling(Path file, int n) {
        String name = file.getFileName().toString();
        return file.resolveSibling(n <= 1 ? name + ".bak" : name + "." + n + ".bak");
    }

    /**
     * A sibling of {@code file} that does not exist yet: {@code <name>.bak},
     * else the first free {@code <name>.N.bak}; null when all
     * {@value #MAX_NUMBERED} are taken — a bound beats an unbounded probe of
     * a hostile directory.
     */
    public static Path freeSibling(Path file) {
        for (int n = 1; n <= MAX_NUMBERED; n++) {
            Path candidate = sibling(file, n);
            if (!exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Keeps {@code bytes} — the content of {@code file} a studio could not
     * parse — as a rescue beside it, and returns where they are: an earlier
     * rescue holding exactly these bytes, else a new copy at
     * {@link #freeSibling} written with {@code CREATE_NEW}, so a name that
     * appeared in between is refused rather than overwritten.
     *
     * @throws IOException when no copy could be written — the caller must
     *     then not write over {@code file}, because it is the only copy
     */
    public static Path keep(Path file, byte[] bytes) throws IOException {
        Path same = existingCopy(file, candidate -> sameBytes(candidate, bytes));
        if (same != null) {
            return same;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            Path target = freeSibling(file);
            if (target == null) {
                throw new IOException("Every rescue name beside " + file + " is taken");
            }
            try {
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return target;
            } catch (FileAlreadyExistsException raced) {
                // another rescue took the name between the look and the write
            }
        }
        throw new IOException("Could not find a free rescue name beside " + file);
    }

    /**
     * Copies {@code file} aside the same way, for callers that have not read
     * it into memory: the comparison with an earlier rescue streams
     * ({@link Files#mismatch}), and the copy never replaces.
     *
     * @throws IOException when no copy could be made — as for {@link #keep}
     */
    public static File copyAside(File file) throws IOException {
        Path source = file.toPath();
        Path same = existingCopy(source, candidate -> sameFile(source, candidate));
        if (same != null) {
            return same.toFile();
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            Path target = freeSibling(source);
            if (target == null) {
                throw new IOException("Every rescue name beside " + file + " is taken");
            }
            try {
                // no REPLACE_EXISTING: a copy that appeared since freeSibling
                // looked is somebody's earlier rescue, and it stays
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                return target.toFile();
            } catch (FileAlreadyExistsException raced) {
                // another rescue took the name between the look and the copy
            }
        }
        throw new IOException("Could not find a free rescue name beside " + file);
    }

    private interface Same {
        boolean test(Path candidate);
    }

    /** The first existing rescue for which {@code same} holds; the walk stops at the first gap. */
    private static Path existingCopy(Path file, Same same) {
        for (int n = 1; n <= MAX_NUMBERED; n++) {
            Path candidate = sibling(file, n);
            if (!exists(candidate)) {
                return null;
            }
            if (same.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean sameBytes(Path candidate, byte[] bytes) {
        try {
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(candidate) != bytes.length) {
                return false;
            }
            // bounded by what we already hold in memory, plus one byte to
            // see a file that grew between the size check and the read
            try (java.io.InputStream in = Files.newInputStream(candidate)) {
                return Arrays.equals(in.readNBytes(bytes.length + 1), bytes);
            }
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static boolean sameFile(Path source, Path candidate) {
        try {
            return Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(candidate) == Files.size(source)
                    && Files.mismatch(source, candidate) == -1L;
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static boolean exists(Path p) {
        // a dangling link is still a name something wrote: never reuse it
        return Files.exists(p, LinkOption.NOFOLLOW_LINKS);
    }
}
