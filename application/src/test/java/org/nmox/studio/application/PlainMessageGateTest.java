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
 * External text never reaches a dialog as a String message (v2.86.0):
 * Swing's option pane lays a String out as a label per line and per
 * wrapped fragment, and a label starting with {@code <html>} renders —
 * so any message built from an exception, a file name, a tool's output
 * or catalog prose goes through {@code PlainDialogs}. A message that is
 * NOTHING but string literals (our own sentence) may stay a String;
 * everything else is an offender.
 */
class PlainMessageGateTest {

    private static final Pattern SITE = Pattern.compile(
            "new\\s+NotifyDescriptor\\.Message\\s*\\(\\s*(.*?)\\s*,\\s*(?:NotifyDescriptor\\.|org\\.openide\\.NotifyDescriptor\\.)?[A-Z_]+_MESSAGE\\s*\\)", Pattern.DOTALL);
    private static final Pattern ONE_ARG = Pattern.compile(
            "new\\s+NotifyDescriptor\\.Message\\s*\\(\\s*((?:[^()]|\\((?:[^()]|\\([^()]*\\))*\\))*?)\\s*\\)\\s*\\)", Pattern.DOTALL);
    private static final Pattern LITERALS_ONLY = Pattern.compile(
            "(?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+");

    @Test
    @DisplayName("every String handed to NotifyDescriptor.Message is our own literal sentence, or rides PlainDialogs")
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
                    Matcher m = SITE.matcher(body);
                    while (m.find()) {
                        String arg = m.group(1).strip();
                        if (LITERALS_ONLY.matcher(arg).matches()) {
                            continue;
                        }
                        if (arg.startsWith("new javax.swing.JScrollPane") || arg.startsWith("new JScrollPane")
                                || arg.contains("PlainDialogs.plain(")) {
                            continue; // a component, never a String
                        }
                        int line = 1 + (int) body.chars().limit(m.start()).filter(c -> c == '\n').count();
                        offenders.add(module + "/" + p.getFileName() + ":" + line + " " + arg.replaceAll("\\s+", " ").substring(0, Math.min(60, arg.length())));
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a non-literal String message — route it through PlainDialogs.message so it can never render as markup")
                .isEmpty();
    }
}
