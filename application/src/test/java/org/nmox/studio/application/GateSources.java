package org.nmox.studio.application;

/**
 * What a source gate in this module reads: the CODE, never the prose around it.
 *
 * <p>The rack module has carried its own {@code GateSources.stripComments}
 * since v2.178.0, when four of five new gates let their mutant live because
 * they matched a literal that a comment could satisfy — *a source gate that
 * matches a literal is one comment away from decorative*. This module was
 * missed by that sweep, and v2.182.0 found the mirror image of the same
 * defect: {@link PlainStatusGateTest} flagged a javadoc sentence that
 * DOCUMENTED the platform contract (`setStatusText(String)`) as though it were
 * a call site. A gate that reads prose is wrong in both directions — it can be
 * satisfied by a comment, and it can be tripped by one.
 *
 * <p>Promoted here on its second consumer rather than copied
 * ({@code BoundedReadLedgerTest} had the first): the defect is the second
 * home, not the disagreement between two copies (v2.131.0).
 */
final class GateSources {

    private GateSources() {
    }

    /**
     * The same text with every comment removed and every line kept, so a match
     * still reports the line number the reader will open.
     *
     * <p>Line-oriented on purpose: a block comment opening and closing inside
     * a line of real code is vanishingly rare in this codebase, and a full
     * tokeniser here would be a second parser to keep honest. A trailing
     * {@code //} on a code line is left alone for the same reason — the
     * callers that care strip those themselves.
     */
    static String stripComments(String src) {
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String lineText : src.split("\n", -1)) {
            String t = lineText.strip();
            if (inBlock) {
                if (t.contains("*/")) {
                    inBlock = false;
                }
                sb.append('\n');
                continue;
            }
            if (t.startsWith("/*")) {
                inBlock = !t.contains("*/");
                sb.append('\n');
                continue;
            }
            sb.append(t.startsWith("//") ? "" : lineText).append('\n');
        }
        return sb.toString();
    }
}
