package org.nmox.studio.rack;

/**
 * What a source-reading gate should read: the CODE. A gate that matches a
 * literal must not be satisfied by a comment naming it (the v2.160.0
 * wiring-gate scar); on the 2026-09-17 arc review two such gates stayed
 * green on mutants that moved the literal into a comment
 * ({@code RackShareDoorsTest}'s Cancel default, {@code StarterRacksTest}'s
 * shared-wiring name). Whole-line {@code //} comments and block comments are
 * blanked line for line, so offsets stay comparable; a trailing comment
 * after code is left, because the code on that line is what the literal
 * must then match.
 */
public final class GateSources {

    private GateSources() {
    }

    public static String stripComments(String src) {
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String line : src.split("\n", -1)) {
            String t = line.strip();
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
            sb.append(t.startsWith("//") ? "" : line).append('\n');
        }
        return sb.toString();
    }
}
