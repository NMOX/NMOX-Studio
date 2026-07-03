package org.nmox.studio.ui.options;

import java.util.prefs.Preferences;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The General options panel round-trips a single toggle against the
 * shared {@code nmox/ui} preference node that the update-check startup
 * task gates on. These exercise the non-UI contract — load, dirty
 * detection, apply — against real (headless) NetBeans preferences.
 */
class GeneralOptionsPanelControllerTest {

    private static Preferences prefs() {
        return NbPreferences.root().node("nmox/ui");
    }

    @AfterEach
    void restoreDefault() {
        prefs().remove("updateCheck");
    }

    private static JCheckBox checkbox(GeneralOptionsPanelController c) throws Exception {
        // getComponent builds the panel; the toggle is the sole checkbox in it
        java.awt.Component panel = c.getComponent(org.openide.util.Lookup.getDefault());
        var field = GeneralOptionsPanelController.class.getDeclaredField("updateCheck");
        field.setAccessible(true);
        return (JCheckBox) field.get(c);
    }

    @Test
    @DisplayName("update() loads the stored value onto the toggle; default is on")
    void updateLoadsStoredValue() throws Exception {
        prefs().putBoolean("updateCheck", false);
        GeneralOptionsPanelController c = new GeneralOptionsPanelController();
        c.update();
        assertThat(checkbox(c).isSelected()).isFalse();

        prefs().remove("updateCheck"); // absent -> default true
        c.update();
        assertThat(checkbox(c).isSelected()).isTrue();
    }

    @Test
    @DisplayName("isChanged() is false right after a load, true once the toggle diverges")
    void isChangedTracksDivergence() throws Exception {
        prefs().putBoolean("updateCheck", true);
        GeneralOptionsPanelController c = new GeneralOptionsPanelController();
        c.update();
        assertThat(c.isChanged()).isFalse();

        checkbox(c).setSelected(false);
        assertThat(c.isChanged()).isTrue();
    }

    @Test
    @DisplayName("applyChanges() writes the toggle back into the shared node")
    void applyChangesPersists() throws Exception {
        prefs().putBoolean("updateCheck", true);
        GeneralOptionsPanelController c = new GeneralOptionsPanelController();
        c.update();
        checkbox(c).setSelected(false);
        c.applyChanges();
        assertThat(prefs().getBoolean("updateCheck", true)).isFalse();
    }

    @Test
    @DisplayName("A full load/edit/apply cycle leaves isChanged() clean")
    void roundTripSettles() throws Exception {
        prefs().putBoolean("updateCheck", true);
        GeneralOptionsPanelController c = new GeneralOptionsPanelController();
        c.update();
        checkbox(c).setSelected(false);
        c.applyChanges();
        // after applying, the stored value matches the toggle again
        assertThat(c.isChanged()).isFalse();
    }

    @Test
    @DisplayName("The panel is stable and non-null; the controller reports valid")
    void panelAndValidity() {
        GeneralOptionsPanelController c = new GeneralOptionsPanelController();
        java.awt.Component first = c.getComponent(org.openide.util.Lookup.getDefault());
        java.awt.Component second = c.getComponent(org.openide.util.Lookup.getDefault());
        assertThat(first).isNotNull().isSameAs(second); // cached
        assertThat(c.isValid()).isTrue();
        c.cancel(); // no-op, must not throw
    }
}
