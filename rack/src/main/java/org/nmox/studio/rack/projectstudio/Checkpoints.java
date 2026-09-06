package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Learning-space checkpoints (v2.39.1, the elevation arc's second
 * unit): a tutorial that says "do X, see Y" becomes one that CHECKS —
 * each space may declare verifiable claims about the learner's work,
 * and the Check My Work gesture runs them and answers ✓ or ✗ with the
 * space's own hint. Two kinds, chosen by what a claim needs:
 *
 * <ul>
 *   <li><b>file</b> — pure-Java, toolchain-free, the beginner-space
 *       kind: the file at a relative path must {@code contains} a
 *       substring (at least {@code atLeast} times, when given) and/or
 *       be {@code absent} of one. The absent check is how "you changed
 *       the heading" is verifiable: the SAMPLE's original text must be
 *       gone; the count is how "you added a third item" is — the seed
 *       already contains the substring twice. A task checkpoint must
 *       FAIL on the untouched seed (CheckpointParityTest): the v2.39.1
 *       seeds shipped two that could not, and Check My Work said
 *       "nicely done" to a learner who had done nothing.</li>
 *   <li><b>command</b> — the toolchain-space kind: an argv (never a
 *       shell) runs in the space and must exit 0, optionally with
 *       {@code expect} appearing in its output. A rust space's
 *       {@code cargo test} is the honest verifier of a rust
 *       exercise.</li>
 * </ul>
 *
 * <p>Checkpoints arrive from the learn catalog, whose drop-in
 * siblings are data from anywhere (v1.293+), and command checkpoints
 * EXECUTE — so the device-file law applies (v2.0.0): argv only, the
 * tool name bare (no path separators), and a checkpoint that breaks
 * any rule is skipped WHOLE with its reason, never partially
 * honored. File paths ride the same resolveInside containment as
 * sample files. Pure so every rule is a unit test; the process spawn
 * hides behind {@link Runner} so runs are testable without spawning.
 */
public final class Checkpoints {

    private Checkpoints() {
    }

    /** One verifiable claim from the catalog. */
    public record Checkpoint(String label, String hint, String filePath,
            String contains, String absent, int atLeast, List<String> command, String expect) {

        /** The v2.39.1 shape — no count. */
        public Checkpoint(String label, String hint, String filePath,
                String contains, String absent, List<String> command, String expect) {
            this(label, hint, filePath, contains, absent, 0, command, expect);
        }

        public boolean isFileKind() {
            return filePath != null;
        }
    }

    /** ✓/✗ plus the reason the learner reads. */
    public record Result(String label, boolean passed, String detail) {
    }

    /** Runs an argv in a dir; returns {exitCode, cappedOutput}. */
    public interface Runner extends BiFunction<File, List<String>, Runner.Run> {

        record Run(int exitCode, String output) {
        }
    }

    /**
     * Parses a space's checkpoint array. Malformed entries are
     * skipped whole with a note appended to {@code notes} (the
     * drop-in family's skip-with-note law) — a partially-honored
     * checkpoint would lie to the learner.
     */
    public static List<Checkpoint> parse(JSONArray arr, List<String> notes) {
        List<Checkpoint> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            String label = o == null ? "" : o.optString("label", "").strip();
            if (label.isEmpty()) {
                notes.add("checkpoint " + (i + 1) + ": no label — skipped");
                continue;
            }
            String hint = o.optString("hint", "");
            JSONObject file = o.optJSONObject("file");
            JSONArray cmd = o.optJSONArray("command");
            if (file != null && cmd != null) {
                notes.add(label + ": both file and command — skipped");
                continue;
            }
            if (file != null) {
                String path = file.optString("path", "").strip();
                String contains = file.has("contains") ? file.getString("contains") : null;
                String absent = file.has("absent") ? file.getString("absent") : null;
                if (path.isEmpty() || (contains == null && absent == null)) {
                    notes.add(label + ": file check needs path and contains/absent — skipped");
                    continue;
                }
                // a non-number here is a broken rule, not a zero: optInt would
                // read "many" as 0 and quietly drop the count (v2.85.0 review)
                if (file.has("atLeast") && !(file.get("atLeast") instanceof Number)) {
                    notes.add(label + ": atLeast must be a number — skipped");
                    continue;
                }
                int atLeast = file.optInt("atLeast", 0);
                if (atLeast < 0 || (atLeast > 0 && contains == null)) {
                    notes.add(label + ": atLeast needs a contains and a count of 1 or more — skipped");
                    continue;
                }
                out.add(new Checkpoint(label, hint, path, contains, absent, atLeast, null, null));
            } else if (cmd != null && cmd.length() > 0) {
                List<String> argv = new ArrayList<>();
                boolean bad = false;
                for (int j = 0; j < cmd.length(); j++) {
                    Object v = cmd.opt(j);
                    if (!(v instanceof String sv) || sv.isBlank()) {
                        bad = true;
                        break;
                    }
                    argv.add(sv);
                }
                // the device-file law: the tool is a bare name, never a
                // path — a drop-in catalog must not point Check at an
                // arbitrary binary on disk
                if (bad || argv.get(0).contains("/") || argv.get(0).contains("\\")) {
                    notes.add(label + ": command must be an argv with a bare tool name — skipped");
                    continue;
                }
                out.add(new Checkpoint(label, hint, null, null, null,
                        List.copyOf(argv), o.has("expect") ? o.getString("expect") : null));
            } else {
                notes.add(label + ": neither file nor command — skipped");
            }
        }
        return out;
    }

    /** Runs one checkpoint in the space. Never throws — a broken
     *  check is a failed check with the reason as its detail. */
    public static Result run(File spaceDir, Checkpoint c, Runner runner) {
        try {
            if (c.isFileKind()) {
                File target = LearningSpace.resolveInside(spaceDir, c.filePath());
                if (target == null || !target.isFile()) {
                    return new Result(c.label(), false,
                            c.filePath() + " not found. " + c.hint());
                }
                String text = Files.readString(target.toPath(), StandardCharsets.UTF_8);
                if (c.contains() != null && !text.contains(c.contains())) {
                    return new Result(c.label(), false, c.hint());
                }
                if (c.atLeast() > 1 && occurrences(text, c.contains()) < c.atLeast()) {
                    return new Result(c.label(), false, c.hint());
                }
                if (c.absent() != null && text.contains(c.absent())) {
                    return new Result(c.label(), false, c.hint());
                }
                return new Result(c.label(), true, "");
            }
            Runner.Run r = runner.apply(spaceDir, c.command());
            if (r.exitCode() != 0) {
                return new Result(c.label(), false,
                        "exit " + r.exitCode() + ". " + c.hint());
            }
            if (c.expect() != null && !r.output().contains(c.expect())) {
                return new Result(c.label(), false,
                        "ran, but \"" + c.expect() + "\" did not appear. " + c.hint());
            }
            return new Result(c.label(), true, "");
        } catch (IOException | RuntimeException broken) {
            return new Result(c.label(), false,
                    broken.getMessage() + ". " + c.hint());
        }
    }

    /** Non-overlapping occurrences of {@code needle} in {@code text}. */
    static int occurrences(String text, String needle) {
        int n = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            n++;
        }
        return n;
    }
}
