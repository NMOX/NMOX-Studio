package org.nmox.studio.editor.sticky;

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
 * View ▸ Sticky Scroll — a checkbox that flips {@link StickyPrefs}; the
 * checkbox re-reads the preference each time the menu shows.
 */
@ActionID(category = "View", id = "org.nmox.studio.editor.sticky.ToggleStickyScrollAction")
@ActionRegistration(displayName = "#CTL_ToggleStickyScroll", lazy = false)
@ActionReference(path = "Menu/View", position = 1160)
@Messages("CTL_ToggleStickyScroll=Sticky Scroll")
public final class ToggleStickyScrollAction extends AbstractAction implements Presenter.Menu {

    public ToggleStickyScrollAction() {
        super(Bundle.CTL_ToggleStickyScroll());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        StickyPrefs.setEnabled(!StickyPrefs.enabled());
    }

    @Override
    public JMenuItem getMenuPresenter() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(this) {
            @Override
            public void addNotify() {
                super.addNotify();
                setSelected(StickyPrefs.enabled());
            }
        };
        item.setSelected(StickyPrefs.enabled());
        return item;
    }
}
