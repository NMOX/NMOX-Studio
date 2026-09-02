package org.nmox.studio.editor.minimap;

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
 * View ▸ Minimap — a checkbox that flips {@link MinimapPrefs}; the
 * checkbox reads the preference each time the menu shows, so it can never
 * disagree with the strips it controls.
 */
@ActionID(category = "View", id = "org.nmox.studio.editor.minimap.ToggleMinimapAction")
@ActionRegistration(displayName = "#CTL_ToggleMinimap", lazy = false)
@ActionReference(path = "Menu/View", position = 1150)
@Messages("CTL_ToggleMinimap=Minimap")
public final class ToggleMinimapAction extends AbstractAction implements Presenter.Menu {

    public ToggleMinimapAction() {
        super(Bundle.CTL_ToggleMinimap());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MinimapPrefs.setEnabled(!MinimapPrefs.enabled());
    }

    @Override
    public JMenuItem getMenuPresenter() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(this) {
            @Override
            public void addNotify() {
                super.addNotify();
                setSelected(MinimapPrefs.enabled());
            }
        };
        item.setSelected(MinimapPrefs.enabled());
        return item;
    }
}
