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
                        if (arg.isEmpty() || arg.startsWith("\"") || arg.startsWith("PlainStatus.text(")
                                || arg.startsWith("org.nmox.studio.core.util.PlainStatus.text(")) {
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
