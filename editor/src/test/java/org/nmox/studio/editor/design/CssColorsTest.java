package org.nmox.studio.editor.design;

import java.awt.Color;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The color-literal scanner's rules (v1.227.0): every authored form
 * parses to the right color with the right span, identifiers that
 * merely CONTAIN a color name never match, and comments are prose.
 */
class CssColorsTest {

    @Test
    @DisplayName("hex forms: #rgb, #rgba, #rrggbb, #rrggbbaa")
    void hexForms() {
        List<CssColors.ColorSpan> spans = CssColors.scan(
                "a{color:#369;border-color:#369f;background:#336699;outline-color:#336699cc}");
        assertThat(spans).hasSize(4);
        assertThat(spans.get(0).color()).isEqualTo(new Color(0x33, 0x66, 0x99));
        assertThat(spans.get(1).color().getAlpha()).isEqualTo(0xFF);
        assertThat(spans.get(2).color()).isEqualTo(new Color(0x336699));
        assertThat(spans.get(3).color().getAlpha()).isEqualTo(0xCC);
        // spans cover the whole literal including '#'
        assertThat(spans.get(0).start()).isEqualTo("a{color:".length());
        assertThat(spans.get(0).end()).isEqualTo("a{color:#369".length());
    }

    @Test
    @DisplayName("rgb()/rgba(): ints, percentages, clamping")
    void rgbForms() {
        List<CssColors.ColorSpan> spans = CssColors.scan(
                "a{c:rgb(240, 240, 235);d:rgba(255,0,0,.5);e:rgb(100%, 0%, 50%);f:rgb(300,-5,10)}");
        assertThat(spans).extracting(CssColors.ColorSpan::color).containsExactly(
                new Color(240, 240, 235),
                new Color(255, 0, 0),
                new Color(255, 0, 128),
                new Color(255, 0, 10));
    }

    @Test
    @DisplayName("hsl(): the CSS reference conversion")
    void hslForms() {
        List<CssColors.ColorSpan> spans = CssColors.scan(
                "a{c:hsl(0, 100%, 50%);d:hsl(120, 100%, 25%);e:hsl(210, 60%, 40%)}");
        assertThat(spans.get(0).color()).isEqualTo(new Color(255, 0, 0));
        assertThat(spans.get(1).color()).isEqualTo(new Color(0, 128, 0));
        // hsl(210,60%,40%) = #2966A3
        assertThat(spans.get(2).color()).isEqualTo(new Color(0x29, 0x66, 0xA3));
    }

    @Test
    @DisplayName("named colors match as whole words; identifiers containing them never do")
    void namedBoundaries() {
        assertThat(CssColors.scan("a{color:red}")).hasSize(1);
        assertThat(CssColors.scan("a{color:rebeccapurple}")).singleElement()
                .extracting(CssColors.ColorSpan::color).isEqualTo(new Color(0x663399));
        // preprocessor variables and custom properties are identifiers
        assertThat(CssColors.scan("$red-dark: 1; --red: 2; .red-box{}")).isEmpty();
        assertThat(CssColors.scan("a{color:$red}")).isEmpty();
        assertThat(CssColors.scan("a{color:var(--red)}")).isEmpty();
    }

    @Test
    @DisplayName("comments are prose: literals inside /* */ never match")
    void commentsSkipped() {
        assertThat(CssColors.scan("/* red #fff rgb(1,2,3) */ a{color:blue}"))
                .singleElement()
                .extracting(CssColors.ColorSpan::color).isEqualTo(new Color(0x0000FF));
        // an unclosed comment masks to end of text
        assertThat(CssColors.scan("a{color:blue} /* #fff")).hasSize(1);
    }

    @Test
    @DisplayName("readable text: white on dark colors, black on light ones")
    void readableText() {
        assertThat(CssColors.readableTextOn(new Color(0x336699))).isEqualTo(Color.WHITE);
        assertThat(CssColors.readableTextOn(new Color(0xFFFF00))).isEqualTo(Color.BLACK);
        assertThat(CssColors.readableTextOn(Color.BLACK)).isEqualTo(Color.WHITE);
        assertThat(CssColors.readableTextOn(Color.WHITE)).isEqualTo(Color.BLACK);
    }

    @Test
    @DisplayName("picker formatting preserves the authored form")
    void formatPreservesForm() {
        Color tomato = new Color(0xFF, 0x63, 0x47);
        assertThat(CssColors.format(tomato, "#336699")).isEqualTo("#ff6347");
        assertThat(CssColors.format(tomato, "rgb(1, 2, 3)")).isEqualTo("rgb(255, 99, 71)");
        assertThat(CssColors.format(tomato, "RGBA(1,2,3,.5)")).isEqualTo("rgb(255, 99, 71)");
        // a picked color almost never has a name: named becomes hex
        assertThat(CssColors.format(tomato, "rebeccapurple")).isEqualTo("#ff6347");
    }

    @Test
    @DisplayName("hsl round trip: rgb→hsl→rgb lands on the same color")
    void hslRoundTrip() {
        // hsl(210, 60%, 40%) parses to #2966A3; formatting it back as hsl
        // must re-parse to the same RGB
        Color c = new Color(0x29, 0x66, 0xA3);
        String hsl = CssColors.format(c, "hsl(0, 0%, 0%)");
        assertThat(hsl).startsWith("hsl(");
        List<CssColors.ColorSpan> reparsed = CssColors.scan("a{c:" + hsl + "}");
        assertThat(reparsed).singleElement()
                .extracting(CssColors.ColorSpan::color).isEqualTo(c);
        // achromatic: hue and saturation collapse to zero, lightness holds
        assertThat(CssColors.format(new Color(128, 128, 128), "hsl(1,2%,3%)"))
                .isEqualTo("hsl(0, 0%, 50%)");
    }

