package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The one kit write law (the v1.141.0 debt sprint's HttpBodies-style
 * unification): every kit generator writes through here. Never clobber —
 * a missing file is written, an identical file is left untouched, and a
 * differing file keeps the user's version while the proposal lands as a
 * {@code .suggested} sibling (itself never overwritten). Parent
 * directories are created as needed.
 */
final class KitFiles {

    /** What happened, in the kits' shared vocabulary. */
    record Write(String path, String status, boolean changed) {
    }

    private KitFiles() {
    }

    /**
     * A W3C manifest {@code short_name}, truncated for home-screen
     * display. Home screens show roughly 12 characters, so a longer
     * name is cut — but on a CODE-POINT boundary (never mid-surrogate,
     * which would strand a lone UTF-16 unit and mint an invalid JSON
     * string — the v1.149.0/v1.287.0 truncation class), and with
     * trailing whitespace stripped so the cut never leaves a dangling
     * space in the installed app's name.
     */
    static String shortName(String name) {
        String s = name == null ? "" : name.strip();
        if (s.codePointCount(0, s.length()) <= 12) {
            return s;
        }
        int end = s.offsetByCodePoints(0, 12);
        return s.substring(0, end).stripTrailing();
    }

    static Write writeNeverClobber(File dir, String path, String content)
            throws IOException {
        File target = new File(dir, path);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        if (!target.exists()) {
            Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
            return new Write(path, "written", true);
        }
        if (content.equals(Files.readString(target.toPath(), StandardCharsets.UTF_8))) {
            return new Write(path, "already exists, untouched", false);
        }
        File suggested = new File(dir, path + ".suggested");
        if (suggested.exists()) {
            return new Write(path,
                    "skipped — " + path + " and " + path + ".suggested both exist", false);
        }
        Files.writeString(suggested.toPath(), content, StandardCharsets.UTF_8);
        return new Write(path + ".suggested",
                "existing " + path + " kept — suggestion written alongside", true);
    }
}
