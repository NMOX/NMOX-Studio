package org.nmox.studio.apiclient.api;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The request library: {@code .http}/{@code .rest} files in
 * {@code ~/.nmox/api-library.d/} appear in API Studio's Import… menu,
 * one click from joining any project's workspace (v1.297.0, the
 * extensibility arc's third capture-once loop, after templates.d and
 * presets.d).
 *
 * <p>Why {@code .http} and not {@code .nmoxapi.json}: the workspace
 * file is keychain-COUPLED — its requests carry stable ids whose auth
 * tokens live in the OS keychain (v1.97.0), so copying it between
 * machines or teammates ships dangling references at best. The .http
 * dialect is the product's SHARING format on purpose: export
 * deliberately omits auth (v1.179.0), import lifts a pasted
 * Authorization header into the keychain (v1.166.0), and the round
 * trip is pinned by test. Export a collection to .http, drop the file
 * here, and it is importable everywhere — with the secrets law intact
 * at both ends.
 *
 * <p>Like every drop-in surface: entries are name + file only; the
 * parse happens at import time through the ONE {@code importHttpFrom}
 * implementation the chooser and the editor gesture already share, so
 * refusals, the off-EDT read, and the Authorization lift reach the
 * library by construction.
 */
public final class HttpLibrary {

    /** One library entry: menu label (filename sans extension) and its file. */
    public record Entry(String name, File file) {
    }

    private HttpLibrary() {
    }

    /** Where the library lives: {@code ~/.nmox/api-library.d}. */
    public static File dropInDir() {
        return new File(System.getProperty("user.home"), ".nmox/api-library.d");
    }

    /** Library entries from the default dir, filename order. */
    public static List<Entry> list() {
        return listFrom(dropInDir());
    }

    static List<Entry> listFrom(File dir) {
        List<Entry> out = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".http") || n.endsWith(".rest"));
        if (files == null) {
            return out;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            String n = f.getName();
            String name = n.substring(0, n.lastIndexOf('.'));
            if (!name.isBlank()) {
                out.add(new Entry(name, f));
            }
        }
        return out;
    }
}
