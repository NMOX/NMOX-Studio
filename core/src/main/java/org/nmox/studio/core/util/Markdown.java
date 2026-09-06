package org.nmox.studio.core.util;

/**
 * The fence rule every Copy-as-Markdown gesture shares (one home since
 * v2.87.0; the editor's block builder delegates): CommonMark closes a
 * fenced block on ANY backtick run at least as long as the opener, so a
 * body that itself contains three backticks needs a four-backtick fence
 * to render whole instead of ending early.
 */
public final class Markdown {

    private Markdown() {
    }

    /** The fence string for {@code body}: three backticks, or one more than the body's longest run. */
    public static String fenceFor(String body) {
        return "`".repeat(Math.max(3, longestBacktickRun(body) + 1));
    }

    static int longestBacktickRun(String s) {
        int best = 0;
        int run = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '`') {
                run++;
                best = Math.max(best, run);
            } else {
                run = 0;
            }
        }
        return best;
    }
}
