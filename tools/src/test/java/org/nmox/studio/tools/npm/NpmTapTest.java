package org.nmox.studio.tools.npm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.LiveRuns;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The NPM Service lane's line listener, spawned for real (3.6.0): a
 * caller that reads a script's output as it comes — a VS Code npm task's
 * problem matcher — hears every line the script prints, on both streams,
 * and the run is otherwise the lane's own. The "package manager" is
 * {@code sh} running a script named {@code run}, as in {@link
 * NpmRunLaneTest}; the script spawns nothing and ends by itself.
 */
class NpmTapTest {

    @TempDir
    Path dir;

    @AfterEach
    void stopEverything() {
        LiveRuns.stopAll();
        LiveRuns.clearForTest();
    }

    @Test
    @DisplayName("a listener hears every line of the script, stdout and stderr, and the run still answers with its output")
    void theListenerHearsEveryLine() throws Exception {
        Files.writeString(dir.resolve("run"),
                "echo 'src/a.ts(3,7): error TS2322: nope'\n"
                + "echo 'on the other stream' >&2\n"
                + "echo done\n");
        List<String> heard = Collections.synchronizedList(new ArrayList<>());
        String output = new NpmService().runCommand(dir.toFile(), heard::add, "sh", "run", "lint")
                .get(30, TimeUnit.SECONDS);
        assertThat(heard).containsExactlyInAnyOrder("src/a.ts(3,7): error TS2322: nope", "on the other stream",
                "done");
        assertThat(output).as("the lane's own answer is unchanged").contains("error TS2322").contains("done");
    }

    @Test
    @DisplayName("without a listener the lane runs as it always did")
    void noListenerNoDifference() throws Exception {
        Files.writeString(dir.resolve("run"), "echo plain\n");
        assertThat(new NpmService().runCommand(dir.toFile(), "sh", "run", "lint").get(30, TimeUnit.SECONDS))
                .contains("plain");
    }
}
