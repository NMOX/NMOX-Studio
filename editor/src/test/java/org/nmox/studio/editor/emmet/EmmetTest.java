package org.nmox.studio.editor.emmet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Emmet core's grammar, pinned rule by rule (v1.329.0). Every
 * behavior the expansion chord relies on is a plain assertion here —
 * including the refusals, because an explicit chord must never mangle
 * text it cannot understand.
 */
class EmmetTest {

    private static String html(String abbrev) {
        Emmet.Expansion e = Emmet.expand(abbrev, "  ");
        return e == null ? null : e.html();
    }

    @Test
    @DisplayName("nesting, siblings, and multiplication compose: the canonical ul>li*3")
    void canonicalList() {
        assertThat(html("ul>li*3")).isEqualTo("""
                <ul>
                  <li></li>
                  <li></li>
                  <li></li>
                </ul>""");
    }

    @Test
    @DisplayName("classes, ids, attributes, and text render where they belong")
    void decorations() {
        assertThat(html("div#app.card.dark[data-x=1]{Hello}"))
                .isEqualTo("<div id=\"app\" class=\"card dark\" data-x=\"1\">Hello</div>");
        assertThat(html(".note"))
                .as("leading . implies a div")
                .isEqualTo("<div class=\"note\"></div>");
        assertThat(html("my-badge{ok}"))
                .as("custom elements with dashes are elements too")
                .isEqualTo("<my-badge>ok</my-badge>");
    }

    @Test
    @DisplayName("$ numbering counts 1-based; $$ zero-pads")
    void numbering() {
        assertThat(html("li.item$*3")).isEqualTo("""
                <li class="item1"></li>
                <li class="item2"></li>
                <li class="item3"></li>""");
        assertThat(html("i.p$$*2")).contains("p01").contains("p02");
    }

    @Test
    @DisplayName("groups multiply as a unit")
    void grouping() {
        assertThat(html("(dt{q$}+dd)*2")).isEqualTo("""
                <dt>q1</dt>
                <dd></dd>
                <dt>q2</dt>
                <dd></dd>""");
    }

    @Test
    @DisplayName("void elements close themselves; defaults give a and img their attrs")
    void voidsAndDefaults() {
        assertThat(html("img")).isEqualTo("<img src=\"\" alt=\"\">");
        assertThat(html("a{Home}")).isEqualTo("<a href=\"\">Home</a>");
        assertThat(html("a[href=/docs]{Docs}"))
                .as("an explicit attribute overrides the default's emptiness")
                .isEqualTo("<a href=\"/docs\">Docs</a>");
        assertThat(html("br")).isEqualTo("<br>");
    }

    @Test
    @DisplayName("the caret lands on the first useful empty spot")
    void caretPlacement() {
        Emmet.Expansion e = Emmet.expand("a", "  ");
        // <a href="|"></a> — inside the first empty attribute
        assertThat(e.html().substring(0, e.caretOffset())).isEqualTo("<a href=\"");
        Emmet.Expansion p = Emmet.expand("p", "  ");
        // <p>|</p> — inside the first empty element
        assertThat(p.html().substring(0, p.caretOffset())).isEqualTo("<p>");
    }

    @Test
    @DisplayName("what does not parse returns null — the chord then refuses to touch the text")
    void refusals() {
        assertThat(html("")).isNull();
        assertThat(html("div>")).as("dangling operator").isNull();
        assertThat(html("*3")).as("count without an element").isNull();
        assertThat(html("div[open")).as("unclosed attribute block").isNull();
        assertThat(html("li*0")).as("zero repetitions is a typo, not a wish").isNull();
        assertThat(html("this is prose, not an abbreviation")).isNull();
        assertThat(html("world"))
                .as("a bare word that is not an HTML element stays prose")
                .isNull();
        assertThat(html("section")).as("a bare REAL element expands").isNotNull();
    }

    @Test
    @DisplayName("the abbreviation is the longest parseable suffix of the line")
    void extraction() {
        assertThat(Emmet.abbreviationIn("  ul>li*3")).isEqualTo("ul>li*3");
        assertThat(Emmet.abbreviationIn("<p>ul>li"))
                .as("an existing tag on the line is not swallowed")
                .isEqualTo("ul>li");
        assertThat(Emmet.abbreviationIn("hello world")).isNull();
    }

    @Test
    @DisplayName("auto-pair aware: the caret before an auto-closed } still expands")
    void autoPairClosers() {
        // the shipped-app walk (v1.332.0): typing a.link{Item} the way
        // anyone does leaves the caret BEFORE the editor's auto-closed
        // brace — the abbreviation must fold the trailing closer in
        Emmet.AtCaret at = Emmet.abbreviationAt("  nav>a.link{Item $*3", "}");
        assertThat(at).isNotNull();
        assertThat(at.abbrev()).isEqualTo("nav>a.link{Item $*3}");
        assertThat(at.trailingClosers()).isEqualTo(1);

        // nested pairs: (dt+dd)*2 typed with auto-paren leaves ")*2"? No —
        // the common case is the caret before ONE closer; multiple closers
        // fold only as far as parsing needs
        Emmet.AtCaret group = Emmet.abbreviationAt("(dt+dd", ")");
        assertThat(group.abbrev()).isEqualTo("(dt+dd)");

        // a complete abbreviation before the caret ignores what follows
        Emmet.AtCaret plain = Emmet.abbreviationAt("ul>li*2", "</section>");
        assertThat(plain.abbrev()).isEqualTo("ul>li*2");
        assertThat(plain.trailingClosers()).isZero();

        // still honest: closers that don't complete anything refuse
        assertThat(Emmet.abbreviationAt("hello world", "}")).isNull();
    }
}
