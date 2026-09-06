package org.nmox.studio.editor.present;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.Presenter;

/**
 * View ▸ Presentation Mode — a checkbox that flips {@link PresentationMode};
 * the checkbox reads the live state each time the menu shows, so it can
 * never disagree with the editors it controls. Menu-only by design (the
 * v1.38.1 keymap laws): a presenter finds it under View, and the platform
 * ⌥-wheel still fine-tunes on top.
 */
@ActionID(category = "View", id = "org.nmox.studio.editor.present.PresentationModeAction")
@ActionRegistration(displayName = "#CTL_PresentationMode", lazy = false)
@ActionReference(path = "Menu/View", position = 1170)
@Messages("CTL_PresentationMode=Presentation Mode")
public final class PresentationModeAction extends AbstractAction implements Presenter.Menu {

    public PresentationModeAction() {
        super(Bundle.CTL_PresentationMode());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        PresentationMode.setOn(!PresentationMode.isOn());
    }

    @Override
    public JMenuItem getMenuPresenter() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(this) {
            @Override
            public void addNotify() {
                super.addNotify();
                setSelected(PresentationMode.isOn());
            }
        };
        item.setSelected(PresentationMode.isOn());
        return item;
    }
}
