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
 * A button never renders external text as markup (v2.86.0). Swing's
 * {@code AbstractButton} — {@code JButton}, {@code JMenuItem},
 * {@code JCheckBox} and the rest — renders a text that BEGINS with
 * {@code <html>} exactly as a {@code JLabel} does (measured, pinned by
 * PlainTextTest), and the product sets button text from directory
 * names (the recent-projects rows are real buttons since v2.74.0),
 * device titles carrying a cloned script name, container labels and
 * server errors. Every such text rides the order-independent guard
 * {@code core.util.PlainText.plain} — at construction (the first,
 * textual argument) and at {@code setText}. A constructor whose first
 * argument is an {@code Action}, an {@code Icon} or {@code this} takes
 * no text there and is skipped. Balanced-parenthesis scan over every
 * {@code new <button>(} and every {@code <button>.setText(} in every
 * module; the walk caught the recent-project row buttons rendering a
 * markup-named directory before this gate existed.
 */
class PlainButtonGateTest {

    private static final Pattern CTOR = Pattern.compile(
            "\\bnew\\s+(?:[\\w.]*\\.)?(JButton|JMenuItem|JMenu|JCheckBox|JCheckBoxMenuItem"
            + "|JRadioButton|JRadioButtonMenuItem|JToggleButton)\\(");
    private static final Pattern DECLARED = Pattern.compile(
            "\\b(?:JButton|JMenuItem|JMenu|JCheckBox|JCheckBoxMenuItem"
            + "|JRadioButton|JRadioButtonMenuItem|JToggleButton)\\s+(\\w+)");

    @Test
    @DisplayName("every button built from non-literal text guards it with PlainText.plain")
    void constructorsArePlain() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : sources()) {
            String body = Files.readString(p);
            Matcher m = CTOR.matcher(body);
            while (m.find()) {
                int close = closingParen(body, m.end());
                String first = strip(firstArg(body.substring(m.end(), close)));
                if (ours(first) || actionOrIcon(first)) {
                    continue;
                }
                offenders.add(p.getFileName() + ":" + line(body, m.start()) + " " + clip(first));
            }
        }
        assertThat(offenders)
                .as("a button built from text the product did not write — guard the text with PlainText.plain")
                .isEmpty();
    }

    @Test
    @DisplayName("every button whose text is set from a non-literal guards it with PlainText.plain")
    void setTextsArePlain() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : sources()) {
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
                    offenders.add(p.getFileName() + ":" + line(body, m.start()) + " " + name + ".setText(" + clip(arg));
                }
            }
        }
        assertThat(offenders)
                .as("a button set from text the product did not write — guard the text with PlainText.plain")
                .isEmpty();
    }

    static boolean ours(String a) {
        a = a.strip();
        return a.isEmpty() || a.startsWith("\"") || a.equals("null")
                || a.startsWith("Bundle.") || a.startsWith("NbBundle.") || a.startsWith("org.openide.util.NbBundle.")
                || a.startsWith("PlainText.plain(") || a.startsWith("org.nmox.studio.core.util.PlainText.plain(");
    }

    static boolean actionOrIcon(String a) {
        a = a.strip();
        return a.equals("this") || a.startsWith("new ") || a.endsWith("Action")
                || a.contains("getIcon") || a.endsWith("Icon") || a.startsWith("Actions.");
    }

    static String firstArg(String raw) {
        int depth = 0;
        boolean inString = false, escaped = false;
        StringBuilder fa = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                fa.append(c);
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
                fa.append(c);
            } else if (c == ',' && depth == 0) {
                break;
            } else {
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    depth--;
                }
                fa.append(c);
            }
        }
        return fa.toString();
    }

    static String strip(String s) {
        return s.replaceAll("//[^\n]*", "").strip();
    }

    static String clip(String arg) {
        String flat = arg.replaceAll("\\s+", " ");
        return flat.substring(0, Math.min(50, flat.length()));
    }

    static int line(String body, int k) {
        return 1 + (int) body.chars().limit(k).filter(c -> c == '\n').count();
    }

    static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : new String[]{"core", "editor", "tools", "rack", "project",
            "ui", "apiclient", "dbstudio", "web3", "infra"}) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                files.filter(f -> f.toString().endsWith(".java")).forEach(out::add);
            }
        }
        return out;
    }

    static int closingParen(String body, int start) {
        int depth = 0;
        boolean inString = false, escaped = false;
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
