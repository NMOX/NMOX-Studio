package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.browser.devtools.DomSnapshotParser.DomNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The DOM snapshot parser: realistic fixture, cap-marker placeholders,
 * missing fields, malformed input, and the no-StackOverflow law.
 */
class DomSnapshotParserTest {

    private static final String FIXTURE =
            "{\"t\":\"html\",\"p\":[],\"k\":["
            + "{\"t\":\"head\",\"p\":[0],\"k\":[{\"t\":\"title\",\"p\":[0,0]}]},"
            + "{\"t\":\"body\",\"p\":[1],\"i\":\"app\",\"c\":\"dark theme\","
            + "\"a\":[\"data-v=\\\"1\\\"\",\"lang=\\\"en\\\"\"],\"k\":["
            + "{\"t\":\"div\",\"p\":[1,0],\"c\":\"container\"},"
            + "{\"t\":\"…37 more\",\"p\":[]}"
            + "]}]}";

    @Test
    @DisplayName("parses a realistic snapshot into the typed tree")
    void parsesFixture() {
        DomNode root = DomSnapshotParser.parse(FIXTURE);
        assertThat(root.tag).isEqualTo("html");
        assertThat(root.path).isEmpty();
        assertThat(root.children).hasSize(2);

        DomNode body = root.children.get(1);
        assertThat(body.tag).isEqualTo("body");
        assertThat(body.id).isEqualTo("app");
        assertThat(body.classes).isEqualTo("dark theme");
        assertThat(body.attrs).containsExactly("data-v=\"1\"", "lang=\"en\"");
        assertThat(body.path).containsExactly(1);

        DomNode title = root.children.get(0).children.get(0);
        assertThat(title.path).containsExactly(0, 0);
    }

    @Test
    @DisplayName("the …N more cap placeholder is recognized")
    void placeholderRecognized() {
        DomNode root = DomSnapshotParser.parse(FIXTURE);
        DomNode placeholder = root.children.get(1).children.get(1);
        assertThat(placeholder.isPlaceholder()).isTrue();
        assertThat(placeholder.label()).isEqualTo("…37 more");
        assertThat(root.children.get(1).isPlaceholder()).isFalse();
    }

    @Test
    @DisplayName("labels render tag#id.class with attr count")
    void labels() {
        DomNode root = DomSnapshotParser.parse(FIXTURE);
        assertThat(root.label()).isEqualTo("html");
        assertThat(root.children.get(1).label())
                .isEqualTo("body#app.dark.theme  [2 attrs]");
    }

    @Test
    @DisplayName("missing fields default instead of failing")
    void missingFieldsDefault() {
        DomNode n = DomSnapshotParser.parse("{\"t\":\"div\"}");
        assertThat(n.tag).isEqualTo("div");
        assertThat(n.id).isEmpty();
        assertThat(n.classes).isEmpty();
        assertThat(n.attrs).isEmpty();
        assertThat(n.children).isEmpty();
        // even the tag can be absent
        assertThat(DomSnapshotParser.parse("{\"x\":1}").tag).isEqualTo("#node");
    }

    @Test
    @DisplayName("malformed input is a single honest note node, never a throw")
    void malformedIsNote() {
        for (String bad : new String[]{null, "", "not json", "[]", "{}", "{\"t\":"}) {
            DomNode n = DomSnapshotParser.parse(bad);
            assertThat(n.tag).isEqualTo("(no DOM snapshot)");
            assertThat(n.children).isEmpty();
        }
    }

    @Test
    @DisplayName("hostile deep nesting cannot StackOverflow the parser")
    void hostileDepthSafe() {
        // 10k-deep k-nesting: JsonLite's depth cap refuses it upstream
        StringBuilder deep = new StringBuilder();
        deep.append("{\"t\":\"d\",\"k\":[".repeat(10_000)).append("{\"t\":\"x\"}");
        deep.append("]}".repeat(10_000));
        DomNode n = DomSnapshotParser.parse(deep.toString());
        assertThat(n.tag).isEqualTo("(no DOM snapshot)");
        // and a within-cap 60-deep real snapshot parses
        StringBuilder ok = new StringBuilder();
        ok.append("{\"t\":\"d\",\"k\":[".repeat(60)).append("{\"t\":\"leaf\"}");
        ok.append("]}".repeat(60));
        assertThat(DomSnapshotParser.parse(ok.toString()).tag).isEqualTo("d");
    }
}
