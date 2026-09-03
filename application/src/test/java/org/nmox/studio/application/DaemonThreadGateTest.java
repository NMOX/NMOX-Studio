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
 * Every raw thread in the product's main sources is born in ONE place —
 * {@code core.util.Threads} (named, daemon) — or is a shutdown hook, which
 * must not be daemon and carries its name on the same line. The v2.63.0
 * senior-RCP pass measured 22 {@code new Thread(} sites of which ten were
 * unnamed or non-daemon: an anonymous pump makes a thread dump useless and
 * a non-daemon one can keep the JVM alive past the platform's exit. The
 * gate reads the SHAPE: any other {@code new Thread(} in main sources
 * fails the build by file and line.
 */
class DaemonThreadGateTest {

    private static final List<String> MODULES = List.of("core", "editor", "tools", "rack", "infra",
            "apiclient", "dbstudio", "web3", "project", "ui");

    @Test
    @DisplayName("new Thread( appears only in core.util.Threads or on a named addShutdownHook line")
    void everyThreadIsNamedAndDaemonByConstruction() throws IOException {
        List<String> offenders = new ArrayList<>();
        int hooks = 0;
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    if (p.endsWith(Path.of("core", "util", "Threads.java"))) {
                        continue;
                    }
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (!line.contains("new Thread(")) {
                            continue;
                        }
                        // a shutdown hook: lawful only when the hook thread is NAMED —
                        // the name literal closes the lambda, however long it is, so
                        // the statement is read to the parenthesis that closes the hook
                        String prev = i > 0 ? lines.get(i - 1) : "";
                        if (line.contains("addShutdownHook(") || prev.contains("addShutdownHook(")) {
                            StringBuilder stmt = new StringBuilder();
                            int depth = 0;
                            for (int j = Math.max(0, i - 1); j < Math.min(lines.size(), i + 80); j++) {
                                String l = lines.get(j);
                                stmt.append(l).append('\n');
                                depth += l.chars().filter(c -> c == '(').count() - l.chars().filter(c -> c == ')').count();
                                if (j >= i && depth <= 0) {
                                    break;
                                }
                            }
                            if (stmt.toString().matches("(?s).*\"[A-Za-z][A-Za-z0-9 _-]*\"\\s*\\)\\s*\\)\\s*;.*")) {
                                hooks++;
                                continue;
                            }
                        }
                        offenders.add(src.relativize(p) + ":" + (i + 1) + "  " + line.trim());
                    }
                }
            }
        }
        assertThat(hooks).as("the census saw the shutdown hooks (the product has several)").isGreaterThan(0);
        assertThat(offenders)
                .as("raw threads outside core.util.Threads (use Threads.daemon/startDaemon)")
                .isEmpty();
    }
}
