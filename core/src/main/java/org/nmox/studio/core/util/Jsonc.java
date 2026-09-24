package org.nmox.studio.core.util;

/**
 * JSON with comments, as VS Code writes {@code tasks.json},
 * {@code launch.json} and {@code settings.json}. Promoted from the tools
 * module's task reader when the editor's settings reader became its
 * second consumer (3.1.0): one stripper, one set of rules.
 */
public final class Jsonc {

    private Jsonc() {
    }

    /**
     * JSONC → JSON: {@code //} and {@code /* *}{@code /} comments and
     * trailing commas removed, everything inside a string untouched. A
     * comment's line break is kept so a parser's error names the real line.
     */
    public static String strip(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"') {
                int start = i++;
                while (i < n) {
                    char s = text.charAt(i);
                    if (s == '\\') {
                        i += 2;
                        continue;
                    }
                    i++;
                    if (s == '"') {
                        break;
                    }
                }
                out.append(text, start, Math.min(i, n));
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                for (int k = i; k < stop; k++) {
                    if (text.charAt(k) == '\n') {
                        out.append('\n');
                    }
                }
                i = stop;
                continue;
            }
            if (c == '}' || c == ']') {
                // a comma before the closer, with only whitespace between
                int k = out.length() - 1;
                while (k >= 0 && Character.isWhitespace(out.charAt(k))) {
                    k--;
                }
                if (k >= 0 && out.charAt(k) == ',') {
                    out.deleteCharAt(k);
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