    private static void assertClose(Color actual, Color expected) {
        assertThat(Math.abs(actual.getRed() - expected.getRed())).isLessThanOrEqualTo(2);
        assertThat(Math.abs(actual.getGreen() - expected.getGreen())).isLessThanOrEqualTo(2);
        assertThat(Math.abs(actual.getBlue() - expected.getBlue())).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("oklch/oklab: the reference red, achromatic anchors, % lightness")
    void oklchAnchors() {
        // the widely published Oklch coordinates of sRGB red
        assertClose(CssColors.parseOklchArgs("0.628 0.2577 29.234"), new Color(0xFF0000));
        assertClose(CssColors.parseOklchArgs("62.8% 0.2577 29.234deg"), new Color(0xFF0000));
        assertClose(CssColors.parseOklabArgs("1 0 0"), Color.WHITE);
        assertClose(CssColors.parseOklabArgs("0 0 0"), Color.BLACK);
        List<CssColors.ColorSpan> spans = CssColors.scan("a{c:oklch(0.65 0.15 250)}");
        assertThat(spans).hasSize(1); // the junior's favourite finally swatches
    }

    @Test
    @DisplayName("lab/lch: D50 anchors and the polar identity")
    void labAnchors() {
        assertClose(CssColors.parseLabArgs("100 0 0"), Color.WHITE);
        assertClose(CssColors.parseLabArgs("0 0 0"), Color.BLACK);
        // lch with hue 0 and lab with +a on the same L,C agree
        assertClose(CssColors.parseLchArgs("54.3 106.8 40.9"),
                CssColors.parseLabArgs("54.3 80.7 69.9"));
    }

    @Test
    @DisplayName("hwb: pure hue at 0/0, wash-out gray, blackness")
    void hwbForms() {
        assertClose(CssColors.parseHwbArgs("120 0% 0%"), new Color(0x00FF00));
        assertClose(CssColors.parseHwbArgs("0 0% 100%"), Color.BLACK);
        // w+b >= 100%: achromatic, gray = w/(w+b)
        assertClose(CssColors.parseHwbArgs("0 100% 100%"), new Color(128, 128, 128));
    }

    @Test
    @DisplayName("round trips: format(picked, modern form) re-parses to the same color")
    void modernRoundTrips() {
        Color tomato = new Color(0xFF, 0x63, 0x47);
        for (String form : List.of("oklch(0 0 0)", "oklab(0 0 0)",
                "lab(0 0 0)", "lch(0 0 0)", "hwb(0 0% 0%)")) {
            String out = CssColors.format(tomato, form);
            List<CssColors.ColorSpan> reparsed = CssColors.scan("a{c:" + out + "}");
            assertThat(reparsed).as(form + " -> " + out).hasSize(1);
            assertClose(reparsed.get(0).color(), tomato);
        }
    }

    @Test
    @DisplayName("color-mix: the computed mix is the swatch, spanning the whole recipe")
    void colorMix() {
        String css = "a{c:color-mix(in srgb, red, white)}";
        List<CssColors.ColorSpan> spans = CssColors.scan(css);
        // outer recipe first (start asc, wider first), then the two literals
        assertThat(spans).hasSize(3);
        assertThat(spans.get(0).start()).isEqualTo(css.indexOf("color-mix"));
        assertThat(spans.get(0).end()).isEqualTo(css.indexOf("}"));
        assertClose(spans.get(0).color(), new Color(255, 128, 128));

        // weighted, nested function args, oklab space
        List<CssColors.ColorSpan> weighted = CssColors.scan(
                "a{c:color-mix(in srgb, rgb(255, 0, 0) 25%, white)}");
        assertClose(weighted.get(0).color(), new Color(255, 191, 191));
        assertThat(CssColors.scan("a{c:color-mix(in oklab, black, white)}"))
                .isNotEmpty(); // perceptual space mixes without throwing
        // an unreadable component: no recipe swatch, no guess
        assertThat(CssColors.scan("a{c:color-mix(in srgb, var(--x), white)}"))
                .extracting(CssColors.ColorSpan::start)
                .doesNotContain("a{c:".length());
    }

    @Test
    @DisplayName("garbage never throws and never matches")
    void hostileInput() {
        assertThat(CssColors.scan("")).isEmpty();
        assertThat(CssColors.scan("rgb(")).isEmpty();
        assertThat(CssColors.scan("a{c:rgb(x,y,z)}")).isEmpty();
        assertThat(CssColors.scan("a{c:hsl(1,2,3)}")).isEmpty(); // s/l need %
        assertThat(CssColors.scan("#33669")).isEmpty(); // 5 digits is no color
        assertThat(CssColors.scan("a{c:oklch(x y z)}")).isEmpty();
        assertThat(CssColors.scan("a{c:lab(}")).isEmpty();
        // broken recipes: the inner literals still swatch (they ARE colors),
        // but no span may claim the color-mix( itself
        assertThat(CssColors.scan("a{c:color-mix(in srgb, red")) // unbalanced
                .extracting(CssColors.ColorSpan::start).doesNotContain("a{c:".length());
        assertThat(CssColors.scan("a{c:color-mix(red, white)}")) // no 'in space'
                .extracting(CssColors.ColorSpan::start).doesNotContain("a{c:".length());
    }
}
