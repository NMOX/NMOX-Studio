package org.nmox.studio.ui.actions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The launcher hands the IDE {@code CFProcessPath} so macOS delivers Finder's
 * folders to it; a child that inherits the variable believes it IS NMOX Studio
 * (measured: a plain CoreFoundation tool run with it reported the bundle's id
 * as its own). So the IDE takes it out again, and this test holds that no
 * child can see it afterwards, however that child is started.
 *
 * <p>The variable under test is set by surefire (ui/pom.xml), because a
 * variable has to be in the environment before the JVM starts for both of
 * its copies — the native one and the JDK's snapshot — to hold it.
 */
@DisabledOnOs(OS.WINDOWS) // there is no unsetenv, and nothing here runs on Windows
class LauncherEnvironmentTest {

    private static final String VAR = "NMOX_LAUNCHER_ENV_PROBE";

    @Test
    @DisplayName("after forget, neither the JVM nor any child it starts sees the variable")
    void forgottenForEveryChild() throws Exception {
        assertThat(System.getenv(VAR)).as("surefire sets %s (ui/pom.xml)", VAR).isEqualTo("set-by-surefire");
        assertThat(childSees(false)).as("a child started before the forget inherits it").isEqualTo("set-by-surefire");

        assertThat(LauncherEnvironment.forget(VAR)).as("both copies of the environment lost it").isTrue();

        assertThat(System.getenv(VAR)).as("the JDK's snapshot").isNull();
        assertThat(childSees(false))
                .as("a child whose environment() was never touched inherits the NATIVE environment")
                .isEqualTo("<unset>");
        assertThat(childSees(true))
                .as("a child given a copy of environment() inherits the JDK's snapshot")
                .isEqualTo("<unset>");
        assertThat(LauncherEnvironment.forget(VAR)).as("forgetting what is already gone is not a failure").isTrue();
    }

    @Test
    @DisplayName("a variable that was never there is reported gone")
    void absentIsGone() {
        assertThat(LauncherEnvironment.removeFromSnapshot("NMOX_NEVER_SET_ANYWHERE")).isTrue();
    }

    private static String childSees(boolean copyEnvironment) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "printf %s \"${" + VAR + "-<unset>}\"");
        if (copyEnvironment) {
            pb.environment().put("NMOX_TOUCHED", "1");
        }
        Process p = pb.redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor()).isZero();
        return out;
    }
}
