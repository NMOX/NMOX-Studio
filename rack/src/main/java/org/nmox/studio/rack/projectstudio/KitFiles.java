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
