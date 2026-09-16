package org.nmox.studio.ui.browser.fx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The parts of the Browser's shaping bridge that do not need a live WebKit
 * (v2.165.0): the reverse glyph table, the visual ordering of a shaped run, the
 * transformer's class filter, the agent's one-shot handoff, and the bridge's own
 * reflective wiring against the real JavaFX classes on the test classpath.
 */
class PrismBridgeTest {

    @Test
    @DisplayName("the glyph table maps each shaped-script glyph back to its first character, sorted, with the space glyph")
    void glyphTableIsSortedFirstWinsWithBlank() {
        // Arabic alef and alef-with-madda share glyph 900; beh is 800; space is 3; nothing else is in the font
        int[][] table = ComplexTextShaping.PrismBridge.glyphTable(cp -> switch (cp) {
            case 0x0627, 0x0622 -> 900;
            case 0x0628 -> 800;
            case ' ' -> 3;
            default -> 0;
        });
        assertThat(table[0]).containsExactly(800, 900);
        assertThat(table[1]).containsExactly(0x0628, 0x0622);   // the lower code point is sorted first for the shared glyph
        assertThat(table[2]).containsExactly(3);
        assertThat(ComplexTextShaping.PrismBridge.charFor(table, 900)).isEqualTo(0x0622);
        assertThat(ComplexTextShaping.PrismBridge.charFor(table, 5)).isEqualTo(-1);
        assertThat(ComplexTextShaping.PrismBridge.anyShapeable(new int[]{3, 800}, table)).isTrue();
        assertThat(ComplexTextShaping.PrismBridge.anyShapeable(new int[]{3, 4}, table)).isFalse();
    }

    @Test
    @DisplayName("a shaped run is ordered by where the layout put each glyph, advances measured in that order")
    void visualOrderSortsByPosition() {
        ComplexScripts.Shaped shaped = ComplexTextShaping.PrismBridge.visualOrder(
                new int[]{10, 20, 30}, new float[]{40f, 0f, 20f}, new float[]{0f, -4f, 7f}, 55f);
        assertThat(shaped.glyphs()).containsExactly(20, 30, 10);
        assertThat(shaped.advances()).containsExactly(20f, 20f, 15f);
        assertThat(shaped.rises()).containsExactly(-4f, 7f, 0f);
        // "بِ" as a layout lists it, logically: the letter at 10, its kasra drawn at 12 — the advance
        // between them is the 2 the screen shows, not the letter's own 8
        ComplexScripts.Shaped marked = ComplexTextShaping.PrismBridge.visualOrder(
                new int[]{1, 2, 3}, new float[]{10f, 12f, 0f}, new float[]{0f, 5f, 0f}, 18f);
        assertThat(marked.glyphs()).containsExactly(3, 1, 2);
        assertThat(marked.advances()).containsExactly(10f, 2f, 6f);
    }

    @Test
    @DisplayName("the transformer rewrites only WebKit's graphics context")
    void transformerIgnoresOtherClasses() {
        ComplexTextShaping.Transformer t = new ComplexTextShaping.Transformer();
        assertThat(t.transform(null, null, "java/lang/String", null, null, new byte[0])).isNull();
        assertThat(t.applied).isFalse();
    }

    @Test
    @DisplayName("the transformer rewrites the font's glyph width too, and refuses bytes it cannot read there")
    void transformerRewritesTheFontWidths() {
        ComplexTextShaping.Transformer t = new ComplexTextShaping.Transformer();
        assertThat(t.transform(null, null, ComplexTextShaping.FONT_IMPL, null, null, ComplexTextShapingTest.fontImpl()))
                .isNotNull();
        assertThat(t.widthsApplied).isTrue();
        ComplexTextShaping.Transformer none = new ComplexTextShaping.Transformer();
        assertThat(none.transform(null, null, ComplexTextShaping.FONT_IMPL, null, null,
                ComplexTextShapingTest.emptyClass(ComplexTextShaping.FONT_IMPL))).isNull();
        assertThat(none.widthsApplied).isFalse();
        assertThat(new ComplexTextShaping.Transformer().transform(null, null, ComplexTextShaping.FONT_IMPL, null, null,
                new byte[]{1, 2, 3})).isNull();
    }

