package org.nmox.studio.ui.browser.devtools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * What "Explain this error" sends (v2.39.2, the elevation arc's
 * learning multiplier): the runtime error the Browser just caught,
 * plus — when the error resolved to a project file (v2.39.0) — a
 * SMALL source excerpt around the failing line, because "what does
 * ReferenceError mean" is answerable from the message alone but "why
 * HERE" needs the line. The disclosure discipline of every ORACLE
 * flow (v1.171.0): assembled and capped HERE, where the data lives,
 * so the consent dialog's one-line summary is the literal truth; the
 * seam sends exactly what it is given and nothing can widen it.
 *
 * <p>Caps: ±{@value #CONTEXT} lines of context, {@value #LINE_CAP}
 * chars per line (a minified bundle's single line must not become the
 * whole file), message at {@value #MSG_CAP} code points. The excerpt
 * names its own truncation. Pure so every cap is a unit test.
 */
public final class BrowserErrorDisclosure {

    static final int CONTEXT = 3;
    static final int LINE_CAP = 200;
    static final int MSG_CAP = 500;

    private BrowserErrorDisclosure() {
    }

    /** The consent dialog's one-line summary — the literal truth. */
    public static String what(File file, int line) {
        if (file == null) {
            return "The error message only — no source (the error did not"
                    + " resolve to a project file).";
        }
        return "The error message and " + (CONTEXT * 2 + 1)
                + " lines of " + file.getName() + " around line " + line + ".";
    }

    /** The conversation's opening body. */
    public static String body(String message, File file, int line) {
        StringBuilder b = new StringBuilder();
        b.append("Runtime error in the browser:\n").append(cap(message, MSG_CAP));
        if (file == null) {
            return b.toString();
        }
        b.append("\n\nSource — ").append(file.getName())
                .append(", line ").append(line).append(" marked:\n");
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int from = Math.max(1, line - CONTEXT);
            int to = Math.min(lines.size(), line + CONTEXT);
            for (int i = from; i <= to; i++) {
                b.append(i == line ? ">> " : "   ")
                        .append(i).append(": ")
                        .append(cap(lines.get(i - 1), LINE_CAP)).append('\n');
            }
            if (line > lines.size()) {
                b.append("   (line ").append(line)
                        .append(" is past the file's end — the served copy may differ)\n");
            }
        } catch (IOException unreadable) {
            b.append("   (file unreadable: ").append(unreadable.getMessage()).append(")\n");
        }
        return b.toString();
    }

    private static String cap(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        // code-point-safe (the v1.149.0 cap law)
        return s.substring(0, s.offsetByCodePoints(0, max)) + "…[truncated]";
    }
}
