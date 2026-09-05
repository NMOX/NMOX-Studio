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
 * The ■ ledger (v2.71.0): every production file that spawns through
 * {@code CommandExecutor.run(} either registers the run with
 * {@code LiveRuns} (so the toolbar ■ can stop it) or carries a written
 * {@code LIVERUNS-EXEMPT:} blessing beside the spawn. Enumeration beats
 * recollection (the v1.224.0 spawn-trust ledger's shape): v2.69.10 wired
 * the ▶, v2.70.0 found the NPM lane and the Focused Test lane by hand,
 * v2.71.0 found ng generate and three setup installs — a NEW spawn site
 * now fails the build until it is stoppable or blessed.
 */
class LiveRunsLedgerTest {

    @Test
    @DisplayName("every CommandExecutor.run site registers with LiveRuns or is blessed in writing")
    void everySpawnIsStoppableOrBlessed() throws IOException {
        Path root = Path.of("..").toRealPath();
        List<String> offenders = new ArrayList<>();
        int sites = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                String s = "/" + root.relativize(p).toString().replace('\\', '/');
                if (!s.endsWith(".java") || !s.contains("src/main/java")
                        || s.contains("/target/") || s.contains("/.")) {
                    continue;
                }
                String src = Files.readString(p);
                if (!src.contains("CommandExecutor.run(") || s.endsWith("/CommandExecutor.java")) {
                    continue;
                }
                sites++;
                if (!src.contains("LiveRuns.add(") && !src.contains("LIVERUNS-EXEMPT:")) {
                    offenders.add(root.relativize(p).toString());
                }
            }
        }
        assertThat(offenders).as("a spawn the toolbar ■ cannot stop — register it with LiveRuns "
                + "or bless it with a LIVERUNS-EXEMPT: reason beside the spawn").isEmpty();
        assertThat(sites).as("the ledger has subjects").isGreaterThanOrEqualTo(8);
    }
}
