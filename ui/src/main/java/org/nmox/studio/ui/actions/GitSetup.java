package org.nmox.studio.ui.actions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What it takes to make NMOX Studio git's editor, difftool and mergetool
 * (3.2.0): the global settings {@code nmox -w} and {@code nmox -d} need, and the
 * {@code git config} commands that set them. Pure, so the exact strings are
 * a unit test; {@link GitSetupAction} reads the current values and applies.
 *
 * <p>The command is the name {@code nmox}, never an absolute path: git
 * runs its editor through the shell, the {@code nmox} on the PATH is the one
 * the installers put there (and follows the app when it moves or updates),
 * and a path into an app bundle would pin git to one install. When
 * {@code nmox} is not on the PATH the action says so and offers nothing to
 * apply.
 */
public final class GitSetup {

    private GitSetup() {
    }

    /** The difftool's name in git's config: {@code diff.tool nmox}, {@code difftool.nmox.cmd}. */
    public static final String TOOL = "nmox";

    /**
     * The settings, in the order they are shown and applied: the editor,
     * the difftool's name and its command, then the mergetool's name, its
     * command and how git judges it. {@code $LOCAL}, {@code $REMOTE} and
     * {@code $MERGED} stay literal: git substitutes them when it runs the
     * tool.
     *
     * <p>The mergetool opens the conflicted file itself ({@code $MERGED})
     * and waits for its tab to close; the editor's conflict hints do the
     * resolving (3.2.0, {@code editor.conflicts}). {@code trustExitCode
     * false} because {@code nmox -w} exits 0 whether or not the file was
     * resolved: git then asks "Was the merge successful?" when the file was
     * not saved, instead of marking an untouched conflict resolved.
     */
    public static Map<String, String> settings() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("core.editor", "nmox -w");
        s.put("diff.tool", TOOL);
        s.put("difftool." + TOOL + ".cmd", "nmox -w -d \"$LOCAL\" \"$REMOTE\"");
        s.put("merge.tool", TOOL);
        s.put("mergetool." + TOOL + ".cmd", "nmox -w \"$MERGED\"");
        s.put("mergetool." + TOOL + ".trustExitCode", "false");
        return s;
    }

    /** The argv that sets one setting globally: fixed words, the value one argument. */
    public static List<String> setCommand(String key, String value) {
        return List.of("git", "config", "--global", key, value);
    }

    /** The argv that reads one setting's current global value (exit 1 when unset). */
    public static List<String> getCommand(String key) {
        return List.of("git", "config", "--global", "--get", key);
    }

    /**
     * The settings as the lines a person would type, for the clipboard and
     * the documentation: single-quoted for a POSIX shell, so {@code $LOCAL}
     * is not expanded by the shell that types it.
     */
    public static List<String> shellLines() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : settings().entrySet()) {
            out.add("git config --global " + e.getKey() + " '" + e.getValue().replace("'", "'\\''") + "'");
        }
        return out;
    }
}
