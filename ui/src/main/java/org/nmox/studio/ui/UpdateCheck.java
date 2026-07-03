package org.nmox.studio.ui;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.prefs.Preferences;
import org.nmox.studio.core.http.HttpClientFactory;
import org.nmox.studio.core.util.Versions;
import org.openide.modules.OnStart;
import org.openide.util.NbPreferences;

/**
 * A daily driver should mention when a newer release exists. Once per
 * day, well after startup and entirely off the EDT, this asks GitHub
 * for the latest release tag and — only if it outranks the stamped
 * version this build carries — shows one quiet notification linking to
 * the release page. Dev builds (unstamped "1.0") never check; offline
 * failures never nag; the {@code updateCheck} preference turns it off.
 */
@OnStart
public class UpdateCheck implements Runnable {

    static final String RELEASES_API =
            "https://api.github.com/repos/NMOX/NMOX-Studio/releases/latest";
    static final String RELEASES_PAGE =
            "https://github.com/NMOX/NMOX-Studio/releases";

    @Override
    public void run() {
        Preferences prefs = NbPreferences.forModule(UpdateCheck.class);
        if (!prefs.getBoolean("updateCheck", true)) {
            return;
        }
        long last = prefs.getLong("updateCheck.lastRun", 0);
        if (System.currentTimeMillis() - last < Duration.ofDays(1).toMillis()) {
            return;
        }
        String running = Versions.extract(currentVersion());
        if (!Versions.isStamped(running)) {
            return; // dev build: nothing meaningful to compare
        }
        org.openide.windows.WindowManager.getDefault().invokeWhenUIReady(() ->
                org.openide.util.RequestProcessor.getDefault().post(
                        () -> check(prefs, running), 15_000));
    }

    private void check(Preferences prefs, String running) {
        prefs.putLong("updateCheck.lastRun", System.currentTimeMillis());
        try {
            HttpResponse<String> response = HttpClientFactory.shared().send(
                    HttpRequest.newBuilder(URI.create(RELEASES_API))
                            .timeout(Duration.ofSeconds(10))
                            .header("Accept", "application/vnd.github+json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }
            String latest = latestTag(response.body());
            if (latest == null || Versions.compare(running, latest) >= 0) {
                return;
            }
            javax.swing.SwingUtilities.invokeLater(() ->
                    org.openide.awt.NotificationDisplayer.getDefault().notify(
                            "NMOX Studio " + latest + " is available",
                            javax.swing.UIManager.getIcon("OptionPane.informationIcon"),
                            "You're on " + running + ". Click to open the releases page.",
                            e -> openReleases()));
        } catch (Exception offline) {
            // no network, rate-limited, whatever — a check must never nag
        }
    }

    /** Pulls the version out of the tag_name field without a JSON dependency. */
    static String latestTag(String body) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"tag_name\"\\s*:\\s*\"v?([0-9][0-9.]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** The branded product version — stamped by the release workflow. */
    static String currentVersion() {
        try {
            return java.util.ResourceBundle
                    .getBundle("org.netbeans.core.startup.Bundle")
                    .getString("currentVersion");
        } catch (RuntimeException missing) {
            return null;
        }
    }

    private static void openReleases() {
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(RELEASES_PAGE));
        } catch (Exception ignored) {
            // the notification text carries enough to find it by hand
        }
    }
}
