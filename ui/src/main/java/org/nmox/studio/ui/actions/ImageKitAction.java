package org.nmox.studio.ui.actions;

import org.nmox.studio.core.util.PlainText;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.nmox.studio.core.process.ToolLocator;
import org.nmox.studio.rack.projectstudio.ImagePress;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * The Image Kit: press the aimed project's images for the web —
 * smaller JPEGs by pure-Java re-encoding, optional downscale, and WebP
 * siblings via the user's own cwebp when it's installed. Originals are
 * never touched, outputs never clobber, and a press that saves nothing
 * says so instead of shipping a bigger "optimized" file.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.ImageKitAction")
@ActionRegistration(displayName = "#CTL_ImageKitAction")
@ActionReference(path = "Menu/File/AddToProject", position = 50)
@Messages({
    "CTL_ImageKitAction=Image Kit (Web)…",
    "ImageKitAction_aimFirst=Aim the studio at a project first (open a folder or project).",
    "ImageKitAction_noImages=No .jpg/.jpeg/.png images found in {0} (node_modules and build outputs are skipped).",
    "ImageKitAction_messageName=Message",
    "ImageKitAction_jpegBox=Re-encode JPEGs/PNGs → .min.jpg siblings (pure Java, kept only if smaller)",
    "ImageKitAction_quality85=85 — visually lossless for photos",
    "ImageKitAction_quality80=80 — the web default",
    "ImageKitAction_quality70=70 — aggressive, check the results",
    "ImageKitAction_qualityName=JPEG quality",
    "ImageKitAction_noResize=No resize",
    "ImageKitAction_max2560=Max 2560 px wide (retina hero)",
    "ImageKitAction_max1600=Max 1600 px wide (content images)",
    "ImageKitAction_max800=Max 800 px wide (thumbnails)",
    "ImageKitAction_downscaleName=Downscale",
    "ImageKitAction_webpFound=WebP siblings via cwebp (found at {0})",
    "ImageKitAction_webpMissing=WebP siblings — cwebp not on PATH (brew install webp), lane disabled",
    "ImageKitAction_imageSingular=image",
    "ImageKitAction_imagePlural=images",
    "ImageKitAction_scanSummary={0}, {1} — outputs are siblings; originals untouched.",
    "ImageKitAction_qualityLabel=    JPEG quality:",
    "ImageKitAction_downscaleLabel=    Downscale:",
    "ImageKitAction_title=Image Kit (Web) — {0}",
    "ImageKitAction_pressing=Pressing images…",
    "ImageKitAction_fileSingular=file",
    "ImageKitAction_filePlural=files",
    "ImageKitAction_summary={0} written, {1} saved. Originals untouched.\nServe the smallest per browser:\n\n{2}\n\n",
    "ImageKitAction_reportName=Image Kit report",
    "ImageKitAction_lineNote={0} → {1}",
    "ImageKitAction_lineWritten={0} → {1} ({2} → {3})",
    "ImageKitAction_sizeMb={0} MB",
    "ImageKitAction_sizeKb={0} KB"
})
public final class ImageKitAction implements ActionListener {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-image-kit", 1);

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.ImageKitAction_aimFirst()));
            return;
        }

        // the scan is a real disk walk (depth 12, up to 500 files) and
        // the cwebp probe stats PATH dirs — neither belongs on the EDT
        // (the v1.33.1/v1.115.0 law; a wedged mount must not freeze the
        // paint thread on a menu click). Probe on the RP, dialog on EDT.
        RP.post(() -> {
            List<ImagePress.Candidate> scanned = ImagePress.scan(project);
            // resolve() returns the bare name when nothing on PATH matches
            String resolved = ToolLocator.resolve("cwebp");
            File located = new File(resolved);
            File cwebpFound = located.isAbsolute() && located.canExecute() ? located : null;
            java.awt.EventQueue.invokeLater(() -> showDialog(project, scanned, cwebpFound));
        });
    }

    private void showDialog(File project, List<ImagePress.Candidate> found, File cwebp) {
        if (found.isEmpty()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    org.nmox.studio.core.util.PlainDialogs.plain(Bundle.ImageKitAction_noImages(project.getName()),
                    Bundle.ImageKitAction_messageName())));
            return;
        }
        long totalBytes = found.stream().mapToLong(ImagePress.Candidate::bytes).sum();

        JCheckBox jpeg = new JCheckBox(
                Bundle.ImageKitAction_jpegBox(), true);
        JComboBox<String> quality = new JComboBox<>(new String[]{
            Bundle.ImageKitAction_quality85(),
            Bundle.ImageKitAction_quality80(),
            Bundle.ImageKitAction_quality70()
        });
        quality.getAccessibleContext().setAccessibleName(Bundle.ImageKitAction_qualityName());
        quality.setSelectedIndex(1);
        JComboBox<String> maxWidth = new JComboBox<>(new String[]{
            Bundle.ImageKitAction_noResize(),
            Bundle.ImageKitAction_max2560(),
            Bundle.ImageKitAction_max1600(),
            Bundle.ImageKitAction_max800()
        });
        maxWidth.getAccessibleContext().setAccessibleName(Bundle.ImageKitAction_downscaleName());
        JCheckBox webp = new JCheckBox(PlainText.plain(cwebp != null
                ? Bundle.ImageKitAction_webpFound(cwebp.getName())
                : Bundle.ImageKitAction_webpMissing()),
                cwebp != null);
        webp.setEnabled(cwebp != null);

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
        rows.add(new JLabel(PlainText.plain(Bundle.ImageKitAction_scanSummary(
                org.nmox.studio.core.util.Plural.of(found.size(),
                        Bundle.ImageKitAction_imageSingular(), Bundle.ImageKitAction_imagePlural()),
                mb(totalBytes)))));
        rows.add(jpeg);
        rows.add(new JLabel(Bundle.ImageKitAction_qualityLabel()));
        rows.add(quality);
        rows.add(new JLabel(Bundle.ImageKitAction_downscaleLabel()));
        rows.add(maxWidth);
        rows.add(webp);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(rows, BorderLayout.CENTER);

        DialogDescriptor dd = new DialogDescriptor(panel,
                Bundle.ImageKitAction_title(project.getName()));
        if (DialogDisplayer.getDefault().notify(dd) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        float q = qualityFor(quality.getSelectedIndex());
        int width = maxWidthFor(maxWidth.getSelectedIndex());
        boolean doJpeg = jpeg.isSelected();
        boolean doWebp = webp.isSelected() && cwebp != null;
        File cwebpFinal = cwebp;

        RP.post(() -> {
            org.netbeans.api.progress.ProgressHandle handle =
                    org.netbeans.api.progress.ProgressHandle.createHandle(
                            Bundle.ImageKitAction_pressing());
            handle.start(found.size());
            StringBuilder report = new StringBuilder();
            long saved = 0;
            int wrote = 0;
            int i = 0;
            try {
                for (ImagePress.Candidate c : found) {
                    handle.progress(c.file().getName(), i++);
                    if (doJpeg) {
                        ImagePress.Result r = ImagePress.pressJpeg(c.file(), q, width);
                        saved += r.saved();
                        if (r.output() != null) {
                            wrote++;
                        }
                        report.append(line(c, r));
                    }
                    if (doWebp) {
                        ImagePress.Result r = ImagePress.pressWebp(c.file(), cwebpFinal, 80);
                        saved += r.saved();
                        if (r.output() != null) {
                            wrote++;
                        }
                        report.append(line(c, r));
                    }
                }
            } finally {
                handle.finish();
            }
            String summary = Bundle.ImageKitAction_summary(
                    org.nmox.studio.core.util.Plural.of(wrote,
                            Bundle.ImageKitAction_fileSingular(), Bundle.ImageKitAction_filePlural()),
                    mb(saved), ImagePress.pictureSnippet("example.jpg"));
            String body = summary + report;
            java.awt.EventQueue.invokeLater(() -> {
                JTextArea area = new JTextArea(body, 24, 78);
                area.getAccessibleContext().setAccessibleName(Bundle.ImageKitAction_reportName());
                area.setEditable(false);
                area.setCaretPosition(0);
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                        new javax.swing.JScrollPane(area)));
            });
        });
    }

    /** The dialog's quality choices, index → JPEG quality (85/80/70). */
    static float qualityFor(int selectedIndex) {
        return switch (selectedIndex) {
            case 0 -> 0.85f;
            case 2 -> 0.70f;
            default -> 0.80f;
        };
    }

    /** The dialog's downscale choices, index → max width px (0 = no resize). */
    static int maxWidthFor(int selectedIndex) {
        return switch (selectedIndex) {
            case 1 -> 2560;
            case 2 -> 1600;
            case 3 -> 800;
            default -> 0;
        };
    }

    /** One report row: the written sibling with sizes, or the honest refusal note. */
    static String line(ImagePress.Candidate c, ImagePress.Result r) {
        return (r.output() == null
                ? Bundle.ImageKitAction_lineNote(c.file().getName(), r.note())
                : Bundle.ImageKitAction_lineWritten(c.file().getName(), r.output().getName(),
                        mb(r.before()), mb(r.after()))) + "\n";
    }

    /** Human sizes: MB with one decimal above a megabyte, else KB (floor 1). */
    static String mb(long bytes) {
        return bytes >= 1_000_000
                ? Bundle.ImageKitAction_sizeMb(org.nmox.studio.core.util.Numbers.display(bytes / 1_000_000.0, 1))
                : Bundle.ImageKitAction_sizeKb(String.valueOf(Math.max(1, bytes / 1_000)));
    }
}
