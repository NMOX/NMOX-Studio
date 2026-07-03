package org.nmox.studio.rack.options;

import javax.swing.JComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.ReflexDevice;
import org.openide.util.NbPreferences;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tools → Options → Rack & Cloud: the controller round-trips the REFLEX
 * poll interval through NbPreferences and reports its trivial validity
 * contract. The token fields are write-only (blank keeps the stored
 * value), so we exercise the interval, which is safe to read back.
 */
class RackOptionsPanelControllerTest {

    @Test
    @DisplayName("The panel builds and is a JComponent")
    void buildsPanel() {
        RackOptionsPanelController c = new RackOptionsPanelController();
        JComponent comp = c.getComponent(org.openide.util.Lookup.EMPTY);
        assertThat(comp).isNotNull();
        // idempotent: a second call returns the same cached panel
        assertThat(c.getComponent(org.openide.util.Lookup.EMPTY)).isSameAs(comp);
    }

    @Test
    @DisplayName("isValid and isChanged report their fixed contract")
    void validityContract() {
        RackOptionsPanelController c = new RackOptionsPanelController();
        assertThat(c.isValid()).isTrue();
        assertThat(c.isChanged()).isTrue();
        assertThat(c.getHelpCtx()).isNotNull();
    }

    @Test
    @DisplayName("update loads the stored REFLEX interval; applyChanges writes it back")
    void reflexIntervalRoundTrips() {
        var prefs = NbPreferences.forModule(ReflexDevice.class);
        int original = prefs.getInt("reflexIntervalMs", 1200);
        try {
            prefs.putInt("reflexIntervalMs", 3400);

            RackOptionsPanelController c = new RackOptionsPanelController();
            c.update(); // pulls 3400 into the spinner
            // change the stored value out from under it, then applyChanges
            // must write the spinner's 3400 back (not read 9999)
            prefs.putInt("reflexIntervalMs", 9999);
            c.applyChanges();
            assertThat(prefs.getInt("reflexIntervalMs", -1)).isEqualTo(3400);
        } finally {
            prefs.putInt("reflexIntervalMs", original);
        }
    }

    @Test
    @DisplayName("Listener registration and cancel are inert but callable")
    void listenersAndCancel() {
        RackOptionsPanelController c = new RackOptionsPanelController();
        java.beans.PropertyChangeListener l = evt -> { };
        c.addPropertyChangeListener(l);
        c.removePropertyChangeListener(l);
        c.cancel(); // no-op, must not throw
    }
}
