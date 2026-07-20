package org.nmox.studio.ui;

import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.openide.modules.OnStart;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * The docs screenshot forge: boot the app with
 * {@code -J-Dnmox.shots.dir=/some/dir} and it cycles each suite tab,
 * paints the main window into a PNG per tab, and exits. Screenshots for
 * the tutorials become a one-command pipeline ({@code scripts/docs-shots.sh})
 * instead of a manual capture chore — re-runnable every release, so the
 * pictures never drift from the shipping UI.
 *
 * <p>The capture is Swing painting straight to an image — no OS
 * screen-recording permission, no window compositor, and pixel-perfect at
 * 2x for crisp docs. Without the property this is a single
 * {@code getProperty} at boot: the zero-boot-cost law holds.
 */
@OnStart
public class DocsShots implements Runnable {

    /** TopComponent preferredID → tutorial image name (insertion order = capture order). */
    static final Map<String, String> SHOTS = new LinkedHashMap<>();

    static {
        // the Workbench is the left-dock ProjectExplorer TC (display name
        // "Workbench") — MainWindowTopComponent is the Welcome launchpad
        SHOTS.put("ProjectExplorerTopComponent", "workbench.png");
        SHOTS.put("RackTopComponent", "the-task-rack.png");
        SHOTS.put("ProjectStudioTopComponent", "project-studio.png");
        SHOTS.put("DbStudioTopComponent", "db-studio.png");
        SHOTS.put("Web3StudioTopComponent", "contract-studio.png");
        SHOTS.put("InfraDesignerTopComponent", "infra-designer.png");
        SHOTS.put("ApiClientTopComponent", "api-studio.png");
        SHOTS.put("DockerPanelTopComponent", "docker-panel.png");
        SHOTS.put("BlockStudioTopComponent", "block-studio.png");
    }

    /** ms after selecting a tab before painting — lets componentShowing-deferred work land. */
    static final int SETTLE_MS = 2_500;
    /** ms after UI-ready before the first selection — lets the default-open set finish. */
    static final int WARMUP_MS = 5_000;

    @Override
    public void run() {
        String dir = System.getProperty("nmox.shots.dir");
        if (dir == null || dir.isBlank()) {
            return; // the normal boot: one property read, nothing else
        }
        File out = new File(dir);
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            javax.swing.Timer warmup = new javax.swing.Timer(WARMUP_MS,
                    e -> new Session(out).next());
            warmup.setRepeats(false);
            warmup.start();
        });
    }

    /** One capture run: walks SHOTS in order on the EDT, then exits the app. */
    private static final class Session {

        private final File dir;
        private final java.util.Iterator<Map.Entry<String, String>> queue =
                SHOTS.entrySet().iterator();

        Session(File dir) {
            this.dir = dir;
            if (!dir.isDirectory() && !dir.mkdirs()) {
                // no output dir → no run: exit honestly instead of failing
                // every capture with a confusing per-file IO error
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .warning("cannot create shots dir " + dir);
                org.openide.LifecycleManager.getDefault().exit();
            }
            // a consistent window size so every release's shots line up
            Frame main = WindowManager.getDefault().getMainWindow();
            main.setSize(1600, 1000);
            main.validate();
        }

        void next() {
            if (!queue.hasNext()) {
                org.openide.LifecycleManager.getDefault().exit();
                return;
            }
            Map.Entry<String, String> shot = queue.next();
            TopComponent tc = WindowManager.getDefault().findTopComponent(shot.getKey());
            if (tc == null) {
                next(); // module not installed here — skip, never stall the run
                return;
            }
            if (!tc.isOpened()) {
                tc.open(); // Block Studio is not in the default-open set
            }
            tc.requestActive();
            javax.swing.Timer settle = new javax.swing.Timer(SETTLE_MS, e -> {
                capture(shot.getValue());
                next();
            });
            settle.setRepeats(false);
            settle.start();
        }

        private void capture(String filename) {
            try {
                Frame main = WindowManager.getDefault().getMainWindow();
                int w = main.getWidth(), h = main.getHeight();
                if (w <= 0 || h <= 0) {
                    return; // never NPE the run on a hidden window
                }
                // 2x supersample: crisp text in the rendered docs
                BufferedImage img = new BufferedImage(w * 2, h * 2,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.scale(2, 2);
                main.paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(dir, filename));
            } catch (Exception ex) {
                // a failed shot must never wedge the run — the script's
                // missing-file check reports it honestly
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .warning("shot " + filename + " failed: " + ex);
            }
        }
    }
}
