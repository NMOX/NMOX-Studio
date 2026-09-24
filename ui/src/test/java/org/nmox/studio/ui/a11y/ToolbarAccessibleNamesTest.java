package org.nmox.studio.ui.a11y;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.awt.Actions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A screen reader hears a toolbar button's name, not its mnemonic marker
 * (3.1.0). The buttons are built the platform's way, with
 * {@code Actions.connect}, which is what put the ampersand there.
 */
class ToolbarAccessibleNamesTest {

    private static JButton platformButton(String name) {
        AbstractAction action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        action.putValue(Action.SMALL_ICON, new ImageIcon(new java.awt.image.BufferedImage(16, 16, 2)));
        JButton b = new JButton();
        Actions.connect(b, action);
        return b;
    }

    @Test
    @DisplayName("the platform's own wiring puts the ampersand in the accessible name - the premise")
    void premise() throws Exception {
        SwingUtilities.invokeAndWait(() -> assertThat(platformButton("&New File...")
                .getAccessibleContext().getAccessibleName()).isEqualTo("&New File..."));
    }

    @Test
    @DisplayName("hooked buttons read without the marker, now and after the platform renames them")
    void namesAreCutNowAndLater() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JToolBar bar = new JToolBar();
            JButton newFile = platformButton("&New File...");
            JButton profile = platformButton("Profile the Application");
            bar.add(newFile);
            bar.add(profile);
            ToolbarAccessibleNames.hook(bar);
            assertThat(newFile.getAccessibleContext().getAccessibleName()).isEqualTo("New File...");
            assertThat(profile.getAccessibleContext().getAccessibleName())
                    .as("a name with no marker is left alone").isEqualTo("Profile the Application");

            newFile.getAccessibleContext().setAccessibleName("Save &All");
            assertThat(newFile.getAccessibleContext().getAccessibleName())
                    .as("a later rename is cut too").isEqualTo("Save All");

            JButton late = platformButton("&Run Main Project");
            bar.add(late);
            assertThat(late.getAccessibleContext().getAccessibleName())
                    .as("a button added after the hook").isEqualTo("Run Main Project");

            ToolbarAccessibleNames.hook(bar);
            JButton again = platformButton("&Undo");
            bar.add(again);
            assertThat(again.getAccessibleContext().getAccessibleName()).isEqualTo("Undo");
        });
    }

    @Test
    @DisplayName("re-cutting converges: the platform's own cut is the rule, and it never loops")
    void recutConverges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JToolBar bar = new JToolBar();
            JButton b = platformButton("Build && &Test & Ship");
            bar.add(b);
            ToolbarAccessibleNames.hook(bar);
            String name = b.getAccessibleContext().getAccessibleName();
            assertThat(Actions.cutAmpersand(name)).as("a fixed point of the platform's cut").isEqualTo(name);
        });
    }
}
