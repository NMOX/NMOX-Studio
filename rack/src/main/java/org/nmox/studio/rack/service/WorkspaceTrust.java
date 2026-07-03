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

    /**
     * Legacy storage: every trusted path joined into ONE preference value with
     * {@link File#pathSeparator}. java.util.prefs caps a single value at
     * {@link Preferences#MAX_VALUE_LENGTH} (8 KB); a long-lived install that
     * trusted enough projects would overflow it and every subsequent trust()
     * would throw "Value too long". Read once for migration, then removed.
     */
    private static final String LEGACY_JOINED_KEY = "trusted_workspaces";
    private static final Set<String> trustedPaths = new HashSet<>();
    private static final Preferences prefs = Preferences.userNodeForPackage(WorkspaceTrust.class);
    /**
     * Current storage: one entry per trusted path under this child node —
     * key = a short stable hash (Preferences keys are capped at 80 chars, and
     * paths are not), value = the path (a single path is always well under the
     * per-value limit). This grows one small entry at a time and can never
     * overflow a value the way the joined string could.
     */
    private static final Preferences trustedNode = prefs.node("trusted");

    static {
        load();
    }

    private WorkspaceTrust() {
    }

    private static synchronized void load() {
        // Migrate the legacy joined value (if any) to per-path entries, then
        // drop it so the overflow-prone key never comes back.
        String legacy = prefs.get(LEGACY_JOINED_KEY, "");
        if (!legacy.isEmpty()) {
            for (String path : legacy.split(File.pathSeparator)) {
                if (!path.isBlank()) {
                    remember(path.trim());
                }
            }
            prefs.remove(LEGACY_JOINED_KEY);
            flush(prefs);
            flush(trustedNode);
        }
        try {
            for (String key : trustedNode.keys()) {
                String path = trustedNode.get(key, "");
                if (!path.isBlank()) {
                    trustedPaths.add(path);
                }
            }
        } catch (BackingStoreException ignore) {
            // best effort; the in-memory set still holds for this session
        }
    }

    /** Records one trusted path as its own preference entry (in memory + store). */
    private static void remember(String path) {
        trustedPaths.add(path);
        trustedNode.put(keyFor(path), path);
    }

    /** A short, stable, collision-resistant key for a path (fits the 80-char key cap). */
    private static String keyFor(String path) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(path.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(24);
            for (int i = 0; i < 12; i++) {
                sb.append(Character.forDigit((h[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(h[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            // SHA-256 is a required algorithm on every conformant JVM (JCA spec),
            // so this branch is unreachable — fail loudly rather than fall back
            // to a weaker key.
            throw new AssertionError("SHA-256 unavailable", impossible);
        }
    }

    private static void flush(Preferences node) {
        try {
            // Persist now. java.util.prefs flushes lazily / on clean exit, so
            // without this an abrupt quit loses the grant and the user is asked
            // to trust the same folder again on the next launch.
            node.flush();
        } catch (BackingStoreException ignore) {
            // best effort; the in-memory set still holds for this session
        }
    }

    /** Test hook: forget every trusted path, in memory and in the store. */
    static synchronized void clearForTest() {
        trustedPaths.clear();
        try {
            trustedNode.clear();
            prefs.remove(LEGACY_JOINED_KEY);
            flush(trustedNode);
            flush(prefs);
        } catch (BackingStoreException ignore) {
            // best effort
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
            remember(dir.getAbsolutePath());
            flush(trustedNode);
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
