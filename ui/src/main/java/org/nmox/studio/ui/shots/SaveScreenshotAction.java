package org.nmox.studio.ui.shots;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;

/**
 * Tools ▸ Save Screenshot… (v2.87.0): the whole IDE window, painted by
 * Swing at 2x, saved where you choose — the developer evangelist's slide
 * and social-post shot without an OS capture permission or a desktop
 * crop. The paint, the off-EDT write and the status line all live in
 * {@link ShotSaver}, shared with {@link SaveEditorScreenshotAction}.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.ui.shots.SaveScreenshotAction")
@ActionRegistration(displayName = "#CTL_SaveScreenshot", lazy = true)
@ActionReference(path = "Menu/Tools", position = 100)
@Messages("CTL_SaveScreenshot=Save Screenshot…")
public final class SaveScreenshotAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        ShotSaver.save(WindowManager.getDefault().getMainWindow(), "Save Screenshot",
                Screenshot.defaultFileName(LocalDateTime.now()), "screenshot");
    }
}
