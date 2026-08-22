package org.nmox.studio.tools.npm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The caret-to-script rule for package.json (v2.33.0, granting the
 * v2.31.0 recorded wish): right-click a line inside the {@code
 * "scripts"} object and the product knows which script you mean. Pure
 * — text + caret in, script name out — so the boundary rules are unit
 * tests: outside the scripts object is null (a dependency named like a
 * script must never run), and a caret on the {@code "scripts"} key
 * itself is null (no script chosen).
 */
public final class NpmScripts {

    private NpmScripts() {
    }

    private static final Pattern SCRIPT_LINE = Pattern.compile(
            "\"((?:[^\"\\\\]|\\\\.)+)\"\\s*:");

    /**
     * The script name on the caret's line, when that line is a
     * {@code "name": "command"} entry INSIDE the top-level
     * {@code "scripts"} object; null anywhere else.
     */
    public static String scriptAt(String text, int caret) {
        if (text == null || caret < 0 || caret > text.length()) {
            return null;
        }
        int scriptsKey = indexOfScriptsKey(text);
        if (scriptsKey < 0) {
            return null;
        }
        int open = text.indexOf('{', scriptsKey);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        int close = -1;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    close = i;
                    break;
                }
            }
        }
        if (close < 0 || caret <= open || caret >= close) {
            return null;              // outside the scripts object
        }
        int lineStart = text.lastIndexOf('\n', caret - 1) + 1;
        int lineEnd = text.indexOf('\n', caret);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        Matcher m = SCRIPT_LINE.matcher(text.substring(lineStart, lineEnd));
        return m.find() ? m.group(1) : null;
    }

    /** The top-level {@code "scripts"} key's index, or -1. */
    private static int indexOfScriptsKey(String text) {
        Matcher m = Pattern.compile("\"scripts\"\\s*:").matcher(text);
        while (m.find()) {
            // top-level = depth 1 at the key (inside the root object only)
            int depth = 0;
            boolean inString = false;
            for (int i = 0; i < m.start(); i++) {
                char c = text.charAt(i);
                if (inString) {
                    if (c == '\\') {
                        i++;
                    } else if (c == '"') {
                        inString = false;
                    }
                } else if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
            if (depth == 1) {
                return m.start();
            }
        }
        return -1;
    }
}
