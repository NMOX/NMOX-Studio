package org.nmox.studio.ui.browser.devtools;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keyframe core (v2.12.0): block emission is pinned byte-for-byte,
 * a same-named block is REPLACED at its LAST occurrence (the block the
 * cascade uses — editing any other changes dead CSS), refusals carry
 * their reasons, the parse round-trips what emit writes, and every
 * DHTML preset is a fixture fed through the real emit + parse + apply.
 */
class KeyframesTest {

    private static Keyframes.Spec two() {
        return new Keyframes.Spec("slide", 1500, "ease-in-out", 0, List.of(
                new Keyframes.Frame(0, Map.of("transform", "translateX(0)")),
                new Keyframes.Frame(100, Map.of("transform", "translateX(50px)"))));
    }

    @Test
    @DisplayName("the block and the animation shorthand are pinned")
    void emissionPinned() {
        assertThat(two().block()).isEqualTo(
                "@keyframes slide {\n"
                + "  0% { transform: translateX(0); }\n"
                + "  100% { transform: translateX(50px); }\n"
                + "}");
        assertThat(two().animationValue()).isEqualTo("slide 1.5s ease-in-out infinite");
        Keyframes.Spec once = new Keyframes.Spec("slide", 2000, "linear", 3, two().frames());
        assertThat(once.animationValue()).isEqualTo("slide 2s linear 3");
    }

    @Test
    @DisplayName("applyBlock appends to a fresh sheet and replaces the LAST same-named block")
    void replaceLastBlock() {
        Keyframes.Result fresh = Keyframes.applyBlock("h1 { color: red; }\n", two());
        assertThat(fresh.ok()).isTrue();
        assertThat(fresh.css()).contains("@keyframes slide");

        // two same-named blocks: the FIRST must survive untouched, the
        // LAST must be replaced — the cascade reads the last one
        String twoBlocks = "@keyframes slide { 0% { opacity: 0; } }\n"
                + "p { color: blue; }\n"
                + "@keyframes slide { 0% { opacity: 1; } }\n";
        Keyframes.Result r = Keyframes.applyBlock(twoBlocks, two());
        assertThat(r.ok()).isTrue();
        assertThat(r.css()).contains("opacity: 0");
        assertThat(r.css()).doesNotContain("opacity: 1");
        assertThat(r.css()).contains("translateX(50px)");
    }

    @Test
    @DisplayName("refusals speak: bad name, breakout value, unsorted percents, empty frame")
    void refusals() {
        Keyframes.Spec badName = new Keyframes.Spec("2fast", 1000, "linear", 1,
                two().frames());
        assertThat(Keyframes.problem(badName)).contains("identifier");
        Keyframes.Spec breakout = new Keyframes.Spec("ok", 1000, "linear", 1, List.of(
                new Keyframes.Frame(0, Map.of("color", "red } h1 { x: y"))));
        assertThat(Keyframes.problem(breakout)).contains("must not");
        Keyframes.Spec unsorted = new Keyframes.Spec("ok", 1000, "linear", 1, List.of(
                new Keyframes.Frame(50, Map.of("opacity", "1")),
                new Keyframes.Frame(10, Map.of("opacity", "0"))));
        assertThat(Keyframes.problem(unsorted)).contains("increasing");
        assertThat(Keyframes.applyBlock("", badName).ok()).isFalse();
    }

    @Test
    @DisplayName("parse round-trips emit; from/to blocks refuse rather than guess")
    void parseRoundTrip() {
        String css = Keyframes.applyBlock("/* @keyframes slide in a comment { } */\n", two()).css();
        Keyframes.Spec back = Keyframes.parse(css, "slide", 1500, "ease-in-out", 0);
        assertThat(back).isNotNull();
        assertThat(back.block()).isEqualTo(two().block());
        assertThat(Keyframes.parse(
                "@keyframes other { from { opacity: 0; } to { opacity: 1; } }",
                "other", 1000, "linear", 1)).isNull();
        assertThat(Keyframes.parse(css, "ghost", 1000, "linear", 1)).isNull();
    }

    @Test
    @DisplayName("every DHTML preset is a fixture: validates, applies, and round-trips")
    void presetsAreFixtures() {
        assertThat(Keyframes.presets()).isNotEmpty();
        for (Keyframes.Spec preset : Keyframes.presets()) {
            assertThat(Keyframes.problem(preset)).as(preset.name()).isNull();
            Keyframes.Result applied = Keyframes.applyBlock("body { margin: 0; }\n", preset);
            assertThat(applied.ok()).as(preset.name()).isTrue();
            Keyframes.Spec back = Keyframes.parse(applied.css(), preset.name(),
                    preset.durationMs(), preset.easing(), preset.iterations());
            assertThat(back).as(preset.name()).isNotNull();
            assertThat(back.block()).as(preset.name()).isEqualTo(preset.block());
        }
    }
}
