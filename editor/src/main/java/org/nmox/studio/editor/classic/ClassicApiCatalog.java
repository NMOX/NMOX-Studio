package org.nmox.studio.editor.classic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The classic-web API surface, loaded once from {@code classic-apis.json}:
 * per library id (jquery, mootools, prototype, backbone, underscore,
 * knockout), the canonical documented entries of its last major release.
 * Entry names follow two conventions the matcher understands — statics
 * carry their namespace ({@code $.ajax}, {@code _.each},
 * {@code ko.observable}), instance methods a leading dot
 * ({@code .addClass}).
 *
 * <p>Pure data: no editor, no project, no network. The completion
 * provider intersects {@link ClassicLibraryDetector}'s detected ids with
 * this catalog.
 */
public final class ClassicApiCatalog {

    private static final Logger LOG = Logger.getLogger(ClassicApiCatalog.class.getName());

    /** One completable API: display name and human signature. */
    public record Entry(String name, String sig) {
    }

    /** One library's surface. */
    public record Library(String id, String display, List<Entry> entries) {
    }

    private static final Map<String, Library> LIBRARIES = load();

    private ClassicApiCatalog() {
    }

    /** All libraries by id, in catalog order. Never null; empty only if the bundled resource is broken. */
    public static Map<String, Library> libraries() {
        return LIBRARIES;
    }

    /** The library for an id, or null when the catalog has no such library. */
    public static Library library(String id) {
        return LIBRARIES.get(id);
    }

    private static Map<String, Library> load() {
        try (InputStream in = ClassicApiCatalog.class.getResourceAsStream("classic-apis.json")) {
            if (in == null) {
                LOG.warning("classic-apis.json missing from module resources");
                return Map.of();
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(text);
            Map<String, Library> out = new LinkedHashMap<>();
            for (String id : root.keySet()) {
                JSONObject lib = root.getJSONObject(id);
                JSONArray raw = lib.getJSONArray("entries");
                List<Entry> entries = new ArrayList<>(raw.length());
                for (int i = 0; i < raw.length(); i++) {
                    JSONObject e = raw.getJSONObject(i);
                    entries.add(new Entry(e.getString("name"), e.getString("sig")));
                }
                out.put(id, new Library(id, lib.getString("display"), List.copyOf(entries)));
            }
            return Map.copyOf(out);
        } catch (IOException | org.json.JSONException ex) {
            LOG.log(Level.WARNING, "classic-apis.json unreadable; classic completion disabled", ex);
            return Map.of();
        }
    }
}
