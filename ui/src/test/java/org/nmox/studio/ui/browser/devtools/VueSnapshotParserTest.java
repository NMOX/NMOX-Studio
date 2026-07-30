package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.browser.devtools.VueSnapshotParser.VueNode;
import org.nmox.studio.ui.browser.devtools.VueSnapshotParser.VueTree;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Vue snapshot parser: both version fixtures, props/state maps,
 * DOM paths, the honest no-Vue answer, and hostile-input safety.
 */
class VueSnapshotParserTest {

    private static final String VUE3_FIXTURE =
            "{\"v\":3,\"r\":[{\"n\":\"App\",\"p\":{},\"s\":{\"count\":\"0\"},\"d\":[0],\"k\":["
            + "{\"n\":\"TodoList\",\"p\":{\"items\":\"[{\\\"id\\\":1}]\"},"
            + "\"s\":{\"filter\":\"\\\"all\\\"\"},\"d\":[0,1],\"k\":["
            + "{\"n\":\"TodoItem\",\"p\":{\"todo\":\"{\\\"id\\\":1}\"},\"s\":{},\"d\":[0,1,0],\"k\":[]}"
            + "]}]}]}";

    private static final String VUE2_FIXTURE =
            "{\"v\":2,\"r\":[{\"n\":\"Root\",\"p\":{},\"s\":{\"msg\":\"\\\"hi\\\"\"},"
            + "\"d\":[],\"k\":[{\"n\":\"child-card\",\"p\":{\"title\":\"\\\"x\\\"\"},\"s\":{},\"d\":[0,0],\"k\":[]}]}]}";

    @Test
    @DisplayName("parses a Vue 3 component tree with props, state, and DOM paths")
    void parsesVue3() {
        VueTree t = VueSnapshotParser.parse(VUE3_FIXTURE);
        assertThat(t.version).isEqualTo(3);
        assertThat(t.empty()).isFalse();
        assertThat(t.roots).hasSize(1);

        VueNode app = t.roots.get(0);
        assertThat(app.name).isEqualTo("App");
        assertThat(app.state).containsEntry("count", "0");
        assertThat(app.domPath).containsExactly(0);

        VueNode list = app.children.get(0);
        assertThat(list.name).isEqualTo("TodoList");
        assertThat(list.props).containsEntry("items", "[{\"id\":1}]");
        assertThat(list.state).containsEntry("filter", "\"all\"");

        VueNode item = list.children.get(0);
        assertThat(item.name).isEqualTo("TodoItem");
        assertThat(item.domPath).containsExactly(0, 1, 0);
        assertThat(item.children).isEmpty();
    }

    @Test
    @DisplayName("parses a Vue 2 tree the same way")
    void parsesVue2() {
        VueTree t = VueSnapshotParser.parse(VUE2_FIXTURE);
        assertThat(t.version).isEqualTo(2);
        assertThat(t.roots.get(0).name).isEqualTo("Root");
        assertThat(t.roots.get(0).children.get(0).name).isEqualTo("child-card");
        assertThat(t.roots.get(0).children.get(0).props).containsEntry("title", "\"x\"");
    }

    @Test
    @DisplayName("no Vue on the page is a first-class empty answer")
    void noVueIsEmpty() {
        VueTree t = VueSnapshotParser.parse("{\"v\":null,\"r\":[]}");
        assertThat(t.empty()).isTrue();
        assertThat(t.version).isZero();
    }

    @Test
    @DisplayName("malformed input is 'no Vue', never a throw")
    void malformedIsEmpty() {
        for (String bad : new String[]{null, "", "nope", "[]", "{\"v\":3,\"r\":"}) {
            assertThat(VueSnapshotParser.parse(bad).empty()).isTrue();
        }
    }

    @Test
    @DisplayName("missing fields default; blank names become Anonymous")
    void missingFieldsDefault() {
        VueTree t = VueSnapshotParser.parse("{\"v\":3,\"r\":[{\"x\":1},{\"n\":\"  \"}]}");
        assertThat(t.roots).hasSize(2);
        assertThat(t.roots.get(0).name).isEqualTo("Anonymous");
        assertThat(t.roots.get(1).name).isEqualTo("Anonymous");
        assertThat(t.roots.get(0).props).isEmpty();
        assertThat(t.roots.get(0).domPath).isEmpty();
    }

    @Test
    @DisplayName("hostile deep nesting cannot StackOverflow the parser")
    void hostileDepthSafe() {
        StringBuilder deep = new StringBuilder("{\"v\":3,\"r\":[");
        deep.append("{\"n\":\"C\",\"k\":[".repeat(10_000)).append("{\"n\":\"L\"}");
        deep.append("]}".repeat(10_000)).append("]}");
        assertThat(VueSnapshotParser.parse(deep.toString()).empty()).isTrue();
        // within-cap depth parses
        StringBuilder ok = new StringBuilder("{\"v\":3,\"r\":[");
        ok.append("{\"n\":\"C\",\"k\":[".repeat(40)).append("{\"n\":\"L\"}");
        ok.append("]}".repeat(40)).append("]}");
        assertThat(VueSnapshotParser.parse(ok.toString()).empty()).isFalse();
    }

    @Test
    @DisplayName("hostile oversized names and values are re-capped Java-side")
    void hostileSizesCapped() {
        String big = "{\"v\":2,\"r\":[{\"n\":\"" + "N".repeat(1000) + "\","
                + "\"s\":{\"" + "K".repeat(1000) + "\":\"" + "V".repeat(10_000) + "\"}}]}";
        VueNode n = VueSnapshotParser.parse(big).roots.get(0);
        assertThat(n.name).hasSize(200);
        String key = n.state.keySet().iterator().next();
        assertThat(key).hasSize(200);
        assertThat(n.state.get(key)).hasSize(2000);
    }

    @Test
    @DisplayName("Vue present but a production build reports itself, not \"no Vue\"")
    void productionBuildIsNamed() {
        // what a prod page actually yields: an app was seen (version
        // string) but no roots are reachable — the v1.206.0 gauntlet find
        VueSnapshotParser.VueTree t = VueSnapshotParser.parse(
                "{\"v\":null,\"r\":[],\"prod\":\"3.4.38\"}");
        assertThat(t.empty()).isTrue();
        assertThat(t.productionOnly)
                .as("the pane needs this to explain itself precisely")
                .isEqualTo("3.4.38");
    }

    @Test
    @DisplayName("A genuinely Vue-less page carries no production marker")
    void noVueHasNoProductionMarker() {
        VueSnapshotParser.VueTree t = VueSnapshotParser.parse("{\"v\":null,\"r\":[],\"prod\":\"\"}");
        assertThat(t.empty()).isTrue();
        assertThat(t.productionOnly).isEmpty();
    }
}
