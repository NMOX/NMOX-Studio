package org.nmox.studio.editor.snippets;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.snippets.SnippetBody.FormatGroup;
import org.nmox.studio.editor.snippets.SnippetBody.FormatText;
import org.nmox.studio.editor.snippets.SnippetBody.Node;
import org.nmox.studio.editor.snippets.SnippetBody.Refused;
import org.nmox.studio.editor.snippets.SnippetBody.TabStop;
import org.nmox.studio.editor.snippets.SnippetBody.Text;
import org.nmox.studio.editor.snippets.SnippetBody.Transform;
import org.nmox.studio.editor.snippets.SnippetBody.Variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * VS Code's snippet grammar, construct by construct, and then the text
 * that is not a construct at all: the parser has to agree with VS Code
 * about both, or a body means something else here.
 */
class SnippetBodyTest {

    private static List<Node> parse(String body) throws Refused {
        return SnippetBody.parse(body).nodes();
    }

    private static TabStop stop(int n) {
        return new TabStop(n, List.of(), List.of(), null);
    }

    private static TabStop stop(int n, Node... children) {
        return new TabStop(n, List.of(children), List.of(), null);
    }

    private static Variable variable(String name) {
        return new Variable(name, List.of(), null);
    }

    @Test
    @DisplayName("$1, ${1} and $0 are tab stops; text around them is one node each")
    void tabStops() throws Refused {
        assertThat(parse("a $1 b ${2} c$0")).containsExactly(
                new Text("a "), stop(1), new Text(" b "), stop(2), new Text(" c"), stop(0));
        assertThat(parse("$12abc")).containsExactly(stop(12), new Text("abc"));
    }

    @Test
    @DisplayName("a placeholder's default holds text, variables and further placeholders")
    void placeholdersNest() throws Refused {
        assertThat(parse("${1:foo ${2:bar} $TM_FILENAME}")).containsExactly(
                stop(1, new Text("foo "), stop(2, new Text("bar")), new Text(" "), variable("TM_FILENAME")));
        assertThat(parse("${1:}")).containsExactly(stop(1));
    }

    @Test
    @DisplayName("the same number twice is two nodes of one number: mirrors")
    void mirrors() throws Refused {
        assertThat(parse("${1:name} = $1;")).containsExactly(
                stop(1, new Text("name")), new Text(" = "), stop(1), new Text(";"));
    }

    @Test
    @DisplayName("a choice lists its options; \\, and \\| are the characters")
    void choices() throws Refused {
        assertThat(parse("${1|one,two,three|}")).containsExactly(
                new TabStop(1, List.of(), List.of("one", "two", "three"), null));
        assertThat(parse("${2|a\\,b,c\\|d,e\\\\f|}")).containsExactly(
                new TabStop(2, List.of(), List.of("a,b", "c|d", "e\\f"), null));
        assertThat(parse("${1|it,test|}('${2:does}')")).first()
                .isEqualTo(new TabStop(1, List.of(), List.of("it", "test"), null));
    }

    @Test
    @DisplayName("a choice that does not close, is empty, or is numbered 0 is the text it was written as")
    void brokenChoicesAreText() throws Refused {
        assertThat(parse("${1|one,two}")).containsExactly(new Text("${1|one,two}"));
        assertThat(parse("${1||}")).containsExactly(new Text("${1||}"));
        assertThat(parse("${0|a,b|}")).containsExactly(new Text("${0|a,b|}"));
    }

    @Test
    @DisplayName("$NAME, ${NAME} and ${NAME:default} are variables")
    void variables() throws Refused {
        assertThat(parse("$TM_FILENAME ${CURRENT_YEAR} ${author:someone $1}")).containsExactly(
                variable("TM_FILENAME"), new Text(" "), variable("CURRENT_YEAR"), new Text(" "),
                new Variable("author", List.of(new Text("someone "), stop(1)), null));
        assertThat(parse("$_x9-")).containsExactly(variable("_x9"), new Text("-"));
    }

    @Test
    @DisplayName("\\$ \\} and \\\\ are the character; a backslash before anything else is a backslash")
    void escapes() throws Refused {
        assertThat(parse("\\$1 \\} \\\\ \\n \\{")).containsExactly(new Text("$1 } \\ \\n \\{"));
        assertThat(parse("${1:a\\}b}")).containsExactly(stop(1, new Text("a}b")));
        assertThat(parse("end\\")).containsExactly(new Text("end\\"));
    }

    @Test
    @DisplayName("a dollar that starts nothing is a dollar")
    void loneDollars() throws Refused {
        assertThat(parse("cost $ 5 and $$ and ${ and ${} and $")).containsExactly(
                new Text("cost $ 5 and $$ and ${ and ${} and $"));
        assertThat(parse("${no-indent} ${1 2}")).containsExactly(new Text("${no-indent} ${1 2}"));
    }

    @Test
    @DisplayName("a placeholder that never closes is its opening as text, with what was inside it kept")
    void unterminatedPlaceholder() throws Refused {
        assertThat(parse("${1:foo $2 bar")).containsExactly(
                new Text("${1:foo "), stop(2), new Text(" bar"));
        assertThat(parse("${name:foo")).containsExactly(new Text("${name:foo"));
        assertThat(parse("x ${")).containsExactly(new Text("x ${"));
    }

