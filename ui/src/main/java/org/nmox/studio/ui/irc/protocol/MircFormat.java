package org.nmox.studio.ui.irc.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * mIRC formatting codes — the in-band control characters IRC text has
 * carried since the 90s: 0x02 bold, 0x1D italics, 0x1F underline,
 * 0x03 {@code NN[,NN]} color (1–2 digit foreground, optional
 * {@code ,background}), 0x0F reset. This class translates a raw
 * message body into styled {@link Span}s the transcript pane renders,
 * and offers {@link #stripToText} for anywhere plain text is wanted
 * (logs, tooltips, the tree's unread preview).
 *
 * <p>Two extra codes are consumed-and-dropped rather than styled:
 * 0x16 (reverse video) and 0x11 (monospace) — rare, and a dropped
 * toggle beats a rendered control glyph. A 0x03 with no digits after
 * it CLEARS the current colors, per the de-facto spec.
 *
 * <p>Pure text-in/spans-out, no Swing — the UI maps {@link Span#foreground}
 * palette indices (0–98, or −1 for "default") to actual
 * phosphor-friendly {@code java.awt.Color}s.
 */
public final class MircFormat {

    /** Bold toggle, 0x02. */
    public static final char BOLD = '\u0002';
    /** Color introducer, 0x03. */
    public static final char COLOR = '\u0003';
    /** Reset-all, 0x0F. */
    public static final char RESET = '\u000F';
    /** Reverse video (consumed, not styled), 0x16. */
    public static final char REVERSE = '\u0016';
    /** Italics toggle, 0x1D. */
    public static final char ITALIC = '\u001D';
    /** Monospace toggle (consumed, not styled), 0x11. */
    public static final char MONOSPACE = '\u0011';
    /** Underline toggle, 0x1F. */
    public static final char UNDERLINE = '\u001F';

    private MircFormat() {
    }

    /**
     * One run of identically-styled text. Color indices are the mIRC
     * palette numbers as sent ({@code 0}=white … {@code 15}=light grey,
     * extended 16–98 accepted), or {@code -1} for the terminal default.
     *
     * @param text       the run's characters, never empty
     * @param bold       bold on
     * @param italic     italics on
     * @param underline  underline on
     * @param foreground mIRC color index or −1
     * @param background mIRC color index or −1
     */
    public record Span(String text, boolean bold, boolean italic,
            boolean underline, int foreground, int background) {
    }

    /** Splits a raw body into styled spans; a plain body yields one plain span. */
    public static List<Span> parse(String text) {
        List<Span> spans = new ArrayList<>(4);
        StringBuilder run = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        int fg = -1;
        int bg = -1;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            switch (c) {
                case BOLD -> {
                    flush(spans, run, bold, italic, underline, fg, bg);
                    bold = !bold;
                    i++;
                }
                case ITALIC -> {
                    flush(spans, run, bold, italic, underline, fg, bg);
                    italic = !italic;
                    i++;
                }
                case UNDERLINE -> {
                    flush(spans, run, bold, italic, underline, fg, bg);
                    underline = !underline;
                    i++;
                }
                case RESET -> {
                    flush(spans, run, bold, italic, underline, fg, bg);
                    bold = false;
                    italic = false;
                    underline = false;
                    fg = -1;
                    bg = -1;
                    i++;
                }
                case REVERSE, MONOSPACE -> i++; // consumed, unstyled
                case COLOR -> {
                    flush(spans, run, bold, italic, underline, fg, bg);
                    i++;
                    int[] parsedFg = readDigits(text, i);
                    if (parsedFg == null) {
                        // bare ^C: clear colors
                        fg = -1;
                        bg = -1;
                    } else {
                        fg = parsedFg[0];
                        i = parsedFg[1];
                        if (i < text.length() && text.charAt(i) == ',') {
                            int[] parsedBg = readDigits(text, i + 1);
                            if (parsedBg != null) {
                                bg = parsedBg[0];
                                i = parsedBg[1];
                            }
                            // a comma with no digits stays literal text
                        }
                    }
                }
                default -> {
                    // unhandled C0 controls (BEL, BS, raw 0x01…) would
                    // render as garbage glyphs in the transcript — drop
                    // them; tab survives as ordinary whitespace
                    if (!Character.isISOControl(c) || c == '\t') {
                        run.append(c);
                    }
                    i++;
                }
            }
        }
        flush(spans, run, bold, italic, underline, fg, bg);
        if (spans.isEmpty()) {
            spans.add(new Span("", false, false, false, -1, -1));
        }
        return spans;
    }

    /** Reads 1–2 digits at {@code from}; {@code {value, nextIndex}} or null. */
    private static int[] readDigits(String text, int from) {
        int end = from;
        while (end < text.length() && end - from < 2
                && Character.isDigit(text.charAt(end))) {
            end++;
        }
        if (end == from) {
            return null;
        }
        return new int[] {Integer.parseInt(text.substring(from, end)), end};
    }

    private static void flush(List<Span> spans, StringBuilder run, boolean bold,
            boolean italic, boolean underline, int fg, int bg) {
        if (run.length() > 0) {
            spans.add(new Span(run.toString(), bold, italic, underline, fg, bg));
            run.setLength(0);
        }
    }

    /** Removes every formatting code, keeping only the visible text. */
    public static String stripToText(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (Span s : parse(text)) {
            sb.append(s.text());
        }
        return sb.toString();
    }
}
