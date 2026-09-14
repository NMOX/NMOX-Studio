package org.nmox.studio.ui.actions;

import java.awt.ComponentOrientation;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The learning-space picker's rows stay inside their viewport (v2.151.0).
 *
 * <p>The first Hebrew walk photographed every row cut off at the left edge:
 * the list had grown to its widest row, and a right-aligned row loses its END
 * to that overshoot. The list follows the viewport's width instead.
 */
class PickerListWidthTest {

    @Test
    @DisplayName("a long row does not widen the list past its viewport, in either direction")
    void rowsNeverOutgrowTheViewport() {
        for (ComponentOrientation o : new ComponentOrientation[] {
                ComponentOrientation.LEFT_TO_RIGHT, ComponentOrientation.RIGHT_TO_LEFT}) {
            DefaultListModel<String> model = new DefaultListModel<>();
            model.addElement("short");
            model.addElement("דף הווב הראשון שלכם — ".repeat(12));
            JList<String> list = NewLearningSpaceAction.pickerList(model);
            JScrollPane scroll = new JScrollPane(list);
            scroll.applyComponentOrientation(o);
            scroll.setSize(320, 200);
            scroll.doLayout();
            scroll.getViewport().doLayout();

            assertThat(list.getWidth())
                    .as("%s: the list is exactly as wide as the viewport that shows it",
                            o.isLeftToRight() ? "left to right" : "right to left")
                    .isEqualTo(scroll.getViewport().getWidth());
        }
    }
}
