package org.nmox.studio.rack.model;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.nmox.studio.rack.ui.controls.LcdDisplay;

/**
 * Stands in for a device whose type id no catalog entry answers —
 * typically a patch saved with a plugin device that is not installed
 * here, or a newer built-in opened in an older Studio. It preserves the
 * stranger's slot, saved state, and cables verbatim so the patch
 * round-trips losslessly: before this class, RackIO dropped the unknown
 * device while resolving cables by position, silently rewiring every
 * cable saved after it. It does nothing on purpose — no exec, no
 * resume: an honest placeholder, not an imitation.
 */
public final class MissingDevice extends RackDevice {

    private final Map<String, String> savedState = new LinkedHashMap<>();

    public MissingDevice(String typeId) {
        super(typeId, "MISSING",
                "Unknown device \"" + typeId + "\" — install its plugin, or remove it",
                new Color(120, 120, 120), 1);
        LcdDisplay screen = new LcdDisplay(340, 1);
        screen.setText("no device answers \"" + typeId + "\"");
        screen.getAccessibleContext().setAccessibleName("missing device " + typeId);
        place(screen, 44, 20);
    }

    /** The stranger's knob positions, kept verbatim for the round trip. */
    @Override
    public Map<String, String> getState() {
        return new LinkedHashMap<>(savedState);
    }

    @Override
    public void applyState(Map<String, String> state) {
        savedState.clear();
        savedState.putAll(state);
    }

    /**
     * Adopts a port the saved harness references, typed like its live
     * peer so {@link Port#canConnectTo} accepts the re-patch. The jack
     * is real; the device behind it is not.
     */
    Port adoptPort(String id, Port.Direction direction, SignalType type) {
        Port existing = getPort(id);
        if (existing != null) {
            return existing;
        }
        String label = id.toUpperCase(Locale.ROOT);
        return direction == Port.Direction.IN
                ? addInPort(id, label, type)
                : addOutPort(id, label, type);
    }
}
