package org.nmox.studio.ui.shots;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import org.nmox.studio.core.util.PlainStatus;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.WindowManager;

/**
 * Tools ▸ Save Screenshot… (v2.87.0): the whole IDE window, painted by
 * Swing at 2x, saved where you choose — the developer evangelist's slide
 * and social-post shot without an OS capture permission or a desktop
 * crop. The paint happens on the EDT (Swing); the PNG write rides a
 * named RequestProcessor so a slow disk never freezes the window it just
 * photographed (the no-disk-on-the-EDT law). The status line names the
 * file, or the reason nothing was saved.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.ui.shots.SaveScreenshotAction")
@ActionRegistration(displayName = "#CTL_SaveScreenshot", lazy = true)
@ActionReference(path = "Menu/Tools", position = 100)
@Messages("CTL_SaveScreenshot=Save Screenshot…")
public final class SaveScreenshotAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("nmox-screenshot", 1, true);

    @Override
    public void actionPerformed(ActionEvent e) {
        Frame main = WindowManager.getDefault().getMainWindow();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Screenshot");
        chooser.setSelectedFile(new File(defaultDir(), Screenshot.defaultFileName(LocalDateTime.now())));
        chooser.getAccessibleContext().setAccessibleName("Screenshot file");
        if (chooser.showSaveDialog(main) != JFileChooser.APPROVE_OPTION) {
            StatusDisplayer.getDefault().setStatusText("Screenshot not saved");
            return;
        }
        File target = chooser.getSelectedFile();
        // paint NOW, on the EDT, before the chooser's disposal repaints the window
        BufferedImage img = Screenshot.paint2x(main);
        if (img == null) {
            StatusDisplayer.getDefault().setStatusText("Screenshot not saved — the window has no size to paint");
            return;
        }
        RP.post(() -> {
            String status;
            try {
                ImageIO.write(img, "png", target);
                status = "Saved screenshot " + target.getName() + " (" + img.getWidth() + "×" + img.getHeight() + ", 2x)";
            } catch (IOException ex) {
                status = "Screenshot not saved — " + ex.getMessage();
            }
            String s = status;
            javax.swing.SwingUtilities.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(s)));
        });
    }

    static File defaultDir() {
        File pictures = new File(System.getProperty("user.home"), "Pictures");
        return pictures.isDirectory() ? pictures : new File(System.getProperty("user.home"));
    }
}
