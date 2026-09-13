package org.nmox.studio.ui.rtl;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.util.Set;

/**
 * The surfaces that keep the direction they were painted in.
 *
 * <p>Applying an orientation reaches every ordinary Swing component: a tree
 * indents the other way, a toolbar reverses, a form's labels change sides,
 * a scroll bar moves. It does NOT reach a component that paints itself,
 * because such a component computes its own x coordinates and the orientation
 * flag is simply a field it never reads. Eighteen classes in this product
 * paint themselves.
 *
 * <p>Silence about them would be the defect. So every one is classified, and
 * {@code PaintedSurfaceLedgerTest} derives the population from the shipping
 * source — the v2.147.0 lesson, keyed by the thing itself — so a new painted
 * surface fails the build until somebody decides about it.
 *
 * <p>Two honest categories, and the second is a debt, not a decision:
 *
 * <ul>
 *   <li><b>Geometry, not typography.</b> A rack unit's jacks sit where the
 *       hardware puts them; a wire between two cloud nodes reads "serves"
 *       (v1.271.0) and is not a sentence; a timeline runs from earlier to
 *       later; source code is left-to-right in every language. Mirroring any
 *       of these would not translate them, it would break them.
 *   <li><b>Owed.</b> Surfaces that are mostly TEXT laid out by hand — the
 *       Welcome's columns, the shelf's cards, the Task Board overview. These
 *       should mirror and do not yet. Named here so the gap is a line in a
 *       file rather than something a reader discovers.
 * </ul>
 */
final class PaintedSurfaces {

    /** Geometry, not typography: mirroring would break, not translate. */
    static final Set<String> GEOMETRY = Set.of(
            "RackDevice",        // a faceplate: the silkscreen is English by decision
            "RackPanel",         // rack units stack; jacks sit where the hardware puts them
            "Knob", "LcdDisplay", "Led", "RackButton", "ToggleSwitch", "VuMeter",
            "FlowCanvas",        // a wire reads "serves" — a direction with meaning
            "BlockCanvas",       // interlocking pieces; the nesting is the syntax
            "InfraPalette",      // node kinds beside the canvas they drop onto
            "MinimapSideBar",    // a silhouette of code, and code runs left to right
            "StickyScrollSideBar", // the source's own lines, in the source's direction
            "TimelineStrip",     // time runs from earlier to later
            "KeystrokeOverlay"); // a chord is typed in one order

    /** Text laid out by hand. These should mirror; they do not yet. */
    static final Set<String> OWED = Set.of(
            "MainWindow",        // the Welcome's four columns
            "PalettePanel",      // the shelf's cards
            "OverviewPanel");    // the Task Board dashboard

    private PaintedSurfaces() {
    }

    /**
     * Put every painted surface in the tree back the way it was painted.
     *
     * <p>Called after an orientation sweep: the sweep is a blunt instrument
     * that walks the whole tree, and these components must not be caught by
     * it. A component in {@link #OWED} is reset for the same reason — half a
     * mirror is worse than none, and the debt is written down rather than
     * shipped as a broken layout.
     */
    static void keepAuthoredDirection(Component root) {
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                keepAuthoredDirection(child);
            }
        }
        String name = root.getClass().getSimpleName();
        if (GEOMETRY.contains(name) || OWED.contains(name)) {
            root.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        }
    }
}
