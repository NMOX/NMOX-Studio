package org.nmox.studio.editor.snippets;

import java.util.Set;

/**
 * VS Code's snippet variables ({@code TM_FILENAME}, {@code CURRENT_YEAR},
 * {@code UUID}, …), answered from a {@link SnippetContext}.
 *
 * <p>Three outcomes, as in VS Code: a value; null for a variable that is
 * known but not set (no selection, a document with no file, a language
 * with no block comment), where the variable's default or nothing is
 * inserted; and "not known" ({@link #known}), where VS Code inserts the
 * NAME as a placeholder to be typed over.
 *
 * <p><b>The names of days and months are English, whatever language the
 * IDE speaks.</b> That is the {@code core.util.Clocks} split applied to
 * a third reader: the text lands in a source file, usually a header
 * comment, which a team reads in one language and which must not differ
 * between the author's desk and a colleague's (VS Code localizes these
 * with its display language, which in practice is English on a team's
 * machines). The digits are ASCII for the same reason: the numbers are
 * built here, never through a locale's formatter.
 */
final class SnippetVariables {

    private static final Set<String> KNOWN = Set.of(
            "TM_SELECTED_TEXT", "SELECTION", "TM_CURRENT_LINE", "TM_CURRENT_WORD",
            "TM_LINE_INDEX", "TM_LINE_NUMBER", "TM_FILENAME", "TM_FILENAME_BASE",
            "TM_DIRECTORY", "TM_DIRECTORY_BASE", "TM_FILEPATH", "RELATIVE_FILEPATH",
            "CLIPBOARD", "WORKSPACE_NAME", "WORKSPACE_FOLDER", "CURSOR_INDEX", "CURSOR_NUMBER",
            "CURRENT_YEAR", "CURRENT_YEAR_SHORT", "CURRENT_MONTH", "CURRENT_MONTH_NAME",
            "CURRENT_MONTH_NAME_SHORT", "CURRENT_DATE", "CURRENT_DAY_NAME", "CURRENT_DAY_NAME_SHORT",
            "CURRENT_HOUR", "CURRENT_MINUTE", "CURRENT_SECOND", "CURRENT_SECONDS_UNIX",
            "CURRENT_TIMEZONE_OFFSET", "RANDOM", "RANDOM_HEX", "UUID",
            "BLOCK_COMMENT_START", "BLOCK_COMMENT_END", "LINE_COMMENT");

