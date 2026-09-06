package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * What "Explain with KVASIR" sends when checks fail (v2.39.5, the
 * checkpoint loop's tutor half): the failed checkpoints — label,
 * hint, detail — and, for a FILE-kind checkpoint, the learner's own
 * checked file, capped, because "why does this check fail" is only
 * answerable next to what they actually wrote. Command-kind failures
 * send the exit detail alone: a test run's output can be huge and the
 * detail already carries the verdict. The disclosure discipline of
 * every KVASIR flow (v1.171.0): assembled and capped where the data
 * lives, so the consent line is the literal truth. Pure so every cap
 * is a unit test.
 */
public final class CheckDisclosure {

    static final int FILE_CAP = 4000;

    private CheckDisclosure() {
    }

    /** The consent dialog's one-line summary — the literal truth. */
    public static String what(String spaceName, List<Checkpoints.Checkpoint> failed) {
        long files = failed.stream().filter(Checkpoints.Checkpoint::isFileKind).count();
        String base = "The " + failed.size() + " failed "
                + (failed.size() == 1 ? "check" : "checks") + " of " + spaceName
                + " (labels and hints)";
        return files == 0 ? base + " — no file contents."
                : base + " and your " + (files == 1 ? "checked file" : files + " checked files")
                + " (capped).";
    }

    /** The conversation's opening body. */
    public static String body(File spaceDir, List<Checkpoints.Checkpoint> failed,
            List<Checkpoints.Result> results) {
        StringBuilder b = new StringBuilder("These checks fail in my exercise:\n");
        for (int i = 0; i < failed.size(); i++) {
            Checkpoints.Checkpoint c = failed.get(i);
            b.append("\n✗ ").append(c.label()).append('\n');
            if (!c.hint().isBlank()) {
                b.append("  hint: ").append(c.hint()).append('\n');
            }
            if (i < results.size() && !results.get(i).detail().isBlank()) {
                b.append("  detail: ").append(results.get(i).detail()).append('\n');
            }
            if (c.isFileKind()) {
                b.append("  my ").append(c.filePath()).append(":\n");
                try {
                    File target = LearningSpace.resolveInside(spaceDir, c.filePath());
                    String text = target == null || !target.isFile() ? "(missing)"
                            : Files.readString(target.toPath(), StandardCharsets.UTF_8);
                    b.append(cap(text)).append('\n');
                } catch (IOException unreadable) {
                    b.append("  (unreadable: ").append(unreadable.getMessage()).append(")\n");
                }
            }
        }
        return b.toString();
    }

    private static String cap(String s) {
        if (s.codePointCount(0, s.length()) <= FILE_CAP) {
            return s;
        }
        // code-point-safe (the v1.149.0 cap law)
        return s.substring(0, s.offsetByCodePoints(0, FILE_CAP)) + "\n…[truncated]";
    }
}
