package org.nmox.studio.core.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.swing.text.StyleContext;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether U+202F has any width, in the font this platform paints chrome with
 * (ledger 107, the pixel half).
 *
 * <p><b>The law this exists to obey.</b> {@code canDisplay} answers whether a
 * glyph EXISTS, not whether it has width or ink. A font can carry the narrow
 * no-break space at <i>zero advance</i>: every existence check passes, nothing
 * is drawn, no width is reserved, and French's {@code 1 234 567} paints as
 * {@code 1234567} — pixel-identical to the same digits with no separator at
 * all. That is measured, not hypothetical: on macOS the logical families
 * {@code Dialog} and {@code SansSerif} advance U+202F by <b>0.00px</b>. So
 * this test asks three things per font — the glyph, the advance in px, and the
 * count of dark pixels painted at 64pt — beside a control character
 * ({@code U+E000}, guaranteed absent) which must paint a box, so the
 * instrument proves itself on every run.
 *
 * <p><b>What is asserted and what is only recorded.</b> The product-relevant
 * font is the one FlatLaf builds the chrome from: the status line, every
 * dialog and every platform label. That one is ASSERTED — positive advance,
 * no ink. The logical families are RECORDED in the failure message instead,
 * because macOS's 0.00px is a known, accepted, latent state (ledger 107 chose
 * not to fix it: no self-painted surface formats a number today, and the fix
 * when one does is that surface asking for the chrome font, not a rewrite of
 * the separator). A test that failed on it would fail on macOS today for a
 * case the ledger deliberately decided about.
 *
 * <p><b>Why a test and not a workflow job.</b> The ledger expected this to
 * need a job on {@code windows-installer-check}. It does not:
 * {@code build-and-test.yml} has run the full {@code mvn verify} on a
 * {@code windows-latest} matrix leg as a blocking gate since v1.42.0, so a
 * plain unit test turns a one-off measurement into a standing law measured on
 * every pull request, on all three platforms.
 *
 * <p><b>The three answers, per platform</b> — the report below prints them
 * whether it passes, fails or aborts:
 * <ol>
 *   <li>what the JDK's French grouping separator is ({@link
 *       GroupSeparatorLedgerTest} owns that one; the sample here is the
 *       end-to-end echo of it);</li>
 *   <li>the chrome font's own U+202F advance and ink;</li>
 *   <li>whether {@code StyleContext.getFont} wraps it into a composite at all
 *       — the bare and wrapped measurements are printed side by side, and any
 *       difference between them IS that answer. On Linux the wrap fired and
 *       turned boxing fonts into correct composites; Windows'
 *       {@code sun.jnu.encoding} follows the OS ANSI codepage, so if the two
 *       rows agree there, the font's own metrics govern.</li>
 * </ol>
 */
class NarrowNoBreakSpaceInkTest {

    private static final char NNBSP = ' ';

    /** A private-use code point no real font carries: the instrument's own control. */
    private static final char CONTROL = '';

    /** The ledger's measurement size — big enough that a hairline advance is visible. */
    private static final int SIZE = 64;

    /** Fractional metrics on: a 0.9px advance must not round up into a pass. */
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private static final List<String> LOGICAL =
            List.of(Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED);

