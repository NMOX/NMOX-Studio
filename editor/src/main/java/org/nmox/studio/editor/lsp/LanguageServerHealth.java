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
        String title = language + " intelligence unavailable";
        NotificationDisplayer.getDefault().notify(title, ICON, detail(s, binary, install),
                e -> {
                    if (clickInstalls(s)) {
                        runInstall(s);
                    } else {
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(install), null);
                        StatusDisplayer.getDefault().setStatusText("Copied: " + install);
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
            return "Click to install " + binary
                    + (s.projectLocal() ? " into the project" : "")
                    + " — runs: " + install;
        }
        return "Install " + binary
                + " for go-to-definition, hover, rename and live errors  —  click to copy: "
                + install;
    }

    private static void runInstall(Server s) {
        LanguageServerInstaller.install(s, new LanguageServerInstaller.Listener() {
            @Override
            public void onStarted(Server server) {
                status("Installing " + server.binary() + "…");
            }

            @Override
            public void onFinished(Server server, LanguageServerInstaller.Result result,
                    int exitCode) {
                switch (result) {
                    case INSTALLED -> {
                        // the LSP client resolves servers per open file, so a
                        // reopen is what actually starts the fresh install
                        REPORTED.remove(server.binary());
                        status("Installed " + server.binary()
                                + " — reopen the file to start it");
                    }
                    case NEEDS_PROJECT -> status("Open the project first — "
                            + server.binary() + " installs into the project");
                    case NEEDS_TOOLCHAIN -> status(server.installer()
                            + " not found — install it first");
                    default -> status("Install of " + server.binary()
                            + " failed (exit " + exitCode + ") — see Output");
                }
            }

            private void status(String text) {
                java.awt.EventQueue.invokeLater(() ->
                        StatusDisplayer.getDefault().setStatusText(text));
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
