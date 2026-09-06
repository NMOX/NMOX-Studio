package org.nmox.studio.ui.whatsnew;

import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.core.util.Versions;

/**
 * The release notes, the pure half: a Keep-a-Changelog parse of the
 * CHANGELOG the ui module bundles at build time, the entries a user has
 * not seen yet, and the first-boot decision — so the "What's new" the
 * product shows after an update is the same text the repo ships, never a
 * second copy that rots.
 *
 * <p>The decision is deliberately three-valued: a dev build (unstamped
 * version) shows nothing; a FRESH install records the running version
 * silently — a new user wants Getting Started, not a diff; only an
 * install that has seen an earlier version gets the notes, and only the
 * entries between what it saw and what runs now, capped with an honest
 * marker.
 */
public final class ReleaseNotes {

    /** Entries shown at most on a first boot after an update. */
    public static final int MAX_ENTRIES = 10;

    /** One changelog entry: the version, its date, and the body lines as written. */
    public record Entry(String version, String date, String body) {
    }

    /** What the first boot does. */
    public enum Decision { NONE, RECORD_ONLY, SHOW }

    private ReleaseNotes() {
    }

    /** Parses "## [x.y.z] - date" sections; anything before the first heading is ignored. */
    public static List<Entry> parse(String changelog) {
        List<Entry> out = new ArrayList<>();
        if (changelog == null) {
            return out;
        }
        String version = null;
        String date = "";
        StringBuilder body = new StringBuilder();
        for (String line : changelog.split("\n", -1)) {
            if (line.startsWith("## [")) {
                if (version != null) {
                    out.add(new Entry(version, date, body.toString().strip()));
                }
                int close = line.indexOf(']');
                version = close > 4 ? line.substring(4, close) : line.substring(4).strip();
                int dash = line.indexOf(" - ", Math.max(close, 0));
                date = dash > 0 ? line.substring(dash + 3).strip() : "";
                body.setLength(0);
            } else if (version != null) {
                body.append(line).append('\n');
            }
        }
        if (version != null) {
            out.add(new Entry(version, date, body.toString().strip()));
        }
        return out;
    }

    /** The newest entry, or null for an empty changelog. */
    public static Entry head(List<Entry> entries) {
        return entries.isEmpty() ? null : entries.get(0);
    }

    /** The entry for one exact version, or null. */
    public static Entry entryFor(List<Entry> entries, String version) {
        for (Entry e : entries) {
            if (e.version().equals(version)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Entries newer than {@code lastSeen} and not newer than {@code running},
     * newest first, at most {@link #MAX_ENTRIES}; {@link #omitted} says how
     * many the cap hid. A null {@code lastSeen} means "everything up to running".
     */
    public static List<Entry> since(List<Entry> entries, String lastSeen, String running) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (Versions.compare(e.version(), running) > 0) {
                continue;
            }
            if (lastSeen != null && Versions.compare(e.version(), lastSeen) <= 0) {
                break; // entries are newest-first; everything below is seen
            }
            if (out.size() < MAX_ENTRIES) {
                out.add(e);
            }
        }
        return out;
    }

    /** How many unseen entries {@link #since} could not show. */
    public static int omitted(List<Entry> entries, String lastSeen, String running) {
        int n = 0;
        for (Entry e : entries) {
            if (Versions.compare(e.version(), running) > 0) {
                continue;
            }
            if (lastSeen != null && Versions.compare(e.version(), lastSeen) <= 0) {
                break;
            }
            n++;
        }
        return Math.max(0, n - MAX_ENTRIES);
    }

    /** The first-boot rule, stated once. */
    public static Decision decide(String running, String lastSeen, boolean stamped) {
        if (!stamped || running == null || running.isBlank()) {
            return Decision.NONE;
        }
        if (lastSeen == null || lastSeen.isBlank()) {
            return Decision.RECORD_ONLY;
        }
        return running.equals(lastSeen) ? Decision.NONE : Decision.SHOW;
    }

    /**
     * The same entries as Markdown for a release post (v2.88.0, the
     * developer evangelist's "what shipped" motion): each entry under its
     * Keep-a-Changelog heading, {@code ## [2.87.0] - 2026-09-06}, the body
     * exactly as written in CHANGELOG.md — it IS Markdown there — with
     * the omitted-count line, when any, as a trailing note.
     */
    public static String renderMarkdown(List<Entry> entries, int omitted) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("## [").append(e.version()).append(']');
            if (!e.date().isBlank()) {
                sb.append(" - ").append(e.date());
            }
            sb.append("\n\n").append(e.body().strip()).append('\n');
        }
        if (omitted > 0) {
            sb.append("\n_…and ").append(omitted).append(" earlier release").append(omitted == 1 ? "" : "s")
                    .append(" not shown._\n");
        }
        return sb.toString();
    }

    /** Plain text for a text area: version + date headings, bodies as written. */
    public static String render(List<Entry> entries, int omitted) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(e.version());
            if (!e.date().isBlank()) {
                sb.append(" — ").append(e.date());
            }
            sb.append('\n').append(e.body());
        }
        if (omitted > 0) {
            sb.append("\n\n… and ").append(omitted).append(" earlier release")
                    .append(omitted == 1 ? "" : "s").append(" not shown — the full notes are on GitHub.");
        }
        return sb.toString();
    }
}