    @Test
    @DisplayName("a transform has a pattern, a format and flags; \\/ in the pattern is a slash")
    void transforms() throws Refused {
        assertThat(parse("${TM_FILENAME/(.*)\\..+$/$1/}")).containsExactly(
                new Variable("TM_FILENAME", List.of(), new Transform("(.*)\\..+$",
                        List.of(new FormatGroup(1, null, null, null)), "")));
        assertThat(parse("${1/a\\/b/x\\/y/gi}")).containsExactly(
                new TabStop(1, List.of(), List.of(), new Transform("a/b", List.of(new FormatText("x/y")), "gi")));
    }

    @Test
    @DisplayName("a format's group takes a modifier or a conditional")
    void formatGroups() throws Refused {
        Variable v = (Variable) parse(
                "${TM_FILENAME_BASE/^(.)(.*)$/${1:/upcase}${2}-${3:+yes}-${4:?a:b}-${5:-no}-${6:other}/}").get(0);
        assertThat(v.transform().format()).containsExactly(
                new FormatGroup(1, "upcase", null, null),
                new FormatGroup(2, null, null, null),
                new FormatText("-"),
                new FormatGroup(3, null, "yes", null),
                new FormatText("-"),
                new FormatGroup(4, null, "a", "b"),
                new FormatText("-"),
                new FormatGroup(5, null, null, "no"),
                new FormatText("-"),
                new FormatGroup(6, null, null, "other"));
    }

    @Test
    @DisplayName("a transform that never closes is text, as in VS Code")
    void unterminatedTransformIsText() throws Refused {
        assertThat(parse("${1/abc")).containsExactly(new Text("${1/abc"));
        assertThat(parse("${TM_FILENAME/(.*)/$1")).containsExactly(new Text("${TM_FILENAME/(.*)/"), stop(1));
    }

    @Test
    @DisplayName("ten thousand placeholders inside one another are refused by name, quickly")
    void depthIsBounded() {
        String deep = "${1:".repeat(2_000);
        long start = System.nanoTime();
        assertThatThrownBy(() -> SnippetBody.parse(deep))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("nested deeper than " + SnippetBody.MAX_DEPTH);
        assertThat((System.nanoTime() - start) / 1_000_000L).isLessThan(2_000);
        assertThatThrownBy(() -> SnippetBody.parse("${a:".repeat(40) + "x" + "}".repeat(40)))
                .isInstanceOf(Refused.class).hasMessageContaining("nested deeper");
    }

    @Test
    @DisplayName("the deepest body allowed parses; one level more does not")
    void depthBoundary() throws Refused {
        String ok = "${1:".repeat(SnippetBody.MAX_DEPTH) + "x" + "}".repeat(SnippetBody.MAX_DEPTH);
        assertThat(parse(ok)).hasSize(1);
        String over = "${1:".repeat(SnippetBody.MAX_DEPTH + 1) + "x" + "}".repeat(SnippetBody.MAX_DEPTH + 1);
        assertThatThrownBy(() -> SnippetBody.parse(over)).isInstanceOf(Refused.class);
    }

    @Test
    @DisplayName("a megabyte of body is refused before it is parsed")
    void lengthIsBounded() {
        String huge = "x".repeat(1024 * 1024);
        assertThatThrownBy(() -> SnippetBody.parse(huge))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("1048576 characters")
                .hasMessageContaining("over the " + SnippetBody.MAX_CHARS + " limit");
    }

    @Test
    @DisplayName("more tab stops than any snippet has are refused; plain text of the same size is not")
    void nodesAreBounded() throws Refused {
        assertThatThrownBy(() -> SnippetBody.parse("$1".repeat(SnippetBody.MAX_NODES + 1)))
                .isInstanceOf(Refused.class).hasMessageContaining("tab stops and variables");
        assertThat(parse("a b ".repeat(4000))).hasSize(1);
    }

    @Test
    @DisplayName("a tab stop numbered past four digits is refused")
    void numbersAreBounded() {
        assertThatThrownBy(() -> SnippetBody.parse("$123456")).isInstanceOf(Refused.class)
                .hasMessageContaining("123456");
        assertThatThrownBy(() -> SnippetBody.parse("${" + "9".repeat(400) + ":x}")).isInstanceOf(Refused.class);
    }

    @Test
    @DisplayName("a transform that cannot be honoured refuses the whole body, by name")
    void hostileTransformRefusesTheBody() {
        assertThatThrownBy(() -> SnippetBody.parse("ok ${TM_FILENAME/(a+)+$/x/} $1"))
                .isInstanceOf(Refused.class).hasMessageContaining("repeats a group that itself repeats");
        assertThatThrownBy(() -> SnippetBody.parse("${1:${TM_FILENAME/(/x/}}"))
                .as("inside a default too").isInstanceOf(Refused.class).hasMessageContaining("does not compile");
    }

    @Test
    @DisplayName("line breaks and tabs are text; nothing about them is decided here")
    void whitespaceIsText() throws Refused {
        assertThat(parse("if ($1) {\n\t$0\n}")).containsExactly(
                new Text("if ("), stop(1), new Text(") {\n\t"), stop(0), new Text("\n}"));
    }
}
