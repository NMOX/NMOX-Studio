package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@link DevScripts#VUE_SNAPSHOT} JSON into a typed
 * component tree for the Vue tab. Same hostile-input posture as
 * {@link DomSnapshotParser}: never throws, iterative walk (explicit
 * work list — deep hostile JSON cannot StackOverflow), missing fields
 * default, and "no Vue on this page" is a first-class answer
 * ({@code version == 0}, empty roots) rather than an error.
 */
public final class VueSnapshotParser {

    /** One Vue component node. */
    public static final class VueNode {

        public final String name;
        public final Map<String, String> props;
        public final Map<String, String> state;
        public final List<Integer> domPath;
        public final List<VueNode> children = new ArrayList<>();

        VueNode(String name, Map<String, String> props, Map<String, String> state,
                List<Integer> domPath) {
            this.name = name;
            this.props = Collections.unmodifiableMap(props);
            this.state = Collections.unmodifiableMap(state);
            this.domPath = Collections.unmodifiableList(domPath);
        }

        @Override
        public String toString() {
            return "<" + name + ">";
        }
    }

    /** The whole snapshot: detected version (0 = none), root components. */
    public static final class VueTree {

        public final int version;
        public final List<VueNode> roots;
        /**
         * Non-empty when Vue is ON the page but its component tree is
         * unreachable: a PRODUCTION build exposes neither
         * {@code app._instance} nor {@code __vueParentComponent} (both
         * are dev/devtools-gated), so there is nothing to walk. Carries
         * the app's version string so the pane can say so precisely
         * instead of claiming no Vue at all.
         */
        public final String productionOnly;

        VueTree(int version, List<VueNode> roots, String productionOnly) {
            this.version = version;
            this.roots = Collections.unmodifiableList(roots);
            this.productionOnly = productionOnly == null ? "" : productionOnly;
        }

        /** True when no Vue (2 or 3) was detected on the page. */
        public boolean empty() {
            return roots.isEmpty();
        }
    }

    private VueSnapshotParser() {
    }

    /** Parses the snapshot; malformed input is "no Vue detected". */
    public static VueTree parse(String json) {
        Object v = JsonLite.parse(json);
        if (!(v instanceof Map)) {
            return new VueTree(0, List.of(), "");
        }
        Map<String, Object> o = JsonLite.asObject(v);
        int version = JsonLite.num(o, "v", 0);
        List<VueNode> roots = new ArrayList<>();
        ArrayList<Object[]> work = new ArrayList<>();
        for (Object r : JsonLite.asArray(o.get("r"))) {
            if (r instanceof Map) {
                VueNode node = shallow(JsonLite.asObject(r));
                roots.add(node);
                work.add(new Object[]{r, node});
            }
        }
        int guard = 0;
        while (!work.isEmpty() && guard++ < 10000) {
            Object[] item = work.remove(work.size() - 1);
            Map<String, Object> src = JsonLite.asObject(item[0]);
            VueNode target = (VueNode) item[1];
            for (Object kid : JsonLite.asArray(src.get("k"))) {
                if (kid instanceof Map) {
                    VueNode child = shallow(JsonLite.asObject(kid));
                    target.children.add(child);
                    work.add(new Object[]{kid, child});
                }
            }
        }
        return new VueTree(roots.isEmpty() ? 0 : version, roots,
                JsonLite.str(o, "prod", ""));
    }

    private static VueNode shallow(Map<String, Object> o) {
        String name = JsonLite.str(o, "n", "Anonymous");
        if (name.isBlank()) {
            name = "Anonymous";
        }
        if (name.length() > 200) {
            name = name.substring(0, 200);
        }
        Map<String, String> props = stringBag(o.get("p"));
        Map<String, String> state = stringBag(o.get("s"));
        List<Integer> domPath = new ArrayList<>();
        for (Object p : JsonLite.asArray(o.get("d"))) {
            if (p instanceof Double d) {
                domPath.add((int) (double) d);
            }
        }
        return new VueNode(name, props, state, domPath);
    }

    private static Map<String, String> stringBag(Object v) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : JsonLite.asObject(v).entrySet()) {
            String val = e.getValue() instanceof String s ? s : String.valueOf(e.getValue());
            if (val.length() > 2000) {
                val = val.substring(0, 2000);
            }
            String key = e.getKey().length() > 200 ? e.getKey().substring(0, 200) : e.getKey();
            out.put(key, val);
        }
        return out;
    }
}
