package org.nmox.studio.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Welcome's RECENT column and First Steps follow an aim made while the
 * tab is on screen (3.1.0). Before, both refreshed only in
 * {@code componentShowing}: aiming a project with {@code nmoxstudio --aim}
 * while the Welcome was visible left RECENT reading "projects you open
 * gather here" beside a Workbench that already listed it.
 */
class WelcomeFollowsAimTest {

    private static String method(String src, String signature) {
        int at = src.indexOf(signature);
        assertThat(at).as(signature).isNotNegative();
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return src.substring(open, i + 1);
            }
        }
        throw new AssertionError("unbalanced " + signature);
    }

    @Test
    @DisplayName("the aim listener lives for the tab's open life: added on open, removed on close")
    void listenerIsSymmetric() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/MainWindow.java"));
        assertThat(method(src, "public void componentOpened()")).contains("addListener(aimListener)");
        assertThat(method(src, "public void componentClosed()")).contains("removeListener(aimListener)");
    }

    @Test
    @DisplayName("an aim rebuilds RECENT and First Steps on the EDT")
    void anAimRebuildsTheColumns() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/MainWindow.java"));
        int at = src.indexOf("aimListener = () ->");
        assertThat(at).isNotNegative();
        String body = src.substring(at, src.indexOf("});", at));
        assertThat(body).contains("invokeLater").contains("refreshRecents()").contains("refreshGettingStarted()");

        MainWindow[] w = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> w[0] = new MainWindow());
        // delivered off the EDT, as an aim from the CLI is: must not throw
        Thread aimer = new Thread(() -> w[0].aimListener.projectChanged(), "aimer");
        aimer.start();
        aimer.join(5_000);
        SwingUtilities.invokeAndWait(() -> { });
    }
}
