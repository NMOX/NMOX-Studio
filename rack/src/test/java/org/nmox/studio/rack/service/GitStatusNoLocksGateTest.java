package org.nmox.studio.rack.service;

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
 * 3.4, "when something goes wrong": {@code git status} refreshes the index
 * and so takes {@code .git/index.lock}. The chip polls it on a five-second
 * leash that SIGKILLs a slow git, and a killed git leaves the lock on disk —
 * the user's next {@code git add} failed with "File exists" (measured). With
 * {@code --no-optional-locks} git takes no lock it does not need, so a
 * status the IDE runs in the background can never strand one. Every
 * {@code git status} argv in the product carries the flag.
 */
class GitStatusNoLocksGateTest {

    /** An argv literal naming git and then (possibly several tokens later) status. */
    private static final Pattern STATUS_ARGV = Pattern.compile(
            "\"git\"((?:\\s*,\\s*\"[^\"]*\"){0,3})\\s*,\\s*\"status\"");

    @Test
    @DisplayName("Gate: every git status the product spawns carries --no-optional-locks")
    void everyStatusSpawnTakesNoLock() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> offenders = new ArrayList<>();
        int seen = 0;
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path main = module.resolve("src/main/java");
                if (!Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(main)) {
                    for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                        String src = Files.readString(f);
                        Matcher m = STATUS_ARGV.matcher(src);
                        while (m.find()) {
                            seen++;
                            if (!m.group(1).contains("\"--no-optional-locks\"")) {
                                offenders.add(root.relativize(f) + ": " + m.group());
                            }
                        }
                    }
                }
            }
        }
        assertThat(seen).as("the population is real: the chip, the guard, GIT, PREFLIGHT, the wizard")
                .isGreaterThanOrEqualTo(6);
        assertThat(offenders).as("git status without --no-optional-locks can strand index.lock")
                .isEmpty();
    }
}
