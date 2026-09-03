package org.nmox.studio.ui.whatsnew;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.util.Versions;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;

/**
 * What's New, in the product: the bundled CHANGELOG rendered as TEXT in a
 * dialog (a text area, never a label — the v1.306.0 html-render class),
 * opened from Help ▸ What's New… any time, and ONCE on the first boot after
 * an update with exactly the entries this install has not seen. The
 * last-seen version lives in the userdir (NbPreferences), so a fresh
 * userdir records silently instead of greeting a new user with a diff.
 */
public final class WhatsNew {

    private static final RequestProcessor RP = new RequestProcessor("What's New", 1, true);
    private static final String LAST_SEEN = "lastSeenVersion";
    private static volatile boolean firstBootHandled;

    private WhatsNew() {
    }

    /** The running product version ("1.0" for a dev build), or null when unbranded. */
    static String runningVersion() {
        try {
            String branded = java.util.ResourceBundle.getBundle("org.netbeans.core.startup.Bundle")
                    .getString("currentVersion");
            return Versions.extract(branded);
        } catch (RuntimeException missing) {
            return null;
        }
    }

    static Preferences prefs() {
        return NbPreferences.forModule(WhatsNew.class);
    }

    /** Reads the bundled notes; empty on a broken install, never a throw. */
    static String bundledChangelog() {
        try (InputStream in = ReleaseNotes.class.getResourceAsStream("CHANGELOG.md")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * The first-boot hook (Welcome's componentShowing, once per session):
     * the decision is the pure rule; SHOW opens the dialog with the unseen
     * entries and every path that decides anything records the running
     * version — so the dialog can never re-appear on the next boot.
     */
    public static void firstBoot() {
        if (firstBootHandled) {
            return;
        }
        firstBootHandled = true;
        String running = runningVersion();
        boolean stamped = Versions.isStamped(running);
        String lastSeen = prefs().get(LAST_SEEN, null);
        switch (ReleaseNotes.decide(running, lastSeen, stamped)) {
            case NONE -> { }
            case RECORD_ONLY -> prefs().put(LAST_SEEN, running);
            case SHOW -> {
                prefs().put(LAST_SEEN, running);
                show(lastSeen, running, "What's new since " + lastSeen);
            }
        }
    }

    /** Help ▸ What's New…: the running version's entry (or the head, on a dev build). */
    public static void showCurrent() {
        String running = runningVersion();
        show(null, Versions.isStamped(running) ? running : null, "What's new");
    }

    /** Off-EDT read of the bundle, EDT dialog. */
    private static void show(String lastSeen, String running, String title) {
        RP.post(() -> {
            List<ReleaseNotes.Entry> all = ReleaseNotes.parse(bundledChangelog());
            String text;
            if (all.isEmpty()) {
                text = "The release notes are missing from this install.";
            } else if (running == null) {
                text = ReleaseNotes.render(List.of(ReleaseNotes.head(all)), 0);
            } else if (lastSeen == null) {
                ReleaseNotes.Entry e = ReleaseNotes.entryFor(all, running);
                text = ReleaseNotes.render(List.of(e == null ? ReleaseNotes.head(all) : e), 0);
            } else {
                List<ReleaseNotes.Entry> unseen = ReleaseNotes.since(all, lastSeen, running);
                text = unseen.isEmpty()
                        ? "No release notes between " + lastSeen + " and " + running + "."
                        : ReleaseNotes.render(unseen, ReleaseNotes.omitted(all, lastSeen, running));
            }
            String finalText = text;
            SwingUtilities.invokeLater(() -> dialog(title, finalText));
        });
    }

    private static void dialog(String title, String text) {
        JTextArea area = new JTextArea(text, 28, 88);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        area.getAccessibleContext().setAccessibleName("Release notes");
        JScrollPane scroll = new JScrollPane(area);
        Object github = "Full notes on GitHub";
        Object close = "Close";
        NotifyDescriptor nd = new NotifyDescriptor(scroll, title, NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.PLAIN_MESSAGE, new Object[]{github, close}, close);
        if (DialogDisplayer.getDefault().notify(nd) == github) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(
                        "https://github.com/NMOX/NMOX-Studio/releases"));
            } catch (Exception ignored) {
                // no system browser wired up; the notes on screen already say what shipped
            }
        }
    }
}
