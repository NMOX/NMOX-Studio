package org.nmox.studio.rack.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One opener for every registry front door (v2.70.0): a serving picked
 * from the ⇄ chip or from ⌘I "Live Servers" opens in the in-app Browser,
 * the system browser only when none is wired up or it declines — and
 * neither door may reach for the system browser on its own.
 */
class ServingLinksTest {

    @Test
    @DisplayName("in-app Browser first; the system browser when absent or declining")
    void picksInAppFirst() {
        List<String> inApp = new ArrayList<>();
        List<String> system = new ArrayList<>();
        assertThat(ServingLinks.open("http://localhost:8081/", url -> { inApp.add(url); return true; }, system::add)).isTrue();
        assertThat(inApp).containsExactly("http://localhost:8081/");
        assertThat(system).as("the in-app Browser took it").isEmpty();
        assertThat(ServingLinks.open("http://localhost:8082/", null, system::add)).isTrue();
        assertThat(system).as("no in-app Browser wired: the system browser").containsExactly("http://localhost:8082/");
        assertThat(ServingLinks.open("http://localhost:8083/", url -> false, system::add)).isTrue();
        assertThat(system).as("the in-app Browser declined: the system browser")
                .containsExactly("http://localhost:8082/", "http://localhost:8083/");
        assertThat(ServingLinks.open("http://localhost:8084/", url -> false, url -> false))
                .as("neither took it: the caller may say so (Docker's browser-refused truth)").isFalse();
    }

    @Test
    @DisplayName("every local-URL door rides ServingLinks.open and none dials Desktop.browse itself")
    void bothDoorsRideTheOpener() throws Exception {
        Path base = Path.of("src/main/java/org/nmox/studio/rack");
        for (String door : List.of("service/RackStatusLine.java", "search/LiveServerSearchProvider.java",
                "docker/DockerPanelTopComponent.java", "devices/SonarDevice.java")) {
            String src = Files.readString(base.resolve(door));
            assertThat(src).as(door + " opens picks through the shared opener")
                    .contains("ServingLinks.open(");
            assertThat(src).as(door + " never dials the system browser on its own (v2.70.0)")
                    .doesNotContain("Desktop.getDesktop().browse(");
        }
        for (String rack : List.of("docker/DockerPanelTopComponent.java", "devices/SonarDevice.java")) {
            assertThat(Files.readString(base.resolve(rack))).as(rack + " opens the port in-app first")
                    .contains("ServingLinks.open(");
        }
        String opener = Files.readString(base.resolve("service/ServingLinks.java"));
        assertThat(opener).as("the opener is where the system-browser fallback lives")
                .contains("Desktop.getDesktop().browse(");
    }
}
