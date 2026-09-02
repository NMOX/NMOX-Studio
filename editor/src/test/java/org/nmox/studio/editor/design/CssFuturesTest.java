package org.nmox.studio.editor.design;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 2031 CSS vocabulary laws: property names are offered only where
 * a declaration name belongs (after { or ;, never inside a value or a
 * selector), values only after one of OUR properties' colons, at-rules
 * from a leading @, and the catalog never duplicates a name the
 * platform already completes (the probe's control terms, pinned).
 */
class CssFuturesTest {

    @Test
    @DisplayName("A property prefix is offered after { and after ; — with whitespace")
    void propertyPositions() {
        assertThat(CssFutures.propertyPrefixAt(".a {\n  anch")).isEqualTo("anch");
        assertThat(CssFutures.propertyPrefixAt(".a { color: red; pos")).isEqualTo("pos");
        assertThat(CssFutures.propertyPrefixAt(".a {")).isEqualTo("");
        assertThat(CssFutures.propertyPrefixAt(".a {\n  ")).isEqualTo("");
    }

    @Test
    @DisplayName("Never inside a value, never in a selector, never at file top")
    void propertyRefusals() {
        // a colon since the boundary means we are in the VALUE
        assertThat(CssFutures.propertyPrefixAt(".a { position-area: to")).isNull();
        // a selector is not a declaration
        assertThat(CssFutures.propertyPrefixAt(".hero .anch")).isNull();
        assertThat(CssFutures.propertyPrefixAt("anch")).isNull();
    }

    @Test
    @DisplayName("Values complete after one of OUR properties' colons only")
    void valueContext() {
        CssFutures.ValueContext vc = CssFutures.valueContextAt(".a { position-area: sp");
        assertThat(vc).isNotNull();
        assertThat(vc.property().name()).isEqualTo("position-area");
        assertThat(vc.partial()).isEqualTo("sp");
        assertThat(vc.property().values()).contains("span-all", "top", "center");
        // a platform-known property is not ours to value
        assertThat(CssFutures.valueContextAt(".a { color: re")).isNull();
        // no colon → not a value position
        assertThat(CssFutures.valueContextAt(".a { position-area")).isNull();
    }

    @Test
    @DisplayName("The second keyword of a multi-keyword value completes too (v2.58.1)")
    void secondKeywordCompletes() {
        CssFutures.ValueContext vc = CssFutures.valueContextAt(".a { position-area: top le");
        assertThat(vc).isNotNull();
        assertThat(vc.property().name()).isEqualTo("position-area");
        assertThat(vc.partial()).isEqualTo("le");
        // the walk never crosses into the previous declaration
        assertThat(CssFutures.valueContextAt(".a { color: red; posi")).isNull();
    }

    @Test
    @DisplayName("Inset and sizing properties gain anchor() / anchor-size() as values")
    void anchorFunctionsOnHosts() {
        CssFutures.ValueContext vc = CssFutures.valueContextAt(".a { top: anc");
        assertThat(vc).isNotNull();
        assertThat(vc.property().values()).containsExactly("anchor(", "anchor-size(");
        assertThat(vc.partial()).isEqualTo("anc");
        // provenance is the host's own name, never a placeholder
        assertThat(vc.property().name()).isEqualTo("top");
        assertThat(CssFutures.valueContextAt(".a { width: ").property().values())
                .contains("anchor-size(");
        // a non-host platform property stays the platform's alone
        assertThat(CssFutures.valueContextAt(".a { color: ")).isNull();
        assertThat(CssFutures.valueContextAt(".a { display: ")).isNull();
    }

    @Test
    @DisplayName("At-rules complete from a leading @ at a declaration position")
    void atRules() {
        assertThat(CssFutures.propertyPrefixAt(".a { @start")).isEqualTo("@start");
        assertThat(CssFutures.AT_RULES).contains("@starting-style", "@position-try");
    }

    @Test
    @DisplayName("Top-level at-rules complete at the file top and after a closed block (v2.58.1)")
    void topLevelAtRules() {
        assertThat(CssFutures.propertyPrefixAt("@posi")).isEqualTo("@posi");
        assertThat(CssFutures.propertyPrefixAt(".a { color: red; }\n\n@view")).isEqualTo("@view");
        // a property NAME at the top level is still a selector, not a declaration
        assertThat(CssFutures.propertyPrefixAt("anch")).isNull();
        assertThat(CssFutures.propertyPrefixAt(".a { color: red; }\nanch")).isNull();
    }

    @Test
    @DisplayName("The catalog never duplicates what the platform already completes")
    void noPlatformDuplicates() {
        // the v2.57.0 probe's control terms were PRESENT in css-lib's DB
        assertThat(CssFutures.properties()).doesNotContainKeys(
                "container-type", "text-wrap", "color", "display");
        // and every catalog entry carries values and a meaning
        CssFutures.properties().values().forEach(p -> {
            assertThat(p.values()).as(p.name() + " values").isNotEmpty();
            assertThat(p.doc()).as(p.name() + " doc").isNotBlank();
        });
    }
}
