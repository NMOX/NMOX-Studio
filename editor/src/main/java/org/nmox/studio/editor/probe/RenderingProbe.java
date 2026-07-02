package org.nmox.studio.editor.probe;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.openide.modules.OnStart;

/**
 * A headless-CI probe for the editor-coloring failure class: the IDE runs
 * FlatLaf Dark but the editor rendered light-profile colors (v1.10.1's
 * white-editor bug). CI packaged the app for months without ever
 * observing the editor was unreadable. This asserts the RUNTIME resolved
 * color state — not the source layer, which {@code DarkProfileLayerTest}
 * already guards — so a platform default or a future module that changes
 * the active profile is caught too.
 *
 * Both halves of the v1.10.1 bug show up in what {@link FontColorSettings}
 * resolves for a code mime under the <em>active</em> profile, so both
 * assertions read that one public API — no dependency on the profile-name
 * impl class, whose package a module might not export:
 * <ol>
 *   <li>the default editor background is dark — a light background means
 *       the active profile fell back to "NetBeans" (bug half 1);</li>
 *   <li>the JS keyword foreground is Phosphor's — anything else means the
 *       Phosphor palette isn't registered under the active profile
 *       (bug half 2).</li>
 * </ol>
 *
 * Inert unless {@code -Dnmox.rendering.probe=true}. Runs at
 * {@code @OnStart} (modules up, colorings resolvable without a shown
 * window — the resolved colors are model state, not painted pixels),
 * writes one {@code PASS}/{@code FAIL: ...} line to
 * {@code -Dnmox.rendering.probe.out} and to stderr, then the boot quits
 * via {@code netbeans.close}. A result file rather than a thrown
 * assertion keeps the probe out of the platform's startup error handling.
 */
@OnStart
public final class RenderingProbe implements Runnable {

    static final String ENABLED_PROP = "nmox.rendering.probe";
    static final String OUTPUT_PROP = "nmox.rendering.probe.out";

    /** Phosphor's JS keyword foreground — the palette-under-active-profile tell. */
    static final Color EXPECTED_JS_KEYWORD = new Color(0xff6ac1);
    static final String PROBED_MIME = "text/javascript";
    /** A background at or below this luminance reads as "dark". FlatLaf
     *  Dark's editor background is ~#2b2b2b (luminance ~43); the light
     *  "NetBeans" profile is white (255). 96 leaves generous margin on
     *  both sides so a shade tweak doesn't flip the verdict. */
    static final int DARK_MAX_LUMINANCE = 96;

    @Override
    public void run() {
        if (!Boolean.getBoolean(ENABLED_PROP)) {
            return;
        }
        String result;
        try {
            result = evaluate();
        } catch (RuntimeException ex) {
            result = "FAIL: probe threw " + ex;
        }
        emit(result);
    }

    /** Attempts to let settings finish resolving before concluding they
     *  are unavailable — @OnStart can fire just ahead of the settings
     *  storage on a slow runner. Bounded and only retried while the values
     *  are NULL (not-ready); a resolved-but-light value is a real failure
     *  and returns immediately, so the retry can never mask the bug. */
    static final int RESOLVE_ATTEMPTS = 20;
    static final long RESOLVE_WAIT_MS = 250;

    /** The assertions, as a PASS line or the first FAIL reason. */
    static String evaluate() {
        for (int attempt = 1; ; attempt++) {
            FontColorSettings fcs = MimeLookup.getLookup(MimePath.parse(PROBED_MIME))
                    .lookup(FontColorSettings.class);
            Color background = fcs == null ? null
                    : attr(fcs.getFontColors(FontColorNames.DEFAULT_COLORING),
                            StyleConstants.Background);
            Color keyword = fcs == null ? null
                    : attr(fcs.getTokenFontColors("keyword"), StyleConstants.Foreground);

            if ((background == null || keyword == null) && attempt < RESOLVE_ATTEMPTS) {
                sleep(RESOLVE_WAIT_MS);
                continue; // settings not resolved yet — keep waiting, don't judge
            }
            if (fcs == null) {
                return "FAIL: no FontColorSettings for " + PROBED_MIME
                        + " (editor settings never resolved)";
            }
            return classify(background, keyword);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The verdict for a resolved (background, keyword) pair — split out
     * from the platform lookup so the dark/light and palette decisions are
     * unit-testable without booting the IDE.
     */
    static String classify(Color background, Color keyword) {
        if (background == null) {
            return "FAIL: no resolved default background for " + PROBED_MIME;
        }
        int lum = luminance(background);
        if (lum > DARK_MAX_LUMINANCE) {
            return "FAIL: editor background " + hex(background) + " (luminance " + lum
                    + ") is light — the active profile is not the dark one";
        }
        if (keyword == null) {
            return "FAIL: no resolved keyword foreground for " + PROBED_MIME;
        }
        if (!sameRgb(keyword, EXPECTED_JS_KEYWORD)) {
            return "FAIL: " + PROBED_MIME + " keyword resolves to " + hex(keyword)
                    + ", expected Phosphor " + hex(EXPECTED_JS_KEYWORD)
                    + " (palette not under the active profile?)";
        }
        return "PASS: background=" + hex(background) + " (dark), "
                + PROBED_MIME + " keyword=" + hex(keyword);
    }

    private static Color attr(AttributeSet attrs, Object key) {
        if (attrs == null) {
            return null;
        }
        Object v = attrs.getAttribute(key);
        return v instanceof Color ? (Color) v : null;
    }

    /** Rec. 601 luma, 0 (black) .. 255 (white). */
    private static int luminance(Color c) {
        return (c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114) / 1000;
    }

    private static boolean sameRgb(Color a, Color b) {
        return (a.getRGB() & 0xFFFFFF) == (b.getRGB() & 0xFFFFFF);
    }

    private static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    private void emit(String result) {
        String out = System.getProperty(OUTPUT_PROP);
        if (out != null && !out.isEmpty()) {
            try {
                Files.writeString(Path.of(out), result + System.lineSeparator(),
                        StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new UncheckedIOException("rendering probe could not write " + out, ex);
            }
        }
        System.err.println("NMOX-RENDER-PROBE " + result);
    }
}
