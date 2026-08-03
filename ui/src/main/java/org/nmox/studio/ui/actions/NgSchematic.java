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
}
