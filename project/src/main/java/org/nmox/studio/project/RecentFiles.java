package org.nmox.studio.project;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;

/**
 * The developer's trail: every file they've had in the editor, most
 * recent first, deduped, capped, persisted across restarts. The
 * Workbench renders it so "where was I?" is answered before the
 * question forms.
 *
 * <p>Threading: the tracker calls {@link #record} from the window
 * registry's PROP_ACTIVATED — the EDT, on every tab switch. The stat,
 * the read-modify-write, and the synchronous {@code prefs.flush()} (a
 * backing-store disk write) therefore all ride {@link #RP}, a dedicated
 * single-thread lane that also serializes them against each other.
 * Callers on the EDT pay only for posting a task. Readers use
 * {@link #listRaw} (a pure pref parse, no filesystem I/O — EDT-safe)
 * and let {@link #pruneAsync} drop vanished entries off the EDT.
 */
public final class RecentFiles {

    static final int CAP = 20;
    private static final String PREF_KEY = "recentFiles";

    /** One lane for every stat + pref write; FIFO keeps trail order true. */
    private static final RequestProcessor RP =
            new RequestProcessor("nmox-recent-files", 1);

    private RecentFiles() {
    }

    /**
     * Records a file the user just opened or focused. Cheap on the
     * caller's thread (the EDT): all I/O — the {@code isFile} stat, the
     * pref update, and the crash-proofing flush — runs on {@link #RP}.
     */
    public static void record(File file) {
        if (file == null) {
            return;
        }
        RP.post(() -> {
            if (!file.isFile()) {
                return;
            }
            Preferences prefs = prefs();
            prefs.put(PREF_KEY, push(prefs.get(PREF_KEY, ""), file.getAbsolutePath(), CAP));
            try {
                // persist now so a crash doesn't lose the trail before the
                // backing store's lazy flush timer fires
                prefs.flush();
            } catch (java.util.prefs.BackingStoreException ignore) {
                // best effort
            }
        });
    }

    /**
     * The trail exactly as stored — most recent first, no filesystem
     * stats, so it is safe to call during an EDT paint/refresh. Entries
     * whose files have vanished may still appear until {@link #pruneAsync}
     * sweeps them; rendering a soon-to-vanish row for one beat is the
     * price of never statting a (possibly hung network) path on the EDT.
     */
    public static List<File> listRaw() {
        List<File> result = new ArrayList<>();
        for (String path : prefs().get(PREF_KEY, "").split("\n")) {
            if (!path.isBlank()) {
                result.add(new File(path));
            }
        }
        return result;
    }

    /**
     * Sweeps vanished files from the trail off the EDT. If anything was
     * dropped, the pref is rewritten and {@code onChanged} runs on the
     * EDT — callers pass their coalesced refresh so the visible list
     * self-heals one beat later. A clean sweep calls nothing, so
     * prune → refresh → prune converges instead of storming.
     */
    public static void pruneAsync(Runnable onChanged) {
        RP.post(() -> {
            Preferences prefs = prefs();
            List<String> kept = new ArrayList<>();
            boolean dropped = false;
            for (String path : prefs.get(PREF_KEY, "").split("\n")) {
                if (path.isBlank()) {
                    continue;
                }
                if (new File(path).isFile()) {
                    kept.add(path);
                } else {
                    dropped = true;
                }
            }
            if (!dropped) {
                return;
            }
            prefs.put(PREF_KEY, String.join("\n", kept));
            try {
                prefs.flush();
            } catch (java.util.prefs.BackingStoreException ignore) {
                // best effort
            }
            if (onChanged != null) {
                javax.swing.SwingUtilities.invokeLater(onChanged);
            }
        });
    }

    /** Test barrier: drains the trail lane (the awaitDeviceBgIdle idiom). */
    static void awaitIdle() {
        RP.post(() -> { }).waitFinished();
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
