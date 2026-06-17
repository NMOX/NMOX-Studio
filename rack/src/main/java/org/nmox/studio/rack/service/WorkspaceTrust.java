package org.nmox.studio.rack.service;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

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
        try {
            // Persist now. java.util.prefs flushes lazily / on clean exit, so
            // without this an abrupt quit loses the grant and the user is asked
            // to trust the same folder again on the next launch.
            prefs.flush();
        } catch (BackingStoreException ignore) {
            // best effort; the in-memory set still holds for this session
        }
    }

    /** True if the directory (or a parent) has been trusted by the user. */
    public static synchronized boolean isTrusted(File dir) {
        if (dir == null) {
            return false;
        }
        String absolute = dir.getAbsolutePath();
        for (String path : trustedPaths) {
            // match on a path boundary, so trusting /a/foo doesn't also
            // trust the unrelated sibling /a/foobar
            if (absolute.equals(path) || absolute.startsWith(path + File.separator)) {
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

        // Platform dialog (themed with the rest of the IDE), not a bare
        // Swing window; DialogDisplayer is safe to call from any thread and
        // blocks until the user answers.
        String message = "<html><b>Do you trust the files in this folder?</b><br><br>"
                + "Running this project's tasks — npm installs, watchers, database<br>"
                + "scripts, compilers — executes its code on your machine.<br><br>"
                + "Project: <code>" + dir.getAbsolutePath() + "</code></html>";
        Object trustOption = "Trust Workspace";
        NotifyDescriptor nd = new NotifyDescriptor(
                // a JLabel renders the HTML; a bare String is shown as raw text
                new javax.swing.JLabel(message),
                "Workspace Trust",
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.WARNING_MESSAGE,
                new Object[]{trustOption, "Keep Safe"},
                "Keep Safe");
        if (DialogDisplayer.getDefault().notify(nd) == trustOption) {
            trust(dir);
            return true;
        }
        return false;
    }
}
