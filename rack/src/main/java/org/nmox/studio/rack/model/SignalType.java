package org.nmox.studio.rack.model;

import java.awt.Color;

/**
 * The kinds of signals that flow through patch cables. Cable colors are
 * keyed by type so a glance at the back of the rack tells you what is
 * patched where, just like CV vs audio cables in Reason.
 */
public enum SignalType {

    /** A momentary event: "run", "done", "succeeded", "failed". */
    TRIGGER(new Color[]{
        new Color(225, 70, 60), new Color(240, 130, 40), new Color(200, 50, 110)
    }),
    /** A stream of text payloads (process output lines, results). */
    DATA(new Color[]{
        new Color(235, 200, 50), new Color(110, 200, 70), new Color(170, 220, 60)
    }),
    /** A sustained on/off state (dev server running, watch active). */
    GATE(new Color[]{
        new Color(70, 130, 235), new Color(60, 190, 215), new Color(130, 100, 230)
    });

    private final Color[] cableColors;

    SignalType(Color[] cableColors) {
        this.cableColors = cableColors;
    }

    /** Cable color variant; rotate by an index so parallel runs stay legible. */
    public Color cableColor(int variant) {
        return cableColors[Math.floorMod(variant, cableColors.length)];
    }
}
