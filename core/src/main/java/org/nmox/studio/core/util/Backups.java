package org.nmox.studio.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Where a rescued copy of a studio file goes (3.4, question 1).
 *
 * <p>Every studio keeps a file it could not parse as {@code <name>.bak}
 * before it falls back to an empty workspace (the v1.39.0 law), and until
 * 3.4 each of them wrote that copy with {@code REPLACE_EXISTING}: a second
 * rescue — the next broken merge, the next hand edit — silently replaced
 * the first, so the older bytes were lost exactly as the law exists to
 * prevent. The first rescue is still {@code <name>.bak}, the name every
 * status line and document already speaks; a later one takes the next free
 * {@code <name>.bak.1}, {@code <name>.bak.2}, …, and nothing that is
 * already there is ever overwritten.
 */
public final class Backups {

    /** How far the numbering looks before it gives up and reuses the last slot. */
    static final int MAX_NUMBERED = 999;

    private Backups() {
    }

    /**
     * A sibling of {@code file} that does not exist yet: {@code <name>.bak},
     * else the first free {@code <name>.bak.N}. After {@value #MAX_NUMBERED}
     * rescues the last slot is returned — a directory holding a thousand
     * rescues of one file has stopped being a place anyone reads, and a
     * bound beats an unbounded probe of a hostile directory.
     */
    public static Path freeSibling(Path file) {
        String name = file.getFileName().toString() + ".bak";
        Path candidate = file.resolveSibling(name);
        for (int n = 1; exists(candidate) && n <= MAX_NUMBERED; n++) {
            candidate = file.resolveSibling(name + "." + n);
        }
        return candidate;
    }

    /**
     * Keeps {@code bytes} — the content of {@code file} a studio could not
     * parse — as a rescue beside it, and returns where they are. When an
     * earlier rescue already holds exactly these bytes that rescue is the
     * answer and nothing is written: a studio re-reads the same broken file
     * on every re-aim, tab show and search, and a numbered copy per read
     * would bury the one that matters. Otherwise the bytes go to
     * {@link #freeSibling} with {@code CREATE_NEW}, so a name that appeared
     * in between is refused rather than overwritten.
     *
     * @throws IOException when no copy could be written — the caller must
     *     then not write over {@code file}, because it is the only copy
     */
    public static Path keep(Path file, byte[] bytes) throws IOException {
        String name = file.getFileName().toString() + ".bak";
        Path candidate = file.resolveSibling(name);
        for (int n = 1; exists(candidate) && n <= MAX_NUMBERED + 1; n++) {
            if (sameBytes(candidate, bytes)) {
                return candidate;
            }
            candidate = file.resolveSibling(name + "." + n);
        }
        Files.write(candidate, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return candidate;
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

    private static boolean exists(Path p) {
        // a dangling link is still a name something wrote: never reuse it
        return Files.exists(p, LinkOption.NOFOLLOW_LINKS);
    }
}