    /** One font's three answers. */
    private record Measured(String family, boolean canDisplay,
            double advance, double spaceAdvance, int ink, int controlInk) {

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "family=%-20s canDisplay=%-5s advance=%6.2fpx (space %6.2fpx) "
                    + "ink=%d controlInk=%d",
                    family, canDisplay, advance, spaceAdvance, ink, controlInk);
        }
    }

    @Test
    @DisplayName("the chrome font gives U+202F a real width and no ink")
    void theChromeFontReservesWidthAndPaintsNothing() {
        StringBuilder report = new StringBuilder(environment());

        // Recorded, never asserted: see the class javadoc. macOS's 0.00px here
        // is a decided, latent state — asserting on it would fail the build for
        // something ledger 107 chose.
        report.append("\nlogical families (RECORDED, not asserted):\n");
        for (String name : LOGICAL) {
            report.append("  ").append(String.format(Locale.ROOT, "%-12s", name))
                    .append(" bare ").append(measure(new Font(name, Font.PLAIN, SIZE)))
                    .append("\n").append(" ".repeat(15))
                    .append(" wrap ").append(measure(wrapped(name))).append("\n");
        }

        ChromeFont chrome = chromeFont();
        report.append("\nchrome font: ").append(chrome.how).append("\n");
        if (chrome.name == null) {
            abort(report, "no chrome font could be obtained on this platform, so there is "
                    + "nothing honest to assert here — the recorded numbers stand");
        }

        Font bare = new Font(chrome.name, Font.PLAIN, SIZE);
        Font wrap = wrapped(chrome.name);
        Measured bareM = measure(bare);
        Measured wrapM = measure(wrap);
        report.append("  bare new Font(..)     ").append(bareM).append("\n");
        report.append("  StyleContext.getFont  ").append(wrapM).append("\n");
        report.append("  Q3 — composite wrap changes the metrics: ")
                .append(bareM.advance != wrapM.advance || bareM.ink != wrapM.ink
                        || bareM.canDisplay != wrapM.canDisplay
                        ? "YES, the wrap is load-bearing here"
                        : "NO, the font's own metrics govern")
                .append("\n");

        if (!bareM.family.equalsIgnoreCase(chrome.name)) {
            // An unresolvable name silently becomes Dialog — measured: a bare
            // new Font("NoSuchFontXyz") reports family=Dialog. So a name that
            // did not resolve would have us measuring the logical fallback and
            // calling it the chrome font.
            abort(report, "the chrome font name '" + chrome.name + "' did not resolve (it "
                    + "fell back to '" + bareM.family + "'), so this platform's chrome "
                    + "cannot be measured honestly here");
        }

        // Printed, not only asserted: the point of this test is a measurement
        // on platforms the maintainer does not have in front of him. A green
        // Windows leg whose numbers nobody can read answers nothing.
        System.out.println(report);

        String why = "\n\nledger 107: this is the font the status line, every dialog and "
                + "every platform label are painted in. A zero advance here means French's "
                + "group separator DISAPPEARS on screen rather than boxing — and canDisplay "
                + "would still say true.\n" + report;

        assertThat(wrapM.controlInk)
                .as("the instrument's own control: U+E000 must paint a box, or the ink "
                        + "count is measuring nothing and every other number here is a "
                        + "false pass" + why)
                .isPositive();
        assertThat(wrapM.advance)
                .as("U+202F must reserve width in the chrome font" + why)
                .isGreaterThan(0.0);
        assertThat(wrapM.ink)
                .as("U+202F must paint no ink in the chrome font — a box in place of a "
                        + "space is the other failure the conventions file warned about" + why)
                .isZero();
    }

    /** Say what was measured before standing down — a silent skip answers nothing. */
    private static void abort(CharSequence report, String reason) {
        String text = reason + ":\n" + report;
        System.out.println(text);
        Assumptions.abort(text);
    }

    /** Where the chrome font came from, honestly, or {@code null} with the reason. */
    private record ChromeFont(String name, String how) {
    }

    private static ChromeFont chromeFont() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // FlatLaf's Windows font policy reads exactly these two (decompiled).
            for (String prop : List.of("win.messagebox.font", "win.defaultGUI.font")) {
                Object v = desktopProperty(prop);
                if (v instanceof Font f) {
                    return new ChromeFont(f.getFamily(Locale.ROOT),
                            "desktop property " + prop + " = " + f.getFamily(Locale.ROOT)
                            + " " + f.getSize());
                }
            }
            return new ChromeFont("Segoe UI",
                    "NAMED, not read: neither win.messagebox.font nor win.defaultGUI.font "
                    + "answered on this (headless) runner, so this is Segoe UI by name — "
                    + "the Windows 10/11 shell font FlatLaf would land on");
        }
        if (os.contains("mac")) {
            // Measured on the shipped bundled runtime with FlatDarkLaf installed:
            // every UI key resolves to Helvetica Neue (ledger 107).
            for (String name : List.of("Helvetica Neue", ".AppleSystemUIFont")) {
                if (installed(name)) {
                    return new ChromeFont(name, "installed family " + name
                            + " — what every FlatLaf UI font key resolves to on macOS");
                }
            }
            return new ChromeFont(null, "neither Helvetica Neue nor .AppleSystemUIFont "
                    + "is installed on this machine");
        }
        // Linux: FlatLaf.LinuxFontPolicy takes the session's own font name.
        Object gtk = desktopProperty("gnome.Gtk/FontName");
        if (gtk instanceof String s && !s.isBlank()) {
            return new ChromeFont(stripPointSize(s),
                    "desktop property gnome.Gtk/FontName = " + s);
        }
        return new ChromeFont(null, "no session font: gnome.Gtk/FontName is absent, which "
                + "is what a headless runner with no desktop session reports. Linux was "
                + "measured clean under ledger 107 in a real fr_FR.UTF-8 container across "
                + "both architectures and every realistic font set");
    }

    /** {@code "Cantarell 11"} is a family and a point size; we want the family. */
    private static String stripPointSize(String gtkFontName) {
        String s = gtkFontName.strip();
        int space = s.lastIndexOf(' ');
        if (space > 0 && s.substring(space + 1).chars().allMatch(Character::isDigit)) {
            return s.substring(0, space).strip();
        }
        return s;
    }

    private static boolean installed(String family) {
        return Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames(Locale.ROOT)).contains(family);
    }

    private static Object desktopProperty(String name) {
        try {
            return Toolkit.getDefaultToolkit().getDesktopProperty(name);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** The font FlatLaf would build: {@code StyleContext} wraps into a composite. */
    private static Font wrapped(String name) {
        return StyleContext.getDefaultStyleContext().getFont(name, Font.PLAIN, SIZE);
    }

    private static Measured measure(Font f) {
        return new Measured(
                f.getFamily(Locale.ROOT),
                f.canDisplay(NNBSP),
                f.getStringBounds(String.valueOf(NNBSP), FRC).getWidth(),
                f.getStringBounds(" ", FRC).getWidth(),
                ink(f, NNBSP),
                ink(f, CONTROL));
    }

    /** Dark pixels painted by one character at {@link #SIZE}pt — ask the pixels. */
    private static int ink(Font f, char c) {
        int w = SIZE * 4;
        int h = SIZE * 3;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(f);
        g.drawString(String.valueOf(c), SIZE / 4, h - SIZE / 2);
        g.dispose();
        int dark = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int lum = ((rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF)) / 3;
                if (lum < 128) {
                    dark++;
                }
            }
        }
        return dark;
    }

    private static String environment() {
        return String.format(Locale.ROOT,
                "ledger 107 — U+202F measured at %dpt%n"
                + "  java %s (%s) on %s %s, headless=%s, sun.jnu.encoding=%s%n"
                + "  French groups as: %s",
                SIZE,
                System.getProperty("java.version"), System.getProperty("java.vendor"),
                System.getProperty("os.name"), System.getProperty("os.arch"),
                GraphicsEnvironment.isHeadless(), System.getProperty("sun.jnu.encoding"),
                NumberFormat.getIntegerInstance(Locale.FRANCE).format(1234567)
                        .replace(NNBSP, '␣'));
    }
}
