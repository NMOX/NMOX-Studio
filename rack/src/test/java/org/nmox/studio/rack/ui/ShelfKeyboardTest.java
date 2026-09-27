package org.nmox.studio.rack.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The device shelf without a mouse or a screen (3.4). Mounting was a
 * double-click or a drag, so a keyboard user could search for a device and
 * never rack it; and a screen reader heard each card's tooltip, which is
 * markup ({@code <html><b>MAESTRO</b> — …}), while section headers said
 * nothing at all. Enter and Space now mount exactly as a double-click does,
 * and every row reads as plain words.
 */
class ShelfKeyboardTest {

    private Rack rack;
    private PalettePanel shelf;
    private JList<?> list;

    @BeforeEach
    void setUp() throws Exception {
        rack = new Rack();
        SwingUtilities.invokeAndWait(() -> shelf = new PalettePanel(rack));
        list = find(shelf, JList.class);
        assertThat(list).as("the shelf's list").isNotNull();
    }

    @AfterEach
    void tearDown() {
        rack.shutdown();
    }

    private int firstIndex(boolean entry) {
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if ((list.getModel().getElementAt(i) instanceof DeviceCatalog.Entry) == entry) {
                return i;
            }
        }
        throw new AssertionError("no " + (entry ? "entry" : "header") + " on the shelf");
    }

    private void press(JComponent c, String key) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Object name = c.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key));
            assertThat(name).as(key + " is bound").isNotNull();
            c.getActionMap().get(name).actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, key));
        });
    }

    @Test
    @DisplayName("Enter on a selected card mounts that device at the bottom of the rack")
    void enterMounts() throws Exception {
        int i = firstIndex(true);
        DeviceCatalog.Entry entry = (DeviceCatalog.Entry) list.getModel().getElementAt(i);
        SwingUtilities.invokeAndWait(() -> list.setSelectedIndex(i));

        press(list, "ENTER");

        assertThat(rack.getDevices()).hasSize(1);
        assertThat(rack.getDevices().get(0).getTypeId()).isEqualTo(entry.id());
    }

    @Test
    @DisplayName("Space mounts too — the shelf is single-selection, so Space has nothing else to do")
    void spaceMounts() throws Exception {
        int i = firstIndex(true);
        SwingUtilities.invokeAndWait(() -> list.setSelectedIndex(i));
        press(list, "SPACE");
        assertThat(rack.getDevices()).hasSize(1);
    }

    @Test
    @DisplayName("Enter on a section header mounts nothing")
    void headerMountsNothing() throws Exception {
        int i = firstIndex(false);
        SwingUtilities.invokeAndWait(() -> list.setSelectedIndex(i));
        press(list, "ENTER");
        assertThat(rack.getDevices()).isEmpty();
    }

    @Test
    @DisplayName("a card reads as the device and its gloss, in plain words — never the tooltip's markup")
    void cardsReadPlain() {
        AccessibleContext shelfAc = list.getAccessibleContext();
        int i = firstIndex(true);
        DeviceCatalog.Entry entry = (DeviceCatalog.Entry) list.getModel().getElementAt(i);
        AccessibleContext card = ((Accessible) shelfAc.getAccessibleChild(i)).getAccessibleContext();

        assertThat(card.getAccessibleName()).isEqualTo(entry.title() + " — " + DeviceText.gloss(entry));
        assertThat(card.getAccessibleDescription()).isNotBlank().doesNotContain("<");
        assertThat(card.getAccessibleName()).doesNotContain("<");
    }

    @Test
    @DisplayName("every row is named — cards and section headers alike")
    void everyRowIsNamed() {
        AccessibleContext shelfAc = list.getAccessibleContext();
        int rows = list.getModel().getSize();
        assertThat(rows).isPositive();
        for (int i = 0; i < rows; i++) {
            AccessibleContext row = ((Accessible) shelfAc.getAccessibleChild(i)).getAccessibleContext();
            assertThat(row.getAccessibleName()).as("row " + i).isNotBlank().doesNotContain("<html");
            assertThat(row.getAccessibleDescription()).as("row " + i).isNotBlank().doesNotContain("<html");
        }
        int header = firstIndex(false);
        assertThat(((Accessible) shelfAc.getAccessibleChild(header)).getAccessibleContext().getAccessibleName())
                .isEqualTo(String.valueOf(list.getModel().getElementAt(header)));
    }

    @Test
    @DisplayName("Down in the search field lands on the first matching card")
    void downFromSearchSelectsTheFirstCard() throws Exception {
        JTextField search = find(shelf, JTextField.class);
        assertThat(search.getAccessibleContext().getAccessibleName()).isNotBlank();
        press(search, "DOWN");
        assertThat(list.getSelectedIndex()).isEqualTo(firstIndex(true));
    }

    @SuppressWarnings("unchecked")
    static <T extends Component> T find(Container root, Class<T> type) {
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
}
