package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Angular tab's parser: typed tree out of snapshot JSON, hostile
 * input never throws, and both honest empty answers — "no Angular"
 * and "production build" — are first-class (v1.222.0).
 */
class AngularSnapshotParserTest {

    @Test
    @DisplayName("a dev-build snapshot parses into a nested component tree")
    void parsesNestedTree() {
        String json = "{\"v\":\"17.3.0\",\"prod\":\"\",\"r\":[{"
                + "\"n\":\"AppComponent\",\"s\":{\"title\":\"\\\"demo\\\"\"},"
                + "\"dir\":[\"RouterOutlet\"],\"d\":[1,0],"
                + "\"k\":[{\"n\":\"HelloComponent\",\"s\":{\"count\":\"3\"},"
                + "\"dir\":[],\"d\":[1,0,0],\"k\":[]}]}]}";
        AngularSnapshotParser.NgTree tree = AngularSnapshotParser.parse(json);
        assertThat(tree.empty()).isFalse();
        assertThat(tree.version).isEqualTo("17.3.0");
        assertThat(tree.productionOnly).isEmpty();
        assertThat(tree.roots).hasSize(1);
        AngularSnapshotParser.NgNode app = tree.roots.get(0);
        assertThat(app.name).isEqualTo("AppComponent");
        assertThat(app.state).containsEntry("title", "\"demo\"");
        assertThat(app.directives).containsExactly("RouterOutlet");
        assertThat(app.domPath).containsExactly(1, 0);
        assertThat(app.children).hasSize(1);
        assertThat(app.children.get(0).name).isEqualTo("HelloComponent");
    }

    @Test
    @DisplayName("production build: ng-version seen, tree unreachable — prod carries the version")
    void productionBuildIsItsOwnAnswer() {
        AngularSnapshotParser.NgTree tree = AngularSnapshotParser.parse(
                "{\"v\":\"17.3.0\",\"prod\":\"17.3.0\",\"r\":[]}");
        assertThat(tree.empty()).isTrue();
        assertThat(tree.productionOnly).isEqualTo("17.3.0");
    }

    @Test
    @DisplayName("no Angular: blank version, empty roots, empty prod")
    void noAngularIsEmptyNotError() {
        AngularSnapshotParser.NgTree tree = AngularSnapshotParser.parse(
                "{\"v\":\"\",\"prod\":\"\",\"r\":[]}");
        assertThat(tree.empty()).isTrue();
        assertThat(tree.version).isEmpty();
        assertThat(tree.productionOnly).isEmpty();
    }

    @Test
    @DisplayName("malformed input parses as no Angular, never throws")
    void malformedNeverThrows() {
        assertThat(AngularSnapshotParser.parse(null).empty()).isTrue();
        assertThat(AngularSnapshotParser.parse("").empty()).isTrue();
        assertThat(AngularSnapshotParser.parse("not json").empty()).isTrue();
        assertThat(AngularSnapshotParser.parse("[1,2,3]").empty()).isTrue();
        assertThat(AngularSnapshotParser.parse("{\"r\":\"nope\"}").empty()).isTrue();
    }

    @Test
    @DisplayName("hostile caps re-imposed Java-side: name 200, value 2000, 20 directives, 2000 components")
    void hostileCapsReimposed() {
        String longName = "X".repeat(500);
        String longVal = "y".repeat(5000);
        StringBuilder dirs = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            if (i > 0) {
                dirs.append(',');
            }
            dirs.append("\"D").append(i).append('"');
        }
        String json = "{\"v\":\"9\",\"r\":[{\"n\":\"" + longName + "\","
                + "\"s\":{\"a\":\"" + longVal + "\"},\"dir\":[" + dirs + "],"
                + "\"d\":[],\"k\":[]}]}";
        AngularSnapshotParser.NgNode node = AngularSnapshotParser.parse(json).roots.get(0);
        assertThat(node.name).hasSize(200);
        assertThat(node.state.get("a")).hasSize(2000);
        assertThat(node.directives).hasSize(20);

        // a hostile page cannot flood the tree past the component budget
        StringBuilder flood = new StringBuilder("{\"v\":\"9\",\"r\":[");
        for (int i = 0; i < 3000; i++) {
            if (i > 0) {
                flood.append(',');
            }
            flood.append("{\"n\":\"C").append(i).append("\",\"s\":{},\"dir\":[],\"d\":[],\"k\":[]}");
        }
        flood.append("]}");
        assertThat(AngularSnapshotParser.parse(flood.toString()).roots).hasSize(2000);
    }

    @Test
    @DisplayName("hostile deep nesting cannot StackOverflow the parser")
    void deepNestingCannotOverflow() {
        // past JsonLite's depth cap: refused as a whole, never a crash
        StringBuilder deep = new StringBuilder("{\"v\":\"9\",\"r\":[");
        deep.append("{\"n\":\"C\",\"k\":[".repeat(10_000)).append("{\"n\":\"L\"}");
        deep.append("]}".repeat(10_000)).append("]}");
        assertThat(AngularSnapshotParser.parse(deep.toString()).empty()).isTrue();

        // a realistic 40-deep tree still parses fine
        StringBuilder ok = new StringBuilder("{\"v\":\"9\",\"r\":[");
        ok.append("{\"n\":\"C\",\"k\":[".repeat(40)).append("{\"n\":\"L\"}");
        ok.append("]}".repeat(40)).append("]}");
        AngularSnapshotParser.NgTree tree = AngularSnapshotParser.parse(ok.toString());
        assertThat(tree.empty()).isFalse();
        AngularSnapshotParser.NgNode n = tree.roots.get(0);
        int hops = 0;
        while (!n.children.isEmpty()) {
            n = n.children.get(0);
            hops++;
        }
        assertThat(n.name).isEqualTo("L");
        assertThat(hops).isEqualTo(40);
    }
}
