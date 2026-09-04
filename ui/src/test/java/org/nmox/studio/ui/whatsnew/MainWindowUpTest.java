package org.nmox.studio.ui.whatsnew;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainWindowUpTest {

    @Test
    @DisplayName("A dialog may open only against a main window that is showing AND active")
    void decision() {
        assertThat(MainWindowUp.isUp(true, true)).isTrue();
        assertThat(MainWindowUp.isUp(true, false)).as("showing but not yet active: still being ordered front").isFalse();
        assertThat(MainWindowUp.isUp(false, true)).isFalse();
        assertThat(MainWindowUp.isUp(false, false)).isFalse();
    }

    @Test
    @DisplayName("The first-boot What's New routes through MainWindowUp; the menu path does not need to (v2.69.7)")
    void firstBootWaitsForTheMainWindow() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/whatsnew/WhatsNew.java"));
        assertThat(src).as("the first-boot dialog waits for the main window — shown earlier it is created but never visible")
                .contains("MainWindowUp.whenUp(");
        assertThat(src).as("firstBoot passes the flag").contains("\"What's new since \" + lastSeen, true)");
    }
}
