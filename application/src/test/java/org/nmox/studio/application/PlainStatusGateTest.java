package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status line never renders markup (v2.86.0): it is a JLabel with
 * no html-disable, and a label whose text BEGINS with {@code <html>}
 * renders. A status text whose head is the product's own literal can
 * never begin that way; one whose head is a script name, a run label,
 * an exception's words or a CSS name can — so those ride
 * {@code PlainStatus.text}. Balanced-parenthesis scan over every
 * {@code setStatusText(} call in every module.
 */
class PlainStatusGateTest {

    @Test
    @DisplayName("every status text begins with our own literal or rides PlainStatus.text")
    void statusHeadsAreOurs() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : new String[]{"core", "editor", "tools", "rack", "project",
            "ui", "apiclient", "dbstudio", "web3", "infra"}) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    if (p.getFileName().toString().equals("PlainStatus.java")) {
                        continue;
                    }
                    String body = Files.readString(p);
                    int pos = 0;
                    while (true) {
                        int k = body.indexOf("setStatusText(", pos);
                        if (k < 0) {
                            break;
                        }
                        int start = k + "setStatusText(".length();
                        int close = closingParen(body, start);
                        pos = close + 1;
                        String arg = body.substring(start, close).replaceAll("//[^\n]*", "").strip();
                        if (headIsOurs(arg)) {
                            continue;
                        }
                        int line = 1 + (int) body.chars().limit(k).filter(c -> c == '\n').count();
                        offenders.add(module + "/" + p.getFileName() + ":" + line + " " + arg.replaceAll("\\s+", " ").substring(0, Math.min(60, arg.length())));
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a status text whose head is not our own literal — wrap it in PlainStatus.text so it can never render as markup")
                .isEmpty();
    }


    /**
     * Whether the head of whatever string this expression produces is one
     * the product wrote. Three accepted heads: our own literal, a bundle
     * value (v2.97.0, the l10n arc — an authored sentence that merely
     * moved into a .properties file), and {@code PlainStatus.text}, which
     * guards anything else.
     *
     * <p>A conditional expression is accepted when EVERY branch is — the
     * law is about the string that reaches the label, not the shape of the
     * expression that builds it, and the l10n arc turned five one-literal
     * status calls into plural-aware ternaries of two bundle values each.
     * The condition itself is not text and is never checked.
     */
    private static boolean headIsOurs(String expr) {
        String a = expr.strip();
        while (a.startsWith("(") && closingParen(a, 1) == a.length() - 1) {
            a = a.substring(1, a.length() - 1).strip();
        }
        if (a.isEmpty() || a.startsWith("\"") || a.startsWith("PlainStatus.text(")
                || a.startsWith("org.nmox.studio.core.util.PlainStatus.text(")
                || a.startsWith("Bundle.") || a.startsWith("NbBundle.")
                || a.startsWith("org.openide.util.NbBundle.")) {
            return true;
        }
        int q = topLevel(a, 0, '?');
        if (q < 0) {
            return false;
        }
        int c = ternaryColon(a, q + 1);
        return c >= 0 && headIsOurs(a.substring(q + 1, c)) && headIsOurs(a.substring(c + 1));
    }

    /** Index of the first {@code want} at bracket depth zero, or -1. */
    private static int topLevel(String a, int from, char want) {
        int depth = 0;
        for (int i = from; i < a.length(); i++) {
            char c = a.charAt(i);
            if (c == '"' || c == '\'') {
                i = endOfLiteral(a, i);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (depth == 0 && c == want) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The {@code :} that belongs to the {@code ?} just before {@code from}.
     * Nested ternaries in the true-branch each claim one colon first, and a
     * {@code ::} method reference is not a colon at all.
     */
    private static int ternaryColon(String a, int from) {
        int depth = 0;
        int pending = 0;
        for (int i = from; i < a.length(); i++) {
            char c = a.charAt(i);
            if (c == '"' || c == '\'') {
                i = endOfLiteral(a, i);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (depth == 0 && c == '?') {
                pending++;
            } else if (depth == 0 && c == ':') {
                if (i + 1 < a.length() && a.charAt(i + 1) == ':') {
                    i++;
                } else if (pending == 0) {
                    return i;
                } else {
                    pending--;
                }
            }
        }
        return -1;
    }

    /** Index of the closing quote of the literal that opens at {@code at}. */
    private static int endOfLiteral(String a, int at) {
        char quote = a.charAt(at);
        for (int i = at + 1; i < a.length(); i++) {
            char c = a.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return a.length() - 1;
    }

    private static int closingParen(String body, int start) {
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
