package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tooltip never renders external text as markup (v2.86.0): Swing
 * builds a fresh {@code JToolTip} per hover and reads {@code html.disable}
 * on THAT component, so the property on the label, button or knob that
 * carries the text is never consulted (PlainTextTest pins the
 * measurement). A tooltip whose head is a device label, a preset
 * description, a project path or an install command therefore rides the
 * text guard {@code PlainText.plain}; one that MEANS its markup carries
 * a {@code PLAIN-TOOLTIP-EXEMPT:} comment and escapes every external piece
 * through {@code PlainText.escape}. Balanced-parenthesis scan over every
 * {@code setToolTipText(} call in every module. Tooltip text produced by
 * a {@code getToolTipText} override is outside this scan and is escaped
 * by hand at its authored sites.
 */
class PlainTooltipGateTest {

    @Test
    @DisplayName("every tooltip begins with our own literal, rides PlainText.plain, or is exempt in writing")
    void tooltipHeadsAreOurs() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : productionSources()) {
            String body = Files.readString(p);
            int pos = 0;
            while (true) {
                int k = body.indexOf("setToolTipText(", pos);
                if (k < 0) {
                    break;
                }
                int start = k + "setToolTipText(".length();
                int close = closingParen(body, start);
                pos = close + 1;
                String arg = strip(body.substring(start, close));
                if (ours(arg) || exempt(body, k, close, "PLAIN-TOOLTIP-EXEMPT:")) {
                    continue;
                }
                offenders.add(p.getFileName() + ":" + line(body, k) + " " + clip(arg, 60));
            }
        }
        assertThat(offenders)
                .as("a tooltip whose head is not our own literal — wrap it in PlainText.plain so it can never render as markup")
                .isEmpty();
    }

    static boolean ours(String arg) {
        return arg.isEmpty() || arg.startsWith("\"") || arg.equals("null")
                || arg.startsWith("Bundle.") || arg.startsWith("NbBundle.") || arg.startsWith("org.openide.util.NbBundle.")
                || arg.startsWith("PlainText.plain(") || arg.startsWith("org.nmox.studio.core.util.PlainText.plain(");
    }

    static boolean exempt(String body, int k, int close, String marker) {
        int lineStart = body.lastIndexOf('\n', k - 1) + 1;
        int prevStart = body.lastIndexOf('\n', lineStart - 2) + 1;
        return body.substring(lineStart, close).contains(marker)
                || body.substring(prevStart, lineStart).contains(marker);
    }

    static int line(String body, int k) {
        return 1 + (int) body.chars().limit(k).filter(c -> c == '\n').count();
    }

    static String clip(String arg, int max) {
        String flat = arg.replaceAll("\\s+", " ");
        return flat.substring(0, Math.min(max, flat.length()));
    }

    static String strip(String s) {
        return s.replaceAll("//[^\n]*", "").strip();
    }

    static List<Path> productionSources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : new String[]{"core", "editor", "tools", "rack", "project",
            "ui", "apiclient", "dbstudio", "web3", "infra"}) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                files.filter(f -> f.toString().endsWith(".java"))
                        .filter(f -> !f.getFileName().toString().startsWith("Plain"))
                        .forEach(out::add);
            }
        }
        return out;
    }

    static int closingParen(String body, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return body.length();
    }
}
