package org.nmox.studio.ui.actions;

import java.io.File;
import java.util.List;

/**
 * The pure half of File ▸ New Angular Schematic… (v1.239.0, the
 * Angular bet): root detection, input validation, and the exact argv
 * — all unit-testable without a dialog or a spawn. The CLI does the
 * real work; this class only refuses dishonest inputs before a
 * process ever starts.
 */
final class NgSchematic {

    /** HALO's schematic list, verbatim — one vocabulary, two surfaces. */
    static final String[] SCHEMATICS = {
        "component", "service", "directive", "pipe", "guard",
        "interceptor", "resolver", "class"};

    private NgSchematic() {
    }

    /** The Angular workspace root, or null: angular.json at the aim. */
    static File angularRoot(File aim) {
        if (aim == null || !new File(aim, "angular.json").isFile()) {
            return null;
        }
        return aim;
    }

    /**
     * A name ng will accept without surprises: no blanks, no path
     * separators (the FOLDER field owns placement), no leading dash
     * (would parse as a flag).
     */
    static boolean validName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.trim();
        return !n.contains("/") && !n.contains("\\")
                && !n.contains(" ") && !n.startsWith("-");
    }

    /**
     * Resolves the target folder against the root and refuses anything
     * that escapes it — "../../../etc" typed into the folder field must
     * die here, not in a spawn.
     */
    static File targetFolder(File root, String relative) {
        if (relative == null || relative.isBlank()) {
            return root;
        }
        File resolved = new File(root, relative.trim());
        try {
            String canon = resolved.getCanonicalPath();
            if (!canon.equals(root.getCanonicalPath())
                    && !canon.startsWith(root.getCanonicalPath() + File.separator)) {
                return null; // escaped the workspace
            }
        } catch (java.io.IOException ex) {
            return null;
        }
        return resolved.isDirectory() ? resolved : null;
    }

    /**
     * The exact command SEND runs — ng resolves paths against its cwd,
     * so running in the target folder generates in place, exactly like
     * an Angular dev's terminal habit.
     */
    static List<String> argv(String schematic, String name) {
        return List.of("npx", "ng", "generate", schematic, name.trim());
    }

    /**
     * The file a dev edits FIRST after {@code ng generate}, parsed from
     * the CLI's own {@code CREATE path (N bytes)} lines (Angular batch,
     * 2026-08-11): the primary source file — the first created
     * {@code .ts} that is not a spec — else the first created file at
     * all, else null when the run created nothing (an {@code UPDATE}-
     * only run, or a failure). Paths come back exactly as ng printed
     * them, relative to the folder it ran in; the caller resolves and
     * opens. Generation without the open leaves the dev hunting the
     * tree for the file they just asked for — the terminal habit this
     * gesture replaces put the path right there in the output.
     */
    static String primaryCreated(List<String> outputLines) {
        String firstAny = null;
        for (String line : outputLines) {
            String t = line == null ? "" : line.trim();
            if (!t.startsWith("CREATE ")) {
                continue;
            }
            String path = t.substring("CREATE ".length()).trim();
            int paren = path.lastIndexOf(" (");
            if (paren > 0) {
                path = path.substring(0, paren).trim();
            }
            if (path.isEmpty()) {
                continue;
            }
            if (firstAny == null) {
                firstAny = path;
            }
            if (path.endsWith(".ts") && !path.endsWith(".spec.ts")) {
                return path;
            }
        }
        return firstAny;
    }
}
