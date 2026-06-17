package org.nmox.studio.rack.service;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Manages trusted workspace directories. Running terminal/process tools (like npm,
 * databases, docker, make) on untrusted external repositories presents safety risks.
 */
public final class WorkspaceTrust {

    private static final String PREF_TRUSTED_KEYS = "trusted_workspaces";
    private static final Set<String> trustedPaths = new HashSet<>();
    private static final Preferences prefs = Preferences.userNodeForPackage(WorkspaceTrust.class);

    static {
        load();
    }

    private WorkspaceTrust() {
    }

    private static synchronized void load() {
        String data = prefs.get(PREF_TRUSTED_KEYS, "");
        if (!data.isEmpty()) {
            for (String path : data.split(File.pathSeparator)) {
                if (!path.isBlank()) {
                    trustedPaths.add(path.trim());
                }
            }
        }
    }

    private static synchronized void save() {
        String joined = String.join(File.pathSeparator, trustedPaths);
        prefs.put(PREF_TRUSTED_KEYS, joined);
    }

    /** True if the directory (or a parent) has been trusted by the user. */
    public static synchronized boolean isTrusted(File dir) {
        if (dir == null) {
            return false;
        }
        String absolute = dir.getAbsolutePath();
        for (String path : trustedPaths) {
            if (absolute.startsWith(path)) {
                return true;
            }
        }
        return false;
    }

    /** Adds the directory to the trusted list and persists it. */
    public static synchronized void trust(File dir) {
        if (dir != null) {
            trustedPaths.add(dir.getAbsolutePath());
            save();
        }
    }

    /** Prompts the user to trust the workspace folder (blocking Swing Dialog). */
    public static boolean requestTrust(File dir) {
        if (dir == null) {
            return false;
        }
        if (isTrusted(dir)) {
            return true;
        }
        // Workspace trust is an interactive guard: it asks a human before
        // running a stranger's tasks. With no human present - CI, tests,
        // headless/automation launches - there is no prompt to answer, and
        // silently denying would break every command instead of guarding
        // one. No interaction, no interactive attack to defend, so allow.
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return true;
        }

        final boolean[] result = new boolean[1];
        try {
            Runnable r = () -> {
                String message = "<html><b>Do you trust the files in this folder?</b><br><br>"
                        + "Opening untrusted folders can expose your machine to security risks if automated<br>"
                        + "tasks (like npm installations, watchers, database scripts, or compilers) run automatically.<br><br>"
                        + "Project: <code>" + dir.getAbsolutePath() + "</code></html>";
                
                int choice = JOptionPane.showOptionDialog(
                        null,
                        message,
                        "Workspace Trust Confirmation",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        new Object[]{"Yes, Trust Workspace", "No, Keep Safe"},
                        "No, Keep Safe"
                );
                
                if (choice == JOptionPane.YES_OPTION) {
                    trust(dir);
                    result[0] = true;
                } else {
                    result[0] = false;
                }
            };

            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeAndWait(r);
            }
        } catch (Exception ex) {
            return false;
        }
        return result[0];
    }
}
