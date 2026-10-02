package org.nmox.studio.application;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A walk does not see the machine's Docker (3.5.11).
 *
 * <p>{@code scripts/platform-walk.sh} boots the app in a throwaway home so
 * that the walker's own data cannot hide a first-launch defect or end up in
 * a picture. The daemon was left out of that: on a developer's machine DB
 * Studio's "a database container is running" balloon named one of their
 * containers in the walk's DB Studio picture. The docs forge had the same
 * defect until 3.5.0 and was given a filtered view or a dead address; the
 * walk script was the one that was not.
 *
 * <p>This runs the script against a stand-in app that writes down the
 * {@code DOCKER_HOST} it was started with.
 */
@DisabledOnOs(OS.WINDOWS) // the stand-in launcher is a shell script; the script's own Windows branch starts an .exe
class WalkSeesNoDockerGateTest {

    private static String dockerHostSeenBy(Path tmp, String developers, String asked) throws Exception {
        Path bin = Files.createDirectories(tmp.resolve("app/bin"));
        Path seen = tmp.resolve("seen");
        Path launcher = bin.resolve("nmoxstudio");
        Files.writeString(launcher, String.join("\n",
                "#!/bin/sh",
                "printf '%s' \"${DOCKER_HOST-unset}\" > \"" + seen + "\"",
                ""), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwxr-xr-x"));

        ProcessBuilder pb = new ProcessBuilder("sh", "scripts/platform-walk.sh", tmp.resolve("out").toString())
                .directory(new File("..").getCanonicalFile())
                .redirectErrorStream(true)
                .redirectOutput(tmp.resolve("script-output.txt").toFile());
        pb.environment().put("NMOX_WALK_APP", tmp.resolve("app").toString());
        pb.environment().put("NMOX_WALK_TIMEOUT", "20");
        pb.environment().remove("JAVA_HOME");
        pb.environment().remove("NMOX_WALK_DOCKER_HOST");
        if (developers == null) {
            pb.environment().remove("DOCKER_HOST");
        } else {
            pb.environment().put("DOCKER_HOST", developers);
        }
        if (asked != null) {
            pb.environment().put("NMOX_WALK_DOCKER_HOST", asked);
        }
        Process script = pb.start();
        try {
            assertThat(script.waitFor(90, TimeUnit.SECONDS)).as("the walk of a stand-in that exits at once comes back").isTrue();
        } finally {
            script.descendants().forEach(ProcessHandle::destroyForcibly);
            script.destroyForcibly();
        }
        assertThat(seen).as("the stand-in app was started: "
                + Files.readString(tmp.resolve("script-output.txt"), StandardCharsets.UTF_8)).exists();
        return Files.readString(seen, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the app is started with an address nothing listens on, whatever the walker's own DOCKER_HOST says")
    void theDevelopersDaemonIsNotHandedOver(@TempDir Path tmp) throws Exception {
        assertThat(dockerHostSeenBy(tmp.resolve("a"), "unix:///Users/someone/.docker/run/docker.sock", null))
                .isEqualTo("tcp://127.0.0.1:9");
        assertThat(dockerHostSeenBy(tmp.resolve("b"), null, null))
                .as("and with none set: the default socket would otherwise be found").isEqualTo("tcp://127.0.0.1:9");
    }

    @Test
    @DisplayName("a walk that is about Docker names its own view, and gets that one")
    void aWalkAboutDockerNamesItsView(@TempDir Path tmp) throws Exception {
        assertThat(dockerHostSeenBy(tmp, "unix:///Users/someone/.docker/run/docker.sock", "tcp://127.0.0.1:23750"))
                .isEqualTo("tcp://127.0.0.1:23750");
    }
}
