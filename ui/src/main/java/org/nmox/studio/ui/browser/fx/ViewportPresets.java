package org.nmox.studio.ui.browser.fx;

import java.util.List;

/**
 * The Browser's responsive viewport presets (v1.228.0, the Senior Web
 * Designer pass): a handful of device sizes a designer actually
 * checks, applied by capping the WebView to the preset and centering
 * it on a neutral backdrop — no user-agent games, just the real page
 * at the real width, so CSS breakpoints fire exactly as they would in
 * a resized window.
 *
 * <p>WIDTH is the contract; height is best-effort (v1.234.0 review).
 * The preset caps the WebView with a max size but no min, so a pane
 * shorter than the device gives the page the pane's height — an
 * 844-tall iPhone preset in a 600px pane reports
 * {@code window.innerHeight} 600. Width — the axis breakpoints
 * actually query — is always exact. Forcing the full height would
 * need the viewport wrapped in a scroll pane; not worth the chrome
 * until a height-dependent media query matters to someone.
 */
public final class ViewportPresets {

    /** One preset; width/height ≤ 0 means "fill the window" (Full). */
    public record Preset(String label, int width, int height) {

        @Override
        public String toString() {
            return label;
        }

        /** True for the unconstrained default. */
        public boolean full() {
            return width <= 0 || height <= 0;
        }
    }

    /**
     * Full first (the default), then phones → tablet → laptop. Sizes
     * are the CSS-pixel viewports of the current reference devices,
     * the same ones browser devtools ship.
     */
    public static final List<Preset> ALL = List.of(
            new Preset("Full", -1, -1),
            new Preset("iPhone · 390×844", 390, 844),
            new Preset("Android · 412×915", 412, 915),
            new Preset("Tablet · 768×1024", 768, 1024),
            new Preset("Laptop · 1366×768", 1366, 768));

    private ViewportPresets() {
    }
}
