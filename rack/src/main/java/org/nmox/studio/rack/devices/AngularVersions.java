package org.nmox.studio.rack.devices;

import java.io.File;

/**
 * Version arithmetic for the HALO Angular console: reads the installed
 * @angular/core constraint from package.json and compares against the
 * registry's latest, so the OUTDATED LED tells the truth.
 */
public final class AngularVersions {

    private AngularVersions() {
    }

    /** The @angular/core constraint from package.json, cleaned: "22.0.1", or null. */
    public static String installed(File projectDir) {
        String raw = ProjectInspector.dependencyVersion(projectDir, "@angular/core");
        return raw == null ? null : clean(raw);
    }

    /** Strips range operators: "^22.0.1" -> "22.0.1". */
    public static String clean(String constraint) {
        return constraint.replaceAll("[~^>=<\\s]", "");
    }

    /**
     * True when latest is a newer release than installed. Compares
     * numeric segments; non-numeric segments compare as zero.
     */
    public static boolean isOutdated(String installed, String latest) {
        if (installed == null || latest == null) {
            return false;
        }
        String[] a = clean(installed).split("\\.");
        String[] b = clean(latest).split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = segment(a, i);
            int y = segment(b, i);
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    /** Majors differ: an `ng update` is a real migration, not a patch. */
    public static boolean isMajorBehind(String installed, String latest) {
        if (installed == null || latest == null) {
            return false;
        }
        return segment(clean(installed).split("\\."), 0) < segment(clean(latest).split("\\."), 0);
    }

    private static int segment(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("\\D.*$", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
