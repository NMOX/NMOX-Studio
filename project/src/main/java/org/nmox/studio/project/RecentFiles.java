package org.nmox.studio.project;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * The developer's trail: every file they've had in the editor, most
 * recent first, deduped, capped, persisted across restarts. The
 * Workbench renders it so "where was I?" is answered before the
 * question forms.
 */
public final class RecentFiles {

    static final int CAP = 20;
    private static final String PREF_KEY = "recentFiles";

    private RecentFiles() {
    }

    /** Records a file the user just opened or focused. */
    public static void record(File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        Preferences prefs = prefs();
        prefs.put(PREF_KEY, push(prefs.get(PREF_KEY, ""), file.getAbsolutePath(), CAP));
    }

    /** Most recent first; entries whose files vanished are dropped. */
    public static List<File> list() {
        List<File> result = new ArrayList<>();
        for (String path : prefs().get(PREF_KEY, "").split("\n")) {
            if (!path.isBlank()) {
                File f = new File(path);
                if (f.isFile()) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    /**
     * The pure list discipline, testable without a platform: newline-
     * separated paths, newest first, no duplicates, at most {@code cap}.
     */
    static String push(String csv, String path, int cap) {
        StringBuilder sb = new StringBuilder(path);
        int kept = 1;
        for (String existing : csv.split("\n")) {
            if (existing.isBlank() || existing.equals(path) || kept >= cap) {
                continue;
            }
            sb.append('\n').append(existing);
            kept++;
        }
        return sb.toString();
    }

    private static Preferences prefs() {
        return NbPreferences.forModule(RecentFiles.class);
    }
}