    @Test
    @DisplayName("the transformer rewrites WebKit's glyph-list build, and refuses bytes it cannot read there")
    void transformerRewritesTheGlyphListBuild() {
        ComplexTextShaping.Transformer t = new ComplexTextShaping.Transformer();
        assertThat(t.transform(null, null, ComplexTextShaping.TEXT_UTILITIES, null, null,
                ComplexTextShapingTest.textUtilities())).isNotNull();
        assertThat(t.placementApplied).isTrue();
        ComplexTextShaping.Transformer none = new ComplexTextShaping.Transformer();
        assertThat(none.transform(null, null, ComplexTextShaping.TEXT_UTILITIES, null, null,
                ComplexTextShapingTest.emptyClass(ComplexTextShaping.TEXT_UTILITIES))).isNull();
        assertThat(none.placementApplied).isFalse();
        assertThat(new ComplexTextShaping.Transformer().transform(null, null, ComplexTextShaping.TEXT_UTILITIES,
                null, null, new byte[]{1, 2, 3})).isNull();
    }

    @Test
    @DisplayName("the transformer rewrites the context's glyph draw, and refuses bytes it cannot read")
    void transformerRewritesTheContextAndRefusesGarbage() {
        ComplexTextShaping.Transformer t = new ComplexTextShaping.Transformer();
        byte[] out = t.transform(null, null, ComplexTextShaping.CONTEXT, null, null, ComplexTextShapingTest.context());
        assertThat(out).isNotNull();
        assertThat(t.applied).isTrue();
        ComplexTextShaping.Transformer none = new ComplexTextShaping.Transformer();
        assertThat(none.transform(null, null, ComplexTextShaping.CONTEXT, null, null,
                ComplexTextShapingTest.emptyClass(ComplexTextShaping.CONTEXT))).isNull();
        assertThat(none.applied).isFalse();
        assertThat(new ComplexTextShaping.Transformer().transform(null, null, ComplexTextShaping.CONTEXT, null, null,
                new byte[]{1, 2, 3})).isNull();
    }

    @Test
    @DisplayName("the agent's handoff gives the handle once, and a newer attach replaces an unclaimed one")
    void handoffIsOneShot() {
        ShapingAgent.claim();
        java.lang.instrument.Instrumentation first = (java.lang.instrument.Instrumentation) java.lang.reflect.Proxy
                .newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{java.lang.instrument.Instrumentation.class}, (p, m, a) -> null);
        java.lang.instrument.Instrumentation second = (java.lang.instrument.Instrumentation) java.lang.reflect.Proxy
                .newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{java.lang.instrument.Instrumentation.class}, (p, m, a) -> null);
        ShapingAgent.agentmain(null, first);
        ShapingAgent.agentmain(null, second);
        assertThat(ShapingAgent.claim()).isSameAs(second);
        assertThat(ShapingAgent.claim()).isNull();
    }

    @Test
    @DisplayName("the bridge finds every JavaFX member it reads, and refuses a call with nothing to shape")
    void bridgeWiresAgainstRealJavaFx() throws Exception {
        ComplexTextShaping.PrismBridge bridge = new ComplexTextShaping.PrismBridge(getClass().getClassLoader());
        assertThat(bridge.apply(new Object[]{null, new int[0], new float[0]})).isEqualTo(0f);
        assertThat(bridge.apply(new Object[]{"not a font", null, null})).isEqualTo(0f);
        assertThat((Double) bridge.width(new Object[]{null, 5})).isNaN();
        assertThat((Double) bridge.width(new Object[]{"not a font", 5})).isNaN();
    }

    @Test
    @DisplayName("a shaped run's offsets reach only the glyph list built from that paint's own glyph array, once")
    void placementAnswersOnlyTheSamePaint() throws Exception {
        ComplexTextShaping.PrismBridge bridge = new ComplexTextShaping.PrismBridge(getClass().getClassLoader());
        int[] glyphs = {1, 2, 3};
        float[] advances = {4f, 5f, 6f};
        assertThat(bridge.place(new Object[]{glyphs, advances})).isNull();          // nothing shaped yet
        bridge.remember(glyphs, new float[]{0f, -7f, 2f});
        assertThat(bridge.place(new Object[]{new int[]{1, 2, 3}, advances})).isNull(); // an equal array is not this paint's
        assertThat((float[]) bridge.place(new Object[]{glyphs, advances}))
                .containsExactly(0f, 0f, 4f, -7f, 9f, 2f, 15f, 0f);
        assertThat(bridge.place(new Object[]{glyphs, advances})).isNull();          // used up
        bridge.remember(glyphs, new float[]{1f});
        assertThat(bridge.place(new Object[]{glyphs, advances})).isNull();          // lengths disagree
    }
}