    private static final String[] DAYS = {
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private static final String[] MONTHS = {
        "January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December"};

    /** A clipboard longer than this is not pasted into a snippet. */
    static final int MAX_CLIPBOARD_CHARS = 64 * 1024;

    private SnippetVariables() {
    }

    /** Whether {@code name} is one of VS Code's variables. */
    static boolean known(String name) {
        return KNOWN.contains(name);
    }

    /** The value of {@code name}, or null when it is not set (or not known). */
    static String resolve(String name, SnippetContext c) {
        return switch (name) {
            case "TM_SELECTED_TEXT", "SELECTION" -> empty(c.selectedText());
            case "TM_CURRENT_LINE" -> c.currentLine();
            case "TM_CURRENT_WORD" -> empty(c.currentWord());
            case "TM_LINE_INDEX" -> Integer.toString(c.lineIndex());
            case "TM_LINE_NUMBER" -> Integer.toString(c.lineIndex() + 1);
            case "CURSOR_INDEX" -> "0";
            case "CURSOR_NUMBER" -> "1";
            case "TM_FILENAME" -> c.filePath() == null ? null : baseName(c.filePath());
            case "TM_FILENAME_BASE" -> c.filePath() == null ? null : withoutExtension(baseName(c.filePath()));
            case "TM_DIRECTORY" -> c.filePath() == null ? null : dirName(c.filePath());
            case "TM_DIRECTORY_BASE" -> c.filePath() == null ? null : baseName(dirName(c.filePath()));
            case "TM_FILEPATH" -> c.filePath();
            case "RELATIVE_FILEPATH" -> c.filePath() == null ? null : relative(c.filePath(), c.workspaceFolder());
            case "WORKSPACE_NAME" -> c.workspaceFolder() == null ? null : baseName(c.workspaceFolder());
            case "WORKSPACE_FOLDER" -> c.workspaceFolder();
            case "CLIPBOARD" -> clipboard(c);
            case "CURRENT_YEAR" -> Integer.toString(c.now().getYear());
            case "CURRENT_YEAR_SHORT" -> two(Math.floorMod(c.now().getYear(), 100));
            case "CURRENT_MONTH" -> two(c.now().getMonthValue());
            case "CURRENT_MONTH_NAME" -> MONTHS[c.now().getMonthValue() - 1];
            case "CURRENT_MONTH_NAME_SHORT" -> MONTHS[c.now().getMonthValue() - 1].substring(0, 3);
            case "CURRENT_DATE" -> two(c.now().getDayOfMonth());
            case "CURRENT_DAY_NAME" -> DAYS[c.now().getDayOfWeek().getValue() - 1];
            case "CURRENT_DAY_NAME_SHORT" -> DAYS[c.now().getDayOfWeek().getValue() - 1].substring(0, 3);
            case "CURRENT_HOUR" -> two(c.now().getHour());
            case "CURRENT_MINUTE" -> two(c.now().getMinute());
            case "CURRENT_SECOND" -> two(c.now().getSecond());
            case "CURRENT_SECONDS_UNIX" -> Long.toString(c.now().toEpochSecond());
            case "CURRENT_TIMEZONE_OFFSET" -> offset(c.now().getOffset().getTotalSeconds());
            case "RANDOM" -> six(c.random().nextInt(1_000_000), 10);
            case "RANDOM_HEX" -> six(c.random().nextInt(0x1000000), 16);
            case "UUID" -> uuid(c);
            case "LINE_COMMENT" -> c.lineComment();
            case "BLOCK_COMMENT_START" -> c.blockCommentStart();
            case "BLOCK_COMMENT_END" -> c.blockCommentEnd();
            default -> null;
        };
    }

    private static String empty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static String clipboard(SnippetContext c) {
        String text;
        try {
            text = c.clipboard().get();
        } catch (RuntimeException unreadable) {
            // a clipboard another program owns can refuse in a dozen ways; none of them is the snippet's fault
            return null;
        }
        if (text == null || text.isEmpty() || text.length() > MAX_CLIPBOARD_CHARS) {
            return null;
        }
        return text;
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : Integer.toString(n);
    }

    private static String six(int n, int radix) {
        String s = Integer.toString(n, radix);
        return "000000".substring(s.length()) + s;
    }

    /** {@code +02:00}, {@code -05:30}: VS Code's shape. */
    private static String offset(int totalSeconds) {
        int minutes = Math.abs(totalSeconds) / 60;
        return (totalSeconds < 0 ? "-" : "+") + two(minutes / 60) + ":" + two(minutes % 60);
    }

    /** A version-4 UUID from the context's dice, so a test can pin it. */
    private static String uuid(SnippetContext c) {
        long high = (c.random().nextLong() & ~0xF000L) | 0x4000L;
        long low = (c.random().nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new java.util.UUID(high, low).toString();
    }

    private static int lastSeparator(String path) {
        return Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    }

    static String baseName(String path) {
        String p = trimTrailingSeparators(path);
        return p.substring(lastSeparator(p) + 1);
    }

    static String dirName(String path) {
        String p = trimTrailingSeparators(path);
        int cut = lastSeparator(p);
        return cut <= 0 ? (cut == 0 ? p.substring(0, 1) : "") : p.substring(0, cut);
    }

    private static String trimTrailingSeparators(String path) {
        int end = path.length();
        while (end > 1 && (path.charAt(end - 1) == '/' || path.charAt(end - 1) == '\\')) {
            end--;
        }
        return path.substring(0, end);
    }

    /** {@code user.service.ts} as {@code user.service}; a dotfile keeps its whole name, as in VS Code. */
    static String withoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    /** {@code path} as seen from {@code workspace}; the full path when it is not under it. */
    static String relative(String path, String workspace) {
        if (workspace == null || workspace.isEmpty()) {
            return path;
        }
        String root = trimTrailingSeparators(workspace);
        if (path.length() > root.length() + 1 && path.startsWith(root)
                && (path.charAt(root.length()) == '/' || path.charAt(root.length()) == '\\')) {
            return path.substring(root.length() + 1);
        }
        return path;
    }
}
