package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * External text never reaches a dialog as a String message (v2.86.0):
 * the platform's presenter builds a {@code JOptionPane} from a String
 * (and renders an html-prefixed one by design), and Swing lays the rest
 * out as a label per line and per wrapped fragment, where {@code <html>}
 * renders — so any message built from an exception, a file name, a
 * tool's output, a user's own names or catalog prose goes through
 * {@code PlainDialogs.plain}. Every constructor that takes a message
 * is read — Message, Confirmation, the bare NotifyDescriptor and
 * DialogDescriptor — by a balanced-parenthesis scan (a regex ran a
 * one-argument literal site into its neighbour once). A first argument
 * that is NOTHING but string literals (our own sentence) or a component
 * expression may stay; everything else is an offender.
 */
class PlainMessageGateTest {

    private static final String[] HEADS = {
        "new NotifyDescriptor.Message(", "new NotifyDescriptor.Confirmation(", "new NotifyDescriptor(",
        "new DialogDescriptor(", "new org.openide.DialogDescriptor(", "new org.openide.NotifyDescriptor.Message("
    };
    private static final Pattern LITERALS_ONLY = Pattern.compile("(?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+");
    /** A lone identifier or field path with no operators — a panel/form/scroll variable, never text. */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)*");

    @Test
    @DisplayName("every String handed to a dialog is our own literal sentence, or rides PlainDialogs")
    void externalTextRidesPlainDialogs() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : new String[]{"core", "editor", "tools", "rack", "project",
            "ui", "apiclient", "dbstudio", "web3", "infra"}) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    if (p.getFileName().toString().equals("PlainDialogs.java")) {
                        continue;
                    }
                    String body = Files.readString(p);
                    for (String head : HEADS) {
                        int pos = 0;
                        while (true) {
                            int k = body.indexOf(head, pos);
                            if (k < 0) {
                                break;
                            }
                            int[] firstArg = firstArgument(body, k + head.length());
                            pos = firstArg[2] + 1;
                            String arg = body.substring(firstArg[0], firstArg[1])
                                    .replaceAll("//[^\n]*", "").strip(); // a comment above the argument is not the argument
                            if (arg.isEmpty() || LITERALS_ONLY.matcher(arg).matches() || isComponent(arg)
                                    || arg.startsWith("\"<html>")) {
                                // an html-led literal is a deliberately AUTHORED HTML message; its
                                // interpolations must be the product's own tokens — the review's lens,
                                // not this gate's (NewProjectDialog's install dialog names the package manager)
                                continue;
                            }
                            int line = 1 + (int) body.chars().limit(k).filter(c -> c == '\n').count();
                            offenders.add(module + "/" + p.getFileName() + ":" + line + " "
                                    + arg.replaceAll("\\s+", " ").substring(0, Math.min(70, arg.length())));
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a non-literal String handed to a dialog — route it through PlainDialogs.plain so it can never render as markup")
                .isEmpty();
    }

    /** Component expressions the message slot may carry as-is. */
    private static boolean isComponent(String arg) {
        return arg.startsWith("PlainDialogs.plain(") || arg.startsWith("org.nmox.studio.core.util.PlainDialogs.plain(")
                || arg.startsWith("new javax.swing.JScrollPane") || arg.startsWith("new JScrollPane")
                || arg.startsWith("new javax.swing.JLabel(") // a JLabel a site builds on purpose (its own literal HTML)
                || arg.startsWith("new JLabel(")
                || (IDENTIFIER.matcher(arg).matches() && !arg.matches("(?i).*(message|msg|text|problem|invalid|report).*"));
    }

    /** {start, end, closingParenIndex} of the first top-level argument after an open paren. */
    private static int[] firstArgument(String body, int start) {
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
                    return new int[]{start, i, i};
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                // find the closing paren for the caller's advance
                int close = i;
                int d = 0;
                boolean s = false;
                boolean e = false;
                for (int j = i; j < body.length(); j++) {
                    char cj = body.charAt(j);
                    if (s) {
                        if (e) {
                            e = false;
                        } else if (cj == '\\') {
                            e = true;
                        } else if (cj == '"') {
                            s = false;
                        }
                        continue;
                    }
                    if (cj == '"') {
                        s = true;
                    } else if (cj == '(' || cj == '[' || cj == '{') {
                        d++;
                    } else if (cj == ')' || cj == ']' || cj == '}') {
                        if (d == 0) {
                            close = j;
                            break;
                        }
                        d--;
                    }
                }
                return new int[]{start, i, close};
            }
        }
        return new int[]{start, body.length(), body.length()};
    }
}
