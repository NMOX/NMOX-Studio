package org.nmox.studio.editor.blame;

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
 * View ▸ Line Blame — a checkbox that flips {@link BlamePrefs}, beside
 * Minimap and Sticky Scroll; the checkbox re-reads the preference each time
 * the menu shows.
 */
@ActionID(category = "View", id = "org.nmox.studio.editor.blame.ToggleLineBlameAction")
@ActionRegistration(displayName = "#CTL_ToggleLineBlame", lazy = false)
@ActionReference(path = "Menu/View", position = 1165)
@Messages("CTL_ToggleLineBlame=Line Blame")
public final class ToggleLineBlameAction extends AbstractAction implements Presenter.Menu {

    public ToggleLineBlameAction() {
        super(Bundle.CTL_ToggleLineBlame());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        BlamePrefs.setEnabled(!BlamePrefs.enabled());
    }

    @Override
    public JMenuItem getMenuPresenter() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(this) {
            @Override
            public void addNotify() {
                super.addNotify();
                setSelected(BlamePrefs.enabled());
            }
        };
        item.setSelected(BlamePrefs.enabled());
        return item;
    }
}
