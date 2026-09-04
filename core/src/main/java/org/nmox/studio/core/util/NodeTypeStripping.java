package org.nmox.studio.core.util;

import java.util.List;

/**
 * TypeScript that runs without a build (futures bet F7): Node strips
 * types itself — flagged since 22.6, the default since 23.6 and in the
 * 22.18 LTS line. The product's run lane passes the flag whenever the
 * entry is a {@code .ts} file: every Node from 22.6 up accepts it and
 * where stripping is already the default it is a harmless no-op, so ONE
 * argv covers every version and no probe is spent (a version probe in
 * the command builder would run on the EDT — tooltips and CI export
 * build the same command). An older Node refuses the flag with
 * {@code bad option}; {@link #wall} turns that line into the human
 * sentence the pump prints (the v1.318.0 Node-floor idiom).
 */
public final class NodeTypeStripping {

    /** The flag Node 22.6–23.5 needs; a no-op where stripping is the default. */
    public static final String FLAG = "--experimental-strip-types";
    /** The lowest Node that strips types at all. */
    public static final String MIN_NODE = "22.6";

    private NodeTypeStripping() {
    }

    /** Whether an entry file is TypeScript the run lane must strip (.ts / .mts / .cts; never .d.ts). */
    public static boolean isTypeScript(String entry) {
        if (entry == null) {
            return false;
        }
        String lower = entry.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".d.ts")) {
            return false;
        }
        return lower.endsWith(".ts") || lower.endsWith(".mts") || lower.endsWith(".cts");
    }

    /** {@code node [--experimental-strip-types] entry} — the flag only for TypeScript entries. */
    public static List<String> argv(String entry) {
        return isTypeScript(entry) ? List.of("node", FLAG, entry) : List.of("node", entry);
    }

    /** The human wall for Node's refusal of the flag, or null when the line is not that refusal. */
    public static String wall(String line) {
        if (line == null || !line.contains("bad option") || !line.contains(FLAG)) {
            return null;
        }
        return "↳ This Node cannot run TypeScript directly — type stripping needs Node "
                + MIN_NODE + " or newer (default since 23.6 and 22.18 LTS). Upgrade Node, or add a "
                + "build step and run the compiled JavaScript.";
    }
}
