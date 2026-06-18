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

    private LanguageServerHealth() {
    }

    /** Called when a server binary failed to launch; notifies at most once per binary. */
    public static void reportMissing(String binary) {
        if (binary == null || !REPORTED.add(binary)) {
            return; // already told them this session
        }
        Server s = LanguageServerCatalog.forBinary(binary);
        String language = s != null ? s.language() : binary;
        String install = s != null ? s.install()
                : "install " + binary + " and put it on your PATH";
        String title = language + " intelligence unavailable";
        String detail = "Install " + binary
                + " for go-to-definition, hover, rename and live errors  —  click to copy: "
                + install;
        NotificationDisplayer.getDefault().notify(title, ICON, detail, e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(install), null);
            StatusDisplayer.getDefault().setStatusText("Copied: " + install);
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
