package org.nmox.studio.rack;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.98.0 safe-default law for the rack module's destructive
 * confirmations: {@code NotifyDescriptor.Confirmation} hard-codes
 * {@code initialValue=OK_OPTION}, so Enter/Space lands on the
 * destructive button unless the full {@code NotifyDescriptor} ctor
 * pins {@code NO_OPTION} as the initial value. Infra and ui earned
 * this gate in v1.98.0/v1.106.0; the rack's two destructive confirms
 * (SONAR's process kill, Project Configuration's dependency removal)
 * predated it and shipped Enter-kills defaults until the v2.18.0
 * polish pass.
 */
class RackDialogSafetyTest {

    @Test
    @DisplayName("SONAR's kill confirm defaults Enter to NO — a reflexive keypress must not kill a process")
    void sonarKillConfirmIsSafe() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/devices/SonarDevice.java"));
        int kill = src.indexOf("\"Kill \" + owner");
        assertThat(kill).as("the kill confirm still exists as written").isPositive();
        String around = src.substring(Math.max(0, kill - 400), kill + 400);
        assertThat(around)
                .as("the kill confirm uses the FULL NotifyDescriptor ctor"
                        + " with NO_OPTION as the initial value (v1.98.0)")
                .contains("NotifyDescriptor.NO_OPTION")
                .doesNotContain("new NotifyDescriptor.Confirmation(");
    }

    @Test
    @DisplayName("Remove Dependency defaults Enter to NO — a reflexive keypress must not uninstall")
    void removeDependencyConfirmIsSafe() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/ProjectConfigDialog.java"));
        int confirm = src.indexOf("\"Remove Dependency\"");
        assertThat(confirm).as("the remove confirm still exists as written").isPositive();
        String around = src.substring(Math.max(0, confirm - 400), confirm + 400);
        assertThat(around)
                .as("the remove confirm uses the FULL NotifyDescriptor ctor"
                        + " with NO_OPTION as the initial value (v1.98.0)")
                .contains("NotifyDescriptor.NO_OPTION")
                .doesNotContain("new NotifyDescriptor.Confirmation(");
    }
}
