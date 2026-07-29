package org.nmox.studio.ui.irc.protocol;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mIRC formatting: toggles ({@code 0x02} bold, {@code 0x1D} italics,
 * {@code 0x1F} underline), {@code 0x03NN[,NN]} colors, {@code 0x0F}
 * reset — parsed into styled spans, and stripped clean for logs. All
 * control characters in these vectors are unicode escapes on purpose:
 * raw bytes in source rot invisibly.
 */
class MircFormatTest {

    private static final String B = "\u0002";
    private static final String I = "\u001D";
    private static final String U = "\u001F";
    private static final String C = "\u0003";
    private static final String R = "\u000F";

    @Test
    @DisplayName("Plain text is one plain span")
    void plainText() {
        List<MircFormat.Span> spans = MircFormat.parse("hello");
        assertThat(spans).hasSize(1);
        MircFormat.Span s = spans.get(0);
        assertThat(s.text()).isEqualTo("hello");
        assertThat(s.bold()).isFalse();
        assertThat(s.italic()).isFalse();
        assertThat(s.underline()).isFalse();
        assertThat(s.foreground()).isEqualTo(-1);
        assertThat(s.background()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Bold toggles on and off around the middle run")
    void boldToggle() {
        List<MircFormat.Span> spans = MircFormat.parse("a" + B + "b" + B + "c");
        assertThat(spans).hasSize(3);
        assertThat(spans.get(0).bold()).isFalse();
        assertThat(spans.get(1).text()).isEqualTo("b");
        assertThat(spans.get(1).bold()).isTrue();
        assertThat(spans.get(2).bold()).isFalse();
    }

    @Test
    @DisplayName("Italics and underline style their runs")
    void italicAndUnderline() {
        List<MircFormat.Span> spans = MircFormat.parse(I + "it" + I + U + "un" + U);
        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).italic()).isTrue();
        assertThat(spans.get(0).underline()).isFalse();
        assertThat(spans.get(1).underline()).isTrue();
        assertThat(spans.get(1).italic()).isFalse();
    }

    @Test
    @DisplayName("A color code sets the foreground until cleared")
    void colorForeground() {
        List<MircFormat.Span> spans = MircFormat.parse(C + "4red" + C + "plain");
        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).text()).isEqualTo("red");
        assertThat(spans.get(0).foreground()).isEqualTo(4);
        assertThat(spans.get(1).text()).isEqualTo("plain");
        assertThat(spans.get(1).foreground()).isEqualTo(-1);
    }

    @Test
    @DisplayName("A two-digit color with background parses both halves")
    void colorWithBackground() {
        List<MircFormat.Span> spans = MircFormat.parse(C + "04,07x");
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).text()).isEqualTo("x");
        assertThat(spans.get(0).foreground()).isEqualTo(4);
        assertThat(spans.get(0).background()).isEqualTo(7);
    }

    @Test
    @DisplayName("A comma with no digits after a color stays literal text")
    void commaWithoutDigitsIsLiteral() {
        List<MircFormat.Span> spans = MircFormat.parse(C + "4,x");
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).text()).isEqualTo(",x");
        assertThat(spans.get(0).foreground()).isEqualTo(4);
        assertThat(spans.get(0).background()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Reset clears every style and color at once")
    void resetClearsAll() {
        List<MircFormat.Span> spans = MircFormat.parse(B + C + "9styled" + R + "clean");
        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).bold()).isTrue();
        assertThat(spans.get(0).foreground()).isEqualTo(9);
        MircFormat.Span clean = spans.get(1);
        assertThat(clean.bold()).isFalse();
        assertThat(clean.foreground()).isEqualTo(-1);
        assertThat(clean.text()).isEqualTo("clean");
    }

    @Test
    @DisplayName("A bare color code clears the colors but keeps other styles")
    void bareColorClears() {
        List<MircFormat.Span> spans = MircFormat.parse(B + C + "4a" + C + "b");
        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).foreground()).isEqualTo(4);
        assertThat(spans.get(1).foreground()).isEqualTo(-1);
        assertThat(spans.get(1).bold()).as("bold survives a color clear").isTrue();
    }

    @Test
    @DisplayName("Reverse and monospace codes are consumed, not rendered")
    void reverseAndMonospaceConsumed() {
        assertThat(MircFormat.stripToText("a\u0016b\u0011c")).isEqualTo("abc");
        List<MircFormat.Span> spans = MircFormat.parse("a\u0016\u0011b");
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).text()).isEqualTo("ab");
    }

    @Test
    @DisplayName("stripToText removes every code and keeps the visible text")
    void stripToText() {
        assertThat(MircFormat.stripToText(C + "12,04hi" + B + "there" + R + "!"))
                .isEqualTo("hithere!");
        assertThat(MircFormat.stripToText("plain")).isEqualTo("plain");
        assertThat(MircFormat.stripToText("")).isEmpty();
    }

    @Test
    @DisplayName("An all-codes string yields one honest empty span")
    void allCodesYieldsEmptySpan() {
        List<MircFormat.Span> spans = MircFormat.parse(B + R + C + "4");
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).text()).isEmpty();
    }
}
