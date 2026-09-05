package org.nmox.studio.tools.npm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The third wall's door (v2.73.0): the balloon names the project and the install; both lanes offer it beside the refusal. */
class InstallDoorTest {

    @Test
    @DisplayName("title and detail")
    void text() {
        assertThat(InstallDoor.title(new File("/tmp/shop"))).isEqualTo("shop needs its dependencies installed");
        assertThat(InstallDoor.detail(new File("/tmp/shop"))).contains("install now").contains("NPM Explorer ▸ Install");
    }

    @Test
    @DisplayName("both lanes offer the door right where they refuse, and the door installs through the trust-gated lane")
    void wired() throws Exception {
        for (String f : List.of("NpmService.java", "WebProjectActionProvider.java")) {
            String src = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/npm/" + f));
            int wall = src.indexOf("InstallGuard.needsInstallMessage(");
            int door = src.indexOf("InstallDoor.offer(", wall);
            assertThat(door).as(f + ": the door follows the wall").isGreaterThan(wall);
            assertThat(door - wall).as(f + ": … immediately").isLessThan(300);
        }
        String door = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/npm/InstallDoor.java"));
        assertThat(door).as("the click is the NPM lane's own install (trust-gated inside)").contains("npm.install(projectDir, npm.detectPackageManager(projectDir))");
    }
}
