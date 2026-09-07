package org.nmox.studio.ui.shots;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.util.PlainStatus;
import org.openide.awt.StatusDisplayer;
import org.openide.util.RequestProcessor;
import org.openide.windows.WindowManager;

/**
 * The one save path both screenshot gestures ride (v2.87.0): a save
 * chooser pre-filled with a time-sorted name, the paint on the EDT the
 * moment the chooser closes (Swing painting, taken before the chooser's
 * disposal repaints the window), the PNG write on a named
 * RequestProcessor so a slow disk never freezes the window it just
 * photographed (the no-disk-on-the-EDT law), and a status line that names
 * the file and its pixel size — or the reason nothing was saved. Refusals
 * speak; nothing here fails silently.
 */
@org.openide.util.NbBundle.Messages({
    "ShotSaver_fileName=Screenshot file",
    "ShotSaver_cancelled=Not saved — {0} cancelled",
    "ShotSaver_noSize=Not saved — the {0} target has no size to paint",
    "ShotSaver_saved=Saved {0} {1} ({2}×{3}, 2x)",
    "ShotSaver_failed=Not saved — {0}: {1}",
    "ShotSaver_copyNoSize=Not copied — the {0} target has no size to paint",
    "ShotSaver_copied=Copied {0} to the clipboard ({1}×{2}, 2x)"
})
final class ShotSaver {

    private static final RequestProcessor RP = new RequestProcessor("nmox-screenshot", 1, true);

    private ShotSaver() {
    }

    /**
     * EDT only. Asks where to save, then paints {@code target} at 2x and
     * writes it. {@code what} names the shot on the status line
     * ("screenshot", "editor screenshot").
     */
    static void save(Component target, String dialogTitle, String defaultName, String what) {
        Frame main = WindowManager.getDefault().getMainWindow();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(dialogTitle);
        chooser.setSelectedFile(new File(defaultDir(), defaultName));
        chooser.getAccessibleContext().setAccessibleName(Bundle.ShotSaver_fileName());
        if (chooser.showSaveDialog(main) != JFileChooser.APPROVE_OPTION) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.ShotSaver_cancelled(what)));
            return;
        }
        File file = chooser.getSelectedFile();
        // paint NOW, on the EDT, before the chooser's disposal repaints the window
        BufferedImage img = Screenshot.paint2x(target);
        if (img == null) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(
                    Bundle.ShotSaver_noSize(what)));
            return;
        }
        RP.post(() -> {
            String status;
            try {
                ImageIO.write(img, "png", file);
                status = Bundle.ShotSaver_saved(what, file.getName(),
                        String.valueOf(img.getWidth()), String.valueOf(img.getHeight()));
            } catch (IOException ex) {
                status = Bundle.ShotSaver_failed(what, ex.getMessage());
            }
            String s = status;
            SwingUtilities.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(s)));
        });
    }

    /**
     * EDT only. Paints {@code target} at 2x straight onto the system
     * clipboard as an image — no chooser, no disk — and says so. A target
     * with no size is a spoken refusal, never an empty clipboard.
     */
    static void copy(Component target, String what) {
        BufferedImage img = Screenshot.paint2x(target);
        if (img == null) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(
                    Bundle.ShotSaver_copyNoSize(what)));
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(img), null);
        StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.ShotSaver_copied(what,
                String.valueOf(img.getWidth()), String.valueOf(img.getHeight()))));
    }

    static File defaultDir() {
        File pictures = new File(System.getProperty("user.home"), "Pictures");
        return pictures.isDirectory() ? pictures : new File(System.getProperty("user.home"));
    }
}
