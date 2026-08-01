package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@link DevScripts#ANGULAR_SNAPSHOT} JSON into a typed
 * component tree for the Angular tab. Same hostile-input posture as
 * {@link VueSnapshotParser}: never throws, iterative walk (explicit
 * work list — deep hostile JSON cannot StackOverflow), missing fields
 * default, every string re-capped Java-side (the page is untrusted
 * even about its own caps), and both honest empty answers are
 * first-class: "no Angular on this page" (blank version, empty roots)
 * and "Angular present but production build" ({@link
 * NgTree#productionOnly} carries the {@code ng-version} the page DID
 * stamp — a prod build strips {@code window.ng}, so no inspector can
 * walk it; the official Angular DevTools is limited the same way).
 */
public final class AngularSnapshotParser {

    /** One Angular component node: class name, state, host directives. */
    public static final class NgNode {

        public final String name;
        public final Map<String, String> state;
        public final List<String> directives;
        public final List<Integer> domPath;
        public final List<NgNode> children = new ArrayList<>();

        NgNode(String name, Map<String, String> state, List<String> directives,
                List<Integer> domPath) {
            this.name = name;
            this.state = Collections.unmodifiableMap(state);
            this.directives = Collections.unmodifiableList(directives);
            this.domPath = Collections.unmodifiableList(domPath);
        }

        @Override
        public String toString() {
            return "<" + name + ">";
        }
    }

    /** The whole snapshot: version string, roots, or the prod refusal. */
    public static final class NgTree {

        public final String version;
        public final List<NgNode> roots;
        /**
         * Non-empty when Angular is ON the page (it stamps
         * {@code ng-version} in dev and prod alike) but the tree is
         * unreachable because the build stripped {@code window.ng} —
         * i.e. a production build. Carries the version string so the
         * pane can say so precisely instead of claiming no Angular.
         */
        public final String productionOnly;

        NgTree(String version, List<NgNode> roots, String productionOnly) {
            this.version = version == null ? "" : version;
            this.roots = Collections.unmodifiableList(roots);
            this.productionOnly = productionOnly == null ? "" : productionOnly;
        }

        /** True when no walkable Angular component tree was found. */
        public boolean empty() {
            return roots.isEmpty();
        }
    }

    private AngularSnapshotParser() {
    }

    /** Parses the snapshot; malformed input is "no Angular detected". */
    public static NgTree parse(String json) {
        Object v = JsonLite.parse(json);
        if (!(v instanceof Map)) {
            return new NgTree("", List.of(), "");
        }
        Map<String, Object> o = JsonLite.asObject(v);
        List<NgNode> roots = new ArrayList<>();
        ArrayList<Object[]> work = new ArrayList<>();
        int[] budget = {2000}; // the script's component cap, re-imposed
        for (Object r : JsonLite.asArray(o.get("r"))) {
            if (r instanceof Map && budget[0] > 0) {
                budget[0]--;
                NgNode node = shallow(JsonLite.asObject(r));
                roots.add(node);
                work.add(new Object[]{r, node});
            }
        }
        int guard = 0;
        while (!work.isEmpty() && guard++ < 10000) {
            Object[] item = work.remove(work.size() - 1);
            Map<String, Object> src = JsonLite.asObject(item[0]);
            NgNode target = (NgNode) item[1];
            for (Object kid : JsonLite.asArray(src.get("k"))) {
                if (kid instanceof Map && budget[0] > 0) {
                    budget[0]--;
                    NgNode child = shallow(JsonLite.asObject(kid));
                    target.children.add(child);
                    work.add(new Object[]{kid, child});
                }
            }
        }
        return new NgTree(cap(JsonLite.str(o, "v", ""), 20), roots,
                cap(JsonLite.str(o, "prod", ""), 20));
    }

    private static NgNode shallow(Map<String, Object> o) {
        String name = JsonLite.str(o, "n", "Anonymous");
        if (name.isBlank()) {
            name = "Anonymous";
        }
        name = cap(name, 200);
        Map<String, String> state = stringBag(o.get("s"));
        List<String> directives = new ArrayList<>();
        for (Object d : JsonLite.asArray(o.get("dir"))) {
            if (directives.size() >= 20) {
                break; // the script's own cap, re-imposed on hostile input
            }
            if (d instanceof String s && !s.isBlank()) {
                directives.add(cap(s, 200));
            }
        }
        List<Integer> domPath = new ArrayList<>();
        for (Object p : JsonLite.asArray(o.get("d"))) {
            if (p instanceof Double d) {
                domPath.add((int) (double) d);
            }
        }
        return new NgNode(name, state, directives, domPath);
    }

    private static Map<String, String> stringBag(Object v) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : JsonLite.asObject(v).entrySet()) {
            String val = e.getValue() instanceof String s ? s : String.valueOf(e.getValue());
            out.put(cap(e.getKey(), 200), cap(val, 2000));
        }
        return out;
    }

    private static String cap(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
