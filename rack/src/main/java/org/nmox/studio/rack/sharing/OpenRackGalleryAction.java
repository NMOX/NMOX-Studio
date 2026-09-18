package org.nmox.studio.rack.sharing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.nmox.studio.rack.RackTopComponent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Tools ▸ Rack Gallery… — the gallery's door outside the rack window, so it is
 * findable from the menu bar and from Quick Search without first knowing that
 * the rack's Presets button opens it. Zero boot cost: a lazy menu item. It
 * fronts the Task Rack (the gallery mounts INTO it, so the reader should see
 * where the rack lands) and asks that window to show the gallery — one
 * implementation, two doors.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.rack.sharing.OpenRackGalleryAction")
@ActionRegistration(displayName = "#CTL_OpenRackGalleryAction", lazy = true)
@ActionReference(path = "Menu/Tools", position = 93)
@Messages("CTL_OpenRackGalleryAction=Rack Gallery…")
public final class OpenRackGalleryAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        TopComponent tc = WindowManager.getDefault().findTopComponent("RackTopComponent");
        if (tc instanceof RackTopComponent rackWindow) {
            rackWindow.open();
            rackWindow.requestActive();
            rackWindow.showGallery();
        }
    }
}
