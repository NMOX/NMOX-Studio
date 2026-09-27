package org.nmox.studio.rack;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.ui.PalettePanel;
import org.nmox.studio.rack.ui.RackPanel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tab in the Task Rack (3.4). The window flips the rack front-to-rear on
 * Tab, and the dispatcher that does it flipped on ANY Tab whose focus was
 * not inside the rack panel — the device shelf and its search field sit
 * outside it, so a keyboard user on the shelf could never Tab to the rack:
 * every Tab turned it around instead, and only Shift+Tab escaped. The flip
 * stays where nothing is being operated; anywhere a real control holds
 * focus, Tab moves focus.
 */
class RackTabFocusTest {

    @SuppressWarnings("unchecked")
    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return (T) c;
            }
            if (c instanceof Container k) {
                T hit = find(k, type);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("Tab moves focus from the shelf and its search; it flips the rack only where nothing is operated")
    void tabFlipsOnlyWhereNothingIsOperated() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RackTopComponent tc = new RackTopComponent();
            RackPanel rackPanel = find(tc, RackPanel.class);
            PalettePanel shelf = find(tc, PalettePanel.class);
            JList<?> list = find(shelf, JList.class);
            JTextField search = find(shelf, JTextField.class);
            assertThat(rackPanel).isNotNull();

            assertThat(RackTopComponent.tabFlipsRack(search, rackPanel, tc))
                    .as("Tab in the shelf's search moves focus").isFalse();
            assertThat(RackTopComponent.tabFlipsRack(list, rackPanel, tc))
                    .as("Tab on the shelf moves focus").isFalse();

            org.nmox.studio.rack.ui.controls.Knob knob = new org.nmox.studio.rack.ui.controls.Knob("GAIN", 0.5);
            rackPanel.add(knob);
            assertThat(RackTopComponent.tabFlipsRack(knob, rackPanel, tc))
                    .as("Tab on a faceplate control moves focus (the v1.41.0 law, kept)").isFalse();

            assertThat(RackTopComponent.tabFlipsRack(null, rackPanel, tc))
                    .as("nothing focused: Tab flips").isTrue();
            assertThat(RackTopComponent.tabFlipsRack(rackPanel, rackPanel, tc))
                    .as("the rack itself focused (after a click on a faceplate): Tab flips").isTrue();
            assertThat(RackTopComponent.tabFlipsRack(tc, rackPanel, tc))
                    .as("the window itself focused: Tab flips").isTrue();

            JLabel inert = new JLabel("x");
            inert.setFocusable(false);
            shelf.add(inert);
            assertThat(RackTopComponent.tabFlipsRack(inert, rackPanel, tc))
                    .as("something that cannot hold focus is not a control").isTrue();

            JPanel elsewhere = new JPanel();
            assertThat(RackTopComponent.tabFlipsRack(elsewhere, rackPanel, tc))
                    .as("focus outside the window: Tab flips, as before").isTrue();
        });
    }
}
