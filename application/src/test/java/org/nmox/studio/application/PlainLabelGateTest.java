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
 * A label never renders external text as markup (v2.86.0): Swing's
 * {@code JLabel} renders a text that BEGINS with {@code <html>}, and
 * the product sets label text from directory names, git branch names,
 * artifact and function names, server errors and drop-in catalogs. The
 * cure is the order-independent text guard {@code PlainText.plain} on
 * the argument, at construction ({@code new JLabel(PlainText.plain(x))})
 * and at every {@code setText}. The component property {@code html.disable}
 * is NOT used for a plain label: {@code BasicHTML} installs the html view
 * when the text is SET, so {@code PlainTables.plain(new JLabel(text))}
 * sets the property one step too LATE and the label still renders markup
 * (the v1.307.0 ordering trap, caught live on a project directory named
 * {@code <html><b>bold</b>}). The property is right only for a renderer,
 * whose {@code setText} runs per paint AFTER it. Balanced-parenthesis scan
 * over every {@code new JLabel(} and every {@code <label>.setText(} whose
 * argument's head is not the product's own literal. A label that MEANS its
 * markup carries a {@code PLAIN-LABEL-EXEMPT:} comment stating why nothing
 * external is spliced in.
 */
class PlainLabelGateTest {

    private static final Pattern DECLARED = Pattern.compile("\\bJLabel\\s+(\\w+)\\s*[=;,)]");

    @Test
    @DisplayName("every label built from a non-literal is html-disabled at construction or exempt in writing")
    void constructionsArePlain() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : productionSources()) {
            String body = Files.readString(p);
            int pos = 0;
            while (true) {
                int k = body.indexOf("new JLabel(", pos);
                if (k < 0) {
                    break;
                }
                int start = k + "new JLabel(".length();
                int close = closingParen(body, start);
                pos = close + 1;
                String arg = strip(body.substring(start, close));
                if (ours(arg) || exempt(body, k, close, "PLAIN-LABEL-EXEMPT:")) {
                    continue;
                }
                offenders.add(p.getFileName() + ":" + line(body, k) + " new JLabel(" + clip(arg, 50));
            }
        }
        assertThat(offenders)
                .as("a label built from text the product did not write — wrap the construction in PlainTables.plain")
                .isEmpty();
    }

    @Test
    @DisplayName("every label whose text is set from a non-literal is html-disabled somewhere in its file")
    void setTextsArePlain() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : productionSources()) {
            String body = Files.readString(p);
            Matcher d = DECLARED.matcher(body);
            List<String> names = new ArrayList<>();
            while (d.find()) {
                if (!names.contains(d.group(1))) {
                    names.add(d.group(1));
                }
            }
            for (String name : names) {
                Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "\\.setText\\(").matcher(body);
                while (m.find()) {
                    int close = closingParen(body, m.end());
                    String arg = strip(body.substring(m.end(), close));
                    if (ours(arg)) {
                        continue;
                    }
                    offenders.add(p.getFileName() + ":" + line(body, m.start()) + " " + name + ".setText(" + clip(arg, 50));
                }
            }
        }
        assertThat(offenders)
                .as("a label set from text the product did not write — html-disable it at construction (PlainTables.plain) or guard the text (PlainText.plain)")
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
