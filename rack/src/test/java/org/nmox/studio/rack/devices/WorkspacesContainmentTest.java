package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A cloned repository's workspace globs name packages inside it (3.3
 * review): a {@code ../} glob, an absolute one, or a link that leaves the
 * repository never makes the IDE read a manifest outside it — the editor's
 * token and route lookups reach this on an ordinary keystroke.
 */
class WorkspacesContainmentTest {

    @TempDir
    Path tmp;

    private static void write(Path p, String text) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    @Test
    @DisplayName("a glob climbing out, an absolute glob, and a link out of the repository name nothing")
    void containment() throws IOException {
        Path outside = tmp.resolve("outside");
        write(outside.resolve("leak/package.json"), "{\"name\":\"leak\"}");
        Path repo = tmp.resolve("repo");
        write(repo.resolve("package.json"), "{\"workspaces\":[\"../outside/*\",\""
                + outside.toAbsolutePath().toString().replace("\\", "\\\\") + "/*\",\"linkdir/**\",\"packages/*\"]}");
        write(repo.resolve("packages/ok/package.json"), "{\"name\":\"ok\"}");
        try {
            Files.createSymbolicLink(repo.resolve("linkdir"), outside);
            // a package folder that is a link out: the one case only the real-path check refuses
            Files.createSymbolicLink(repo.resolve("packages/evil"), outside.resolve("leak"));
        } catch (IOException | UnsupportedOperationException ex) {
            assumeTrue(false, "this file system cannot make a symbolic link");
        }
        assertThat(Workspaces.packages(repo.toFile())).containsOnlyKeys("ok");
    }

    @Test
    @DisplayName("the glob rule: relative and without a .. segment")
    void globRule() {
        assertThat(Workspaces.staysInside("packages/*")).isTrue();
        assertThat(Workspaces.staysInside("apps/**")).isTrue();
        for (String bad : new String[] {"../x/*", "a/../../b", "/abs/*", "\\\\server\\x", "C:/x/*", "a\\..\\b"}) {
            assertThat(Workspaces.staysInside(bad)).as(bad).isFalse();
        }
    }

    @Test
    @DisplayName("a caller's own ceiling: WAYPOINT keeps its list, the route jump reads further")
    void ceiling() throws IOException {
        Path repo = tmp.resolve("big");
        write(repo.resolve("package.json"), "{\"workspaces\":[\"packages/*\"]}");
        for (int i = 0; i < Workspaces.MAX_PACKAGES + 6; i++) {
            write(repo.resolve("packages/p" + i + "/package.json"), "{\"name\":\"p" + i + "\"}");
        }
        assertThat(Workspaces.packages(repo.toFile())).hasSize(Workspaces.MAX_PACKAGES);
        assertThat(Workspaces.packages(repo.toFile(), 500)).hasSize(Workspaces.MAX_PACKAGES + 6);
    }
}
