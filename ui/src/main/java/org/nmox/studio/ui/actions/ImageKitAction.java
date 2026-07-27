package org.nmox.studio.ui.actions;

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
@ActionReference(path = "Menu/File", position = 121)
@Messages("CTL_ImageKitAction=Image Kit (Web)...")
public final class ImageKitAction implements ActionListener {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-image-kit", 1);

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Aim the studio at a project first (open a folder or project)."));
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
                    "No .jpg/.jpeg/.png images found in " + project.getName()
                    + " (node_modules and build outputs are skipped)."));
            return;
        }
        long totalBytes = found.stream().mapToLong(ImagePress.Candidate::bytes).sum();

        JCheckBox jpeg = new JCheckBox(
                "Re-encode JPEGs/PNGs → .min.jpg siblings (pure Java, kept only if smaller)", true);
        JComboBox<String> quality = new JComboBox<>(new String[]{
            "85 — visually lossless for photos",
            "80 — the web default",
            "70 — aggressive, check the results"
        });
        quality.setSelectedIndex(1);
        JComboBox<String> maxWidth = new JComboBox<>(new String[]{
            "No resize",
            "Max 2560 px wide (retina hero)",
            "Max 1600 px wide (content images)",
            "Max 800 px wide (thumbnails)"
        });
        JCheckBox webp = new JCheckBox(cwebp != null
                ? "WebP siblings via cwebp (found at " + cwebp.getName() + ")"
                : "WebP siblings — cwebp not on PATH (brew install webp), lane disabled",
                cwebp != null);
        webp.setEnabled(cwebp != null);

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
        rows.add(new JLabel(found.size() + " image" + (found.size() == 1 ? "" : "s")
                + ", " + mb(totalBytes) + " — outputs are siblings; originals untouched."));
        rows.add(jpeg);
        rows.add(new JLabel("    JPEG quality:"));
        rows.add(quality);
        rows.add(new JLabel("    Downscale:"));
        rows.add(maxWidth);
        rows.add(webp);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(rows, BorderLayout.CENTER);

        DialogDescriptor dd = new DialogDescriptor(panel, "Image Kit (Web) — "
                + project.getName());
        if (DialogDisplayer.getDefault().notify(dd) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        float q = switch (quality.getSelectedIndex()) {
            case 0 -> 0.85f;
            case 2 -> 0.70f;
            default -> 0.80f;
        };
        int width = switch (maxWidth.getSelectedIndex()) {
            case 1 -> 2560;
            case 2 -> 1600;
            case 3 -> 800;
            default -> 0;
        };
        boolean doJpeg = jpeg.isSelected();
        boolean doWebp = webp.isSelected() && cwebp != null;
        File cwebpFinal = cwebp;

        RP.post(() -> {
            org.netbeans.api.progress.ProgressHandle handle =
                    org.netbeans.api.progress.ProgressHandle.createHandle(
                            "Pressing images…");
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
            String summary = wrote + " file" + (wrote == 1 ? "" : "s") + " written, "
                    + mb(saved) + " saved. Originals untouched.\n"
                    + "Serve the smallest per browser:\n\n"
                    + ImagePress.pictureSnippet("example.jpg") + "\n\n";
            String body = summary + report;
            java.awt.EventQueue.invokeLater(() -> {
                JTextArea area = new JTextArea(body, 24, 78);
                area.setEditable(false);
                area.setCaretPosition(0);
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                        new javax.swing.JScrollPane(area)));
            });
        });
    }

    private static String line(ImagePress.Candidate c, ImagePress.Result r) {
        return c.file().getName() + " → "
                + (r.output() == null ? r.note()
                        : r.output().getName() + " (" + mb(r.before()) + " → "
                        + mb(r.after()) + ")") + "\n";
    }

    private static String mb(long bytes) {
        return bytes >= 1_000_000 ? String.format("%.1f MB", bytes / 1_000_000.0)
                : String.format("%d KB", Math.max(1, bytes / 1_000));
    }
}
