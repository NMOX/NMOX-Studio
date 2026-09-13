package org.nmox.studio.ui.rtl;

import java.awt.ComponentOrientation;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sweep is blunt on purpose, so the ledger has to be exact.
 *
 * <p>{@code applyComponentOrientation} walks a whole tree. A painted surface
 * inside that tree must come back the way it was authored, and an ordinary
 * component beside it must not — otherwise a rack faceplate mirrors while its
 * toolbar does, or nothing mirrors at all. Both halves are asserted here, and
 * the ledger is matched by SIMPLE NAME, which is why these fixtures are named
 * after the real classes.
 */
class PaintedSurfacesTest {

    /** Named for the real rack control: the ledger matches simple names. */
    static final class Knob extends JPanel {
    }

    /** Named for the real Welcome: classified OWED, and treated the same way. */
    static final class MainWindow extends JPanel {
    }

    static final class OrdinaryPanel extends JPanel {
    }

    @Test
    @DisplayName("a painted surface keeps the direction it was painted in")
    void geometryIsPutBack() {
        JPanel root = new JPanel();
        Knob knob = new Knob();
        OrdinaryPanel plain = new OrdinaryPanel();
        JLabel text = new JLabel("hello");
        plain.add(text);
        root.add(knob);
        root.add(plain);

        root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        PaintedSurfaces.keepAuthoredDirection(root);

        assertThat(knob.getComponentOrientation().isLeftToRight())
                .as("a faceplate control paints its own coordinates").isTrue();
        assertThat(plain.getComponentOrientation().isLeftToRight())
                .as("an ordinary panel mirrors").isFalse();
        assertThat(text.getComponentOrientation().isLeftToRight())
                .as("and so does the text inside it").isFalse();
    }

    @Test
    @DisplayName("an OWED surface is put back too — half a mirror is worse than none")
    void owedIsPutBackUntilItIsPaid() {
        JPanel root = new JPanel();
        MainWindow welcome = new MainWindow();
        root.add(welcome);

        root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        PaintedSurfaces.keepAuthoredDirection(root);

        assertThat(welcome.getComponentOrientation().isLeftToRight()).isTrue();
    }

    @Test
    @DisplayName("a painted surface nested deep is still found")
    void theWalkIsRecursive() {
        JPanel root = new JPanel();
        JPanel mid = new JPanel();
        Knob deep = new Knob();
        mid.add(deep);
        root.add(mid);

        root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        PaintedSurfaces.keepAuthoredDirection(root);

        assertThat(deep.getComponentOrientation().isLeftToRight()).isTrue();
        assertThat(mid.getComponentOrientation().isLeftToRight())
                .as("its parent is ordinary and mirrors").isFalse();
    }

    @Test
    @DisplayName("every classified name is a real painted surface's name")
    void theLedgerNamesAreSimpleNames() {
        assertThat(PaintedSurfaces.GEOMETRY).contains("RackDevice", "FlowCanvas", "Knob");
        assertThat(PaintedSurfaces.OWED).contains("MainWindow", "PalettePanel");
        assertThat(PaintedSurfaces.GEOMETRY)
                .as("a surface cannot be both kinds of decision")
                .doesNotContainAnyElementsOf(PaintedSurfaces.OWED);
    }
}
