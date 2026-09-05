package org.nmox.studio.tools.npm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.awt.Notification;

import static org.assertj.core.api.Assertions.assertThat;

/** One live balloon per key (v2.74.0): a repeat clears the previous; both balloon sites route through it. */
class BalloonsTest {

    @AfterEach
    void drain() {
        Balloons.clearForTest();
    }

    private static Notification fake(List<String> cleared, String name) {
        return new Notification() {
            @Override
            public void clear() {
                cleared.add(name);
            }
        };
    }

    @Test
    @DisplayName("a second balloon under the same key clears the first; other keys are untouched")
    void replaceClearsThePrevious() {
        List<String> cleared = new ArrayList<>();
        Balloons.replace("install:/shop", fake(cleared, "a"));
        Balloons.replace("launch:Run — api", fake(cleared, "b"));
        assertThat(cleared).isEmpty();
        Balloons.replace("install:/shop", fake(cleared, "c"));
        assertThat(cleared).containsExactly("a");
        assertThat(Balloons.liveCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("the install door and the launch-failure balloon both ride Balloons.replace")
    void bothSitesRouteThrough() throws Exception {
        for (String f : List.of("InstallDoor.java", "LaunchFailure.java")) {
            String src = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/npm/" + f));
            assertThat(src).as(f).contains("Balloons.replace(");
            assertThat(src.indexOf("Balloons.replace(")).as(f + ": the notify call is the replace's argument")
                    .isLessThan(src.indexOf("NotificationDisplayer.getDefault().notify("));
        }
    }
}
