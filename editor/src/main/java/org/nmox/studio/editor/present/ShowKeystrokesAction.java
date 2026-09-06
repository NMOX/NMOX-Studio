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
 * View ▸ Show Keystrokes — the checkbox that flips {@link KeystrokeHud},
 * beside Presentation Mode; it reads the live state each time the menu
 * shows. Menu-only, like its sibling: a chord to toggle the chord display
 * would be the first thing it displayed.
 */
@ActionID(category = "View", id = "org.nmox.studio.editor.present.ShowKeystrokesAction")
@ActionRegistration(displayName = "#CTL_ShowKeystrokes", lazy = false)
@ActionReference(path = "Menu/View", position = 1180)
@Messages("CTL_ShowKeystrokes=Show Keystrokes")
public final class ShowKeystrokesAction extends AbstractAction implements Presenter.Menu {

    public ShowKeystrokesAction() {
        super(Bundle.CTL_ShowKeystrokes());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        KeystrokeHud.setOn(!KeystrokeHud.isOn());
    }

    @Override
    public JMenuItem getMenuPresenter() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(this) {
            @Override
            public void addNotify() {
                super.addNotify();
                setSelected(KeystrokeHud.isOn());
            }
        };
        item.setSelected(KeystrokeHud.isOn());
        return item;
    }
}
