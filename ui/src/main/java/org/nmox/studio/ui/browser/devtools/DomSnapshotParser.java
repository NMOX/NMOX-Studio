package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@link DevScripts#DOM_SNAPSHOT} JSON into a typed tree for
 * the DOM tab's JTree. The snapshot is page-influenced (attribute
 * values, class names — all untrusted), so this parser never throws:
 * malformed JSON becomes a single note node, missing fields default,
 * and nesting is bounded by {@link JsonLite}'s depth cap plus this
 * class's own iterative walk (an explicit work list, not recursion —
 * a 10k-deep hostile document cannot StackOverflow the EDT).
 */
public final class DomSnapshotParser {

    /** One DOM tree node. A placeholder ("…N more") has an empty path. */
    public static final class DomNode {

        public final String tag;
        public final String id;
        public final String classes;
        public final List<String> attrs;
        public final List<Integer> path;
        public final List<DomNode> children = new ArrayList<>();

        DomNode(String tag, String id, String classes, List<String> attrs, List<Integer> path) {
            this.tag = tag;
            this.id = id;
            this.classes = classes;
            this.attrs = Collections.unmodifiableList(attrs);
            this.path = Collections.unmodifiableList(path);
        }

        /** True for the honest "…N more" cap placeholder rows. */
        public boolean isPlaceholder() {
            return tag.startsWith("…");
        }

        /** The JTree label: {@code tag#id.class} plus attr count. */
        public String label() {
            if (isPlaceholder()) {
                return tag;
            }
            StringBuilder sb = new StringBuilder(tag);
            if (!id.isEmpty()) {
                sb.append('#').append(id);
            }
            if (!classes.isEmpty()) {
                sb.append('.').append(classes.replace(' ', '.'));
            }
            if (!attrs.isEmpty()) {
                sb.append("  [").append(attrs.size()).append(" attr").append(attrs.size() == 1 ? "" : "s").append(']');
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return label();
        }
    }

    private DomSnapshotParser() {
    }

    /**
     * Parses the snapshot; malformed or empty input yields a single
     * note node ("(no DOM snapshot)") so the tree always renders
     * something honest.
     */
    public static DomNode parse(String json) {
        Object v = JsonLite.parse(json);
        if (!(v instanceof Map) || JsonLite.asObject(v).isEmpty()) {
            return new DomNode("(no DOM snapshot)", "", "", List.of(), List.of());
        }
        // Iterative walk: pairs of (source map, target parent).
        DomNode root = shallow(JsonLite.asObject(v));
        ArrayList<Object[]> work = new ArrayList<>();
        work.add(new Object[]{JsonLite.asObject(v), root});
        int guard = 0;
        while (!work.isEmpty() && guard++ < 20000) {
            Object[] item = work.remove(work.size() - 1);
            Map<String, Object> src = JsonLite.asObject(item[0]);
            DomNode target = (DomNode) item[1];
            List<Object> kids = JsonLite.asArray(src.get("k"));
            for (Object kid : kids) {
                if (kid instanceof Map) {
                    Map<String, Object> km = JsonLite.asObject(kid);
                    DomNode child = shallow(km);
                    target.children.add(child);
                    work.add(new Object[]{km, child});
                }
            }
        }
        return root;
    }

    /** Java-side ceiling on any page-supplied label fragment. */
    private static String clip(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static DomNode shallow(Map<String, Object> o) {
        // the page-side caps in DevScripts are page-CONTROLLED (a page can
        // redefine tagName's getter to return megabytes), so re-impose
        // them here — the sibling parsers already do
        String tag = clip(JsonLite.str(o, "t", "#node"));
        String id = clip(JsonLite.str(o, "i", ""));
        String classes = clip(JsonLite.str(o, "c", ""));
        List<String> attrs = new ArrayList<>();
        for (Object a : JsonLite.asArray(o.get("a"))) {
            if (a instanceof String s) {
                attrs.add(s.length() > 250 ? s.substring(0, 250) : s);
            }
        }
        List<Integer> path = new ArrayList<>();
        for (Object p : JsonLite.asArray(o.get("p"))) {
            if (p instanceof Double d) {
                path.add((int) (double) d);
            }
        }
        return new DomNode(tag, id, classes, attrs, path);
    }
}
