package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * User-authored Environment Doctor probes: any {@code .json} file in
 * {@code ~/.nmox/doctor.d/} adds one row to the Doctor's table, probed
 * through the SAME hardened launcher, 4-second leash, and honest
 * status wording ({@code detailFor}, v1.303.0) as the built-ins.
 *
 * <p>This is the sixth drop-in surface (v1.305.0) and the last named
 * candidate from the extensibility arc's list: a team whose stack
 * leans on a tool the product doesn't know (terraform, kubectl, an
 * in-house CLI) currently gets SILENCE from the one surface whose job
 * is "what does this machine actually have?" — a drop-in probe makes
 * the Doctor speak for it.
 *
 * <p>The safety law is stricter than the sibling surfaces', because a
 * probe is a COMMAND the Doctor executes on open, with no GO button
 * and no trust prompt in between: a drop-in may only NAME a tool —
 * a bare binary name resolved on PATH exactly like every built-in
 * probe, never a path — and its version arguments must be flag-shaped
 * (no paths, no spaces, no shell metacharacters). Anything else
 * disqualifies the whole file, skipped with a note the table shows.
 * A tool the product already probes is also skipped: one authoritative
 * row per tool, and the built-ins carry their own version dialects.
 */
public final class UserProbes {

    /** One drop-in probe: bare tool name, purpose, version args, install hint. */
    public record Custom(String tool, String purpose, List<String> args,
            String install) {
    }

    /** The scan result: valid probes plus per-file skip notes for the table. */
    public record Loaded(List<Custom> probes, List<String> skipped) {
    }

    /** A bare binary name — never a path, never flag-shaped. */
    private static final Pattern TOOL_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*");

    /** A version flag or plain verb: {@code --version}, {@code -v}, {@code version}. */
    private static final Pattern FLAG_SHAPED =
            Pattern.compile("-{0,2}[A-Za-z0-9][A-Za-z0-9.=_-]*");

    private UserProbes() {
    }

    /** Where user probes live: {@code ~/.nmox/doctor.d}. */
    public static File dropInDir() {
        return new File(System.getProperty("user.home"), ".nmox/doctor.d");
    }

    /**
     * Probes from the default drop-in dir, filename order. {@code taken}
     * is the set of binaries the product already probes — a duplicate is
     * skipped with a note rather than producing two rows that can
     * disagree about one tool.
     */
    public static Loaded load(Set<String> taken) {
        return loadFrom(dropInDir(), taken);
    }

    static Loaded loadFrom(File dir, Set<String> taken) {
        List<Custom> probes = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) {
            return new Loaded(probes, skipped);
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            try {
                String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                JSONObject o = new JSONObject(json);
                String problem = problem(o, taken);
                if (problem != null) {
                    skipped.add(f.getName() + " — " + problem);
                    continue;
                }
                probes.add(new Custom(o.getString("tool"),
                        o.getString("purpose").strip(),
                        argsOf(o),
                        o.optString("install", "").strip()));
            } catch (Exception malformed) {
                // org.json throws JSONException on bad syntax/missing keys;
                // an unreadable file lands here too — skip, never block
                skipped.add(f.getName() + " — not a valid probe file ("
                        + malformed.getMessage() + ")");
            }
        }
        return new Loaded(probes, skipped);
    }

    /**
     * Why this probe object must be refused, or null if it is valid.
     * One bad field disqualifies the whole file — a probe is executed,
     * so there is no safe "partial" acceptance.
     */
    static String problem(JSONObject o, Set<String> taken) {
        String tool = o.optString("tool", "");
        if (!TOOL_NAME.matcher(tool).matches()) {
            // covers empty, paths (/usr/bin/x, ..\x), and flag-shaped
            // names — the probe may point at a PATH-resolved tool only,
            // never at a file a drop-in chose
            return "\"tool\" must be a bare binary name, not a path: \""
                    + tool + "\"";
        }
        if (taken.contains(tool)) {
            return "\"" + tool + "\" is already probed by the product";
        }
        if (o.optString("purpose", "").isBlank()) {
            return "\"purpose\" is required — the table's Used-for column";
        }
        JSONArray args = o.optJSONArray("args");
        if (args != null) {
            for (int i = 0; i < args.length(); i++) {
                String arg = args.optString(i, "");
                if (!FLAG_SHAPED.matcher(arg).matches()) {
                    return "\"args\" entries must be flag-shaped"
                            + " (--version, -v, version): \"" + arg + "\"";
                }
            }
        }
        return null;
    }

    /** The declared version args, or the {@code --version} default. */
    private static List<String> argsOf(JSONObject o) {
        JSONArray args = o.optJSONArray("args");
        if (args == null || args.isEmpty()) {
            return List.of("--version");
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < args.length(); i++) {
            out.add(args.getString(i));
        }
        return List.copyOf(out);
    }
}
