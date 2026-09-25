package org.nmox.studio.ui.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * What {@code nmox --wait} and {@code nmox --diff} ask the IDE for (3.2.0),
 * read from the request file the terminal command writes into a private
 * folder: the files to open (with an optional line), the pairs to compare,
 * and whether the command waits for them to be closed.
 *
 * <p>The format is line-based because a path can hold any character a shell
 * can quote except a newline, and the launchers refuse a name with a
 * newline before writing it:
 * <pre>
 * nmox-request 1
 * wait                 (only when the command waits)
 * open
 * /absolute/path
 * 42                   (or an empty line: no line)
 * diff
 * /absolute/left      (or an empty line: git's /dev/null, an added file)
 * /absolute/right     (or an empty line: a deleted file)
 * </pre>
 * Pure, so every rule is a unit test: {@link #parse} answers a request or
 * throws {@link Refused} with the sentence the terminal prints.
 */
public record EditRequest(boolean waits, List<Item> items) {

    /** The request file's first line: the format and its version. */
    static final String MAGIC = "nmox-request 1";

    /** Enough for any commit, rebase or difftool; a longer file is not a request. */
    static final int MAX_ITEMS = 64;

    /** One thing to show: a file (at a line, or 0 for none), or two files side by side. */
    public sealed interface Item permits Open, Diff {
    }

    /** A file to open, at {@code line} (1-based) or at the top when 0. */
    public record Open(File file, int line) implements Item {
    }

    /**
     * Two files to compare, left and right; one side is null when git names
     * it {@code /dev/null} (an added or a deleted file), never both.
     */
    public record Diff(File left, File right) implements Item {
    }

    /** A request that cannot be honoured, with the sentence that says why. */
    public static final class Refused extends Exception {
        private static final long serialVersionUID = 1L;

        Refused(String why) {
            super(why);
        }
    }

    /**
     * The request {@code text} describes. Every path must be absolute and
     * name an existing regular file: the command made them absolute and
     * checked them, so anything else means the file was written by something
     * else, or the file vanished in between, and either way there is nothing
     * to show.
     */
    public static EditRequest parse(String text) throws Refused {
        String body = text.replace("\r\n", "\n");
        if (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);   // the file's own last newline
        }
        String[] lines = body.split("\n", -1);
        if (lines.length == 0 || !lines[0].equals(MAGIC)) {
            throw new Refused("the request is not one nmox wrote");
        }
        int i = 1;
        boolean waits = false;
        if (i < lines.length && lines[i].equals("wait")) {
            waits = true;
            i++;
        }
        List<Item> items = new ArrayList<>();
        while (i < lines.length) {
            String kind = lines[i++];
            if (items.size() == MAX_ITEMS) {
                throw new Refused("the request names more than " + MAX_ITEMS + " files");
            }
            if (i + 2 > lines.length) {
                throw new Refused("the request ends in the middle of " + kind);
            }
            String a = lines[i++];
            String b = lines[i++];
            switch (kind) {
                case "open" -> items.add(new Open(file(a), line(b)));
                case "diff" -> {
                    if (a.isEmpty() && b.isEmpty()) {
                        throw new Refused("the request compares nothing with nothing");
                    }
                    items.add(new Diff(a.isEmpty() ? null : file(a), b.isEmpty() ? null : file(b)));
                }
                default -> throw new Refused("the request asks for \"" + kind + "\", which nmox never writes");
            }
        }
        if (items.isEmpty()) {
            throw new Refused("the request names no file");
        }
        return new EditRequest(waits, List.copyOf(items));
    }

    private static File file(String path) throws Refused {
        File f = new File(path);
        if (path.isEmpty() || !f.isAbsolute()) {
            throw new Refused("the request names a path that is not absolute: " + path);
        }
        if (!f.isFile()) {
            throw new Refused(path + ": no such file");
        }
        return f;
    }

    private static int line(String s) throws Refused {
        if (s.isEmpty()) {
            return 0;
        }
        if (s.length() > 9 || !s.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new Refused("the request names a line that is not a number: " + s);
        }
        return Integer.parseInt(s);
    }
}
