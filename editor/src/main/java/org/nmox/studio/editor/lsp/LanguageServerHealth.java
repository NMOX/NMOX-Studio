package org.nmox.studio.editor.lsp;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.nmox.studio.editor.lsp.LanguageServerCatalog.Server;
import org.openide.awt.NotificationDisplayer;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;

/**
 * Turns a missing language server from silence into a one-line answer.
 * When a file's server can't launch, the developer gets a single
 * notification — once per language per session — naming the binary and
 * the command to install it, click-to-copy. No nagging, no modal: just
 * the answer to "why is there no hover here?".
 */
public final class LanguageServerHealth {

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final Icon ICON = dot();

    /**
     * Languages whose missing LSP is NOT worth a notification (David's
     * review, 2026-08-11): the stylesheet family ships first-class
     * BUILT-IN intelligence — color swatches, design-token completion
     * with swatch icons, the ⌘-click token jump, the color picker, and
     * ⌥⌘E — so greeting a designer's first .css open with
     * "intelligence unavailable" undersells the product's own feature
     * set to nag about a server that is merely additive (stylelint-lsp
     * still attaches silently when present). Languages stay on this
     * list only when the built-ins genuinely cover the daily loop; for
     * Go or Rust the LSP IS the intelligence and the notification
     * stays.
     */
    private static final Set<String> QUIET_BINARIES = Set.of(
            "vscode-css-language-server");

    private LanguageServerHealth() {
    }

    /**
     * The TypeScript wall spoken once (v2.85.0): a typescript is installed
     * where the server looks, but it is 7+ — the Go port, no tsserver — so
     * the server would fail its initialize on every file open. The balloon
     * names the version, and its click runs the catalog's install (which
     * pins typescript@5) through the same trust-gated installer.
     */
    public static void reportNoTsserver(String version) {
        if (!REPORTED.add("typescript-language-server:no-tsserver")) {
            return;
        }
        Server s = LanguageServerCatalog.forBinary("typescript-language-server");
        String install = s != null ? s.install() : "npm install -g typescript@5";
        // the refusal speaks in the log too (a walk reads logs, not balloons)
        java.util.logging.Logger.getLogger(LanguageServerHealth.class.getName()).info(
                "TypeScript " + version + " ships no tsserver; the TypeScript server was not started — " + install);
        NotificationDisplayer.getDefault().notify(NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_tsTitle"), ICON,
                NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_tsBody", version,
                        clickInstalls(s) ? NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_tsClickInstall", install)
                                : NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_tsRun", install)),
                e -> {
                    if (clickInstalls(s)) {
                        runInstall(s);
                    } else {
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(install), null);
                        StatusDisplayer.getDefault().setStatusText(NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_copied", install));
                    }
                });
    }

    /** Called when a server binary failed to launch; notifies at most once per binary. */
    public static void reportMissing(String binary) {
        if (binary == null || QUIET_BINARIES.contains(binary)
                || !REPORTED.add(binary)) {
            return; // already told them, or built-ins cover this family
        }
        Server s = LanguageServerCatalog.forBinary(binary);
        String language = s != null ? s.language() : binary;
        String install = s != null ? s.install()
                : "install " + binary + " and put it on your PATH";
        String title = NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_title", language);
        NotificationDisplayer.getDefault().notify(title, ICON, detail(s, binary, install),
                e -> {
                    if (clickInstalls(s)) {
                        runInstall(s);
                    } else {
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(install), null);
                        StatusDisplayer.getDefault().setStatusText(NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_copied", install));
                    }
                });
    }

    /**
     * The zero-friction question (Angular-top arc): when the catalog
     * knows the exact argv AND its package manager is here, the click
     * should RUN the install — the trust-gated, project-aware
     * {@link LanguageServerInstaller} the Tools panel already uses —
     * instead of handing the developer a string to paste somewhere.
     * Everything else keeps click-to-copy.
     */
    static boolean clickInstalls(Server s) {
        return s != null && s.autoInstallable()
                && LanguageServerCatalog.isInstalled(s.installer());
    }

    /** The notification body, matched to what the click will actually do. */
    static String detail(Server s, String binary, String install) {
        if (clickInstalls(s)) {
            return s.projectLocal()
                    ? NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_clickInstallProject", binary, install)
                    : NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_clickInstall", binary, install);
        }
        return NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_clickCopy", binary, install);
    }

    private static void runInstall(Server s) {
        LanguageServerInstaller.install(s, new LanguageServerInstaller.Listener() {
            @Override
            public void onStarted(Server server) {
                status(NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_installing", server.binary()));
            }

            @Override
            public void onFinished(Server server, LanguageServerInstaller.Result result,
                    int exitCode) {
                switch (result) {
                    case INSTALLED -> {
                        // the LSP client resolves servers per open file, so a
                        // reopen is what actually starts the fresh install
                        REPORTED.remove(server.binary());
                        status(NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_installed", server.binary()));
                    }
                    case NEEDS_PROJECT -> status(NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_needsProject", server.binary()));
                    case NEEDS_TOOLCHAIN -> status(NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_needsToolchain", server.installer()));
                    default -> status(NbBundle.getMessage(LanguageServerHealth.class, "LanguageServerHealth_installFailed", server.binary(), String.valueOf(exitCode)));
                }
            }

            private void status(String text) {
                java.awt.EventQueue.invokeLater(() ->
                        StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(text)));
            }
        });
    }

    /** Forget the session's reports — so a freshly-installed server can re-notify if still missing. */
    static void resetForTest() {
        REPORTED.clear();
    }

    /** A small amber attention dot, so the notification needs no icon resource. */
    private static Icon dot() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(240, 196, 25));
        g.fillOval(3, 3, 10, 10);
        g.setColor(new Color(150, 120, 10));
        g.drawOval(3, 3, 10, 10);
        g.dispose();
        return new ImageIcon(img);
    }
}
