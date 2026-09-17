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
        // a table that also maps Latin letters: a Latin-only paint has nothing to shape, a combining mark does
        int[][] latin = ComplexTextShaping.PrismBridge.glyphTable(cp -> switch (cp) {
            case 'e' -> 500;
            case 0x0301 -> 600;
            case ' ' -> 3;
            default -> 0;
        });
        assertThat(ComplexTextShaping.PrismBridge.anyShapeable(new int[]{500, 3, 500}, latin)).isFalse();
        assertThat(ComplexTextShaping.PrismBridge.anyShapeable(new int[]{500, 600}, latin)).isTrue();
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
        assumeJavaFxLoads();
        ComplexTextShaping.PrismBridge bridge = new ComplexTextShaping.PrismBridge(getClass().getClassLoader());
        assertThat(bridge.apply(new Object[]{null, new int[0], new float[0]})).isEqualTo(0f);
        assertThat(bridge.apply(new Object[]{"not a font", null, null})).isEqualTo(0f);
        assertThat((Double) bridge.width(new Object[]{null, 5})).isNaN();
        assertThat((Double) bridge.width(new Object[]{"not a font", 5})).isNaN();
    }

    @Test
    @DisplayName("a laid-out run reaches only the glyph list built from that paint's own glyph array, once")
    void placementAnswersOnlyTheSamePaint() throws Exception {
        assumeJavaFxLoads();
        ComplexTextShaping.PrismBridge bridge = new ComplexTextShaping.PrismBridge(getClass().getClassLoader());
        int[] glyphs = {1, 2, 3};
        float[] advances = {4f, 5f, 6f};
        assertThat(bridge.place(new Object[]{glyphs, advances})).isNull();          // nothing shaped yet
        bridge.remember(glyphs, new ComplexScripts.Laid(new int[]{7, 8, 9, 10}, new float[]{0f, 4f, 9f, 15f},
                new float[]{0f, -7f, 2f, 0f}, 16f, 0f, true));
        assertThat(bridge.place(new Object[]{new int[]{1, 2, 3}, advances})).isNull(); // an equal array is not this paint's
        Object[] placed = (Object[]) bridge.place(new Object[]{glyphs, advances});
        assertThat((int[]) placed[0]).containsExactly(7, 8, 9, 10);
        assertThat((float[]) placed[1]).containsExactly(0f, 0f, 4f, -7f, 9f, 2f, 15f, 0f, 16f, 0f);
        assertThat(bridge.place(new Object[]{glyphs, advances})).isNull();          // used up
    }

    @Test
    @DisplayName("a shaped run is shaped once per font and text, a refusal is remembered, and the oldest run leaves first")
    void shapeCacheShapesOnceAndForgetsTheOldest() {
        ComplexTextShaping.ShapeCache cache = new ComplexTextShaping.ShapeCache(2);
        java.util.List<String> shapedTexts = new java.util.ArrayList<>();
        java.util.function.Function<String, ComplexScripts.Shaped> shaper = text -> {
            shapedTexts.add(text);
            return text.equals("refused") ? null : new ComplexScripts.Shaped(new int[]{text.length()}, new float[]{1f});
        };
        ComplexScripts.Shaped first = cache.get("بت", shaper);
        assertThat(cache.get("بت", shaper)).isSameAs(first);
        assertThat(cache.get("refused", shaper)).isNull();
        assertThat(cache.get("refused", shaper)).isNull();
        assertThat(shapedTexts).containsExactly("بت", "refused");
        cache.get("بت", shaper);          // used again: now the most recent
        cache.get("كم", shaper);          // a third run: "refused" is the oldest and leaves
        assertThat(cache.entries()).isEqualTo(2);
        cache.get("refused", shaper);
        assertThat(shapedTexts).containsExactly("بت", "refused", "كم", "refused");
    }

    @Test
    @DisplayName("the attach helper runs this JVM's own java on the jar just written, naming this process")
    void attachHelperCommandIsFixed() {
        java.nio.file.Path home = java.nio.file.Path.of("/opt/jre");
        java.nio.file.Path jar = java.nio.file.Path.of("/tmp/nmox-shaping-agent1.jar");
        assertThat(ComplexTextShaping.helperCommand(home, false, jar, 4242)).containsExactly(
                "/opt/jre/bin/java", "-cp", "/tmp/nmox-shaping-agent1.jar",
                "org.nmox.studio.ui.browser.fx.ShapingAttach", "4242", "/tmp/nmox-shaping-agent1.jar");
        assertThat(ComplexTextShaping.helperCommand(home, true, jar, 7).get(0)).endsWith("java.exe");
    }

    @Test
    @DisplayName("the attach helper refuses a call without exactly a pid and a jar, and says why")
    void attachHelperRefusesBadArguments() {
        assertThat(ShapingAttach.attach(null)).isFalse();
        assertThat(ShapingAttach.attach(new String[]{"1"})).isFalse();
    }

    @Test
    @DisplayName("the agent jar carries the agent and the attach helper under the manifest the attach API reads")
    void agentJarCarriesAgentAndHelper() throws Exception {
        java.nio.file.Path jar = ComplexTextShaping.writeAgentJar();
        assertThat(jar).isNotNull();
        try (java.util.jar.JarFile file = new java.util.jar.JarFile(jar.toFile())) {
            java.util.jar.Attributes main = file.getManifest().getMainAttributes();
            assertThat(main.getValue("Agent-Class")).isEqualTo(ShapingAgent.class.getName());
            assertThat(main.getValue("Can-Redefine-Classes")).isEqualTo("true");
            assertThat(main.getValue("Can-Retransform-Classes")).isEqualTo("true");
            assertThat(file.getEntry("org/nmox/studio/ui/browser/fx/ShapingAgent.class")).isNotNull();
            assertThat(file.getEntry("org/nmox/studio/ui/browser/fx/ShapingAttach.class")).isNotNull();
        } finally {
            java.nio.file.Files.deleteIfExists(jar);
        }
    }

    @Test
    @DisplayName("a helper that cannot attach reports failure, run for real with this JVM's own java, in-process and out")
    void attachHelperReportsARefusedAttach() throws Exception {
        java.nio.file.Path jar = ComplexTextShaping.writeAgentJar();
        try {
            String nobody = Long.toString(Long.MAX_VALUE); // no JVM has this pid
            assertThat(ShapingAttach.attach(new String[]{nobody, jar.toString()})).isFalse();
            assertThat(ComplexTextShaping.attachFromHelper(jar, Long.MAX_VALUE)).isFalse();
        } finally {
            java.nio.file.Files.deleteIfExists(jar);
        }
    }

    @Test
    @DisplayName("an unmapped glyph from a fallback slot asks for one rebuild per slot; mapped glyphs and the primary font never do")
    void newFallbackSlotsRebuildOnce() {
        int[][] table = ComplexTextShaping.PrismBridge.glyphTable(cp -> cp == 0x0628 ? 800 : cp == ' ' ? 3 : 0);
        java.util.Set<Integer> tried = new java.util.HashSet<>();
        int syriac = (0x27 << 24) | 9;
        assertThat(ComplexTextShaping.PrismBridge.newSlots(new int[]{800, 3}, table, tried)).isFalse(); // all mapped
        assertThat(ComplexTextShaping.PrismBridge.newSlots(new int[]{55}, table, tried)).isFalse();     // primary slot
        assertThat(ComplexTextShaping.PrismBridge.newSlots(new int[]{3, syriac}, table, tried)).isTrue();
        assertThat(ComplexTextShaping.PrismBridge.newSlots(new int[]{syriac, syriac + 1}, table, tried)).isFalse(); // tried
        assertThat(tried).containsExactly(0x27);
    }

    /**
     * The provided JavaFX jars are compiled for Java 24 and up; CI runs these on
     * JDK 25. An older local JDK cannot load them at all, which says nothing about
     * the bridge, so the two tests that touch real JavaFX classes skip there and
     * say why. Any other failure to load still fails.
     */
    private static void assumeJavaFxLoads() {
        try {
            Class.forName("com.sun.webkit.graphics.WCFont", false, PrismBridgeTest.class.getClassLoader());
        } catch (UnsupportedClassVersionError tooNew) {
            org.junit.jupiter.api.Assumptions.abort("JavaFX needs a newer JDK than " + Runtime.version() + ": " + tooNew.getMessage());
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("javafx-web is a provided dependency of this module", missing);
        }
    }
}
