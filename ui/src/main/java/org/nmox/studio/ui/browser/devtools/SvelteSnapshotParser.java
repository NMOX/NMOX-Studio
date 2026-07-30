package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the {@link DevScripts#SVELTE_SNAPSHOT} JSON into a typed
 * per-file location list for the Svelte tab. Same hostile-input
 * posture as {@link VueSnapshotParser}: never throws, missing fields
 * default, every string and list is re-capped Java-side (the page is
 * untrusted even about its own caps), and "no Svelte on this page" is
 * a first-class answer (empty files, zero total) rather than an error.
 *
 * <p>The shape is deliberately flat — files owning source locations —
 * because that is ALL Svelte offers a runtime inspector: the compiler
 * compiles components away, so there are no instances, props, or
 * state to walk; dev mode's {@code __svelte_meta} source mapping
 * (which file and line rendered each element) is the whole story, and
 * a production build offers nothing at all.
 */
public final class SvelteSnapshotParser {

    /** One rendered source location: line/column plus the element's DOM path. */
    public static final class Loc {

        public final int line;
        public final int column;
        public final List<Integer> path;

        Loc(int line, int column, List<Integer> path) {
            this.line = line;
            this.column = column;
            this.path = Collections.unmodifiableList(path);
        }

        @Override
        public String toString() {
            return "line " + line + ":" + column;
        }
    }

    /** One source file and the locations its component rendered. */
    public static final class SvelteFile {

        public final String file;
        public final int count;
        public final List<Loc> locs;

        SvelteFile(String file, int count, List<Loc> locs) {
            this.file = file;
            this.count = count;
            this.locs = Collections.unmodifiableList(locs);
        }

        /** The file's basename, for the tree row (full path in details). */
        public String basename() {
            int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
            return slash < 0 ? file : file.substring(slash + 1);
        }

        @Override
        public String toString() {
            return basename() + " (" + count + ")";
        }
    }

    /** The whole snapshot: files with locations, honest element total. */
    public static final class SvelteTree {

        public final List<SvelteFile> files;
        public final int total;

        SvelteTree(List<SvelteFile> files, int total) {
            this.files = Collections.unmodifiableList(files);
            this.total = total;
        }

        /** True when no dev-mode Svelte marker was found on the page. */
        public boolean empty() {
            return files.isEmpty();
        }
    }

    private SvelteSnapshotParser() {
    }

    /** Parses the snapshot; malformed input is "no Svelte detected". */
    public static SvelteTree parse(String json) {
        Object v = JsonLite.parse(json);
        if (!(v instanceof java.util.Map)) {
            return new SvelteTree(List.of(), 0);
        }
        java.util.Map<String, Object> o = JsonLite.asObject(v);
        List<SvelteFile> files = new ArrayList<>();
        for (Object f : JsonLite.asArray(o.get("files"))) {
            if (!(f instanceof java.util.Map)) {
                continue;
            }
            if (files.size() >= 500) {
                break; // a hostile page cannot flood the tree with files
            }
            files.add(file(JsonLite.asObject(f)));
        }
        int total = JsonLite.num(o, "total", 0);
        return new SvelteTree(files, Math.max(0, total));
    }

    private static SvelteFile file(java.util.Map<String, Object> o) {
        String name = JsonLite.str(o, "file", "(unknown)");
        if (name.isBlank()) {
            name = "(unknown)";
        }
        if (name.length() > 500) {
            name = name.substring(0, 500);
        }
        List<Loc> locs = new ArrayList<>();
        for (Object l : JsonLite.asArray(o.get("locs"))) {
            if (!(l instanceof java.util.Map)) {
                continue;
            }
            if (locs.size() >= 200) {
                break; // the script's own cap, re-imposed on hostile input
            }
            java.util.Map<String, Object> lo = JsonLite.asObject(l);
            List<Integer> path = new ArrayList<>();
            for (Object p : JsonLite.asArray(lo.get("path"))) {
                if (p instanceof Double d) {
                    path.add((int) (double) d);
                }
            }
            locs.add(new Loc(Math.max(0, JsonLite.num(lo, "line", 0)),
                    Math.max(0, JsonLite.num(lo, "column", 0)), path));
        }
        int count = JsonLite.num(o, "count", locs.size());
        return new SvelteFile(name, Math.max(count, locs.size()), locs);
    }
}
