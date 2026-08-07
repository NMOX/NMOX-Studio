package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * User-authored rack presets: any {@code .json} file in
 * {@code ~/.nmox/presets.d/} appears in the rack's Presets menu under
 * the built-ins — and the file format is one the user already knows
 * how to produce, because it IS the Save Patch format
 * ({@code .nmoxrack.json}, {@link org.nmox.studio.rack.model.RackIO}).
 *
 * <p>This closes a loop the product left open (v1.294.0, the
 * extensibility arc, beside the v1.293.0 template drop-ins): Save
 * Patch could capture a wiring you liked, but only inside the one
 * project that owned it. Copy the file here and the same wiring is a
 * preset everywhere. No schema to learn, no IDE build.
 *
 * <p>The entry is deliberately just a name and a file: the JSON is
 * parsed at APPLY time, off the EDT, exactly as Load Patch parses the
 * project's own file — a corrupt preset fails with a message then,
 * and an unknown device type inside one degrades to the v1.54.0
 * MISSING placeholder instead of dropping the patch.
 */
public final class UserPresets {

    /** One drop-in preset: menu label (filename sans .json) and its file. */
    public record Custom(String name, File file) {
    }

    private UserPresets() {
    }

    /** Where user presets live: {@code ~/.nmox/presets.d}. */
    public static File dropInDir() {
        return new File(System.getProperty("user.home"), ".nmox/presets.d");
    }

    /** Presets from the default drop-in dir, filename order. */
    public static List<Custom> list() {
        return listFrom(dropInDir());
    }

    static List<Custom> listFrom(File dir) {
        List<Custom> out = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) {
            return out;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            String name = f.getName().substring(0, f.getName().length() - ".json".length());
            if (!name.isBlank()) {
                out.add(new Custom(name, f));
            }
        }
        return out;
    }
}
