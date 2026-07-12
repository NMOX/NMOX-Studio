package org.nmox.studio.core.spi.device;

import java.awt.Color;
import java.util.List;

/**
 * Everything the Studio needs to know about a device kind before one is
 * mounted: identity, shelf placement, the how-to card, and the jacks.
 *
 * <p><b>The id is namespaced</b> — reverse-DNS style, at least one dot
 * ({@code com.example.uptime}). Un-dotted ids belong to the built-in
 * fleet forever; the catalog refuses a descriptor without a dot, and a
 * saved patch stores devices by this id, so treat it like a serialized
 * format: never change it once shipped. When a patch names an id no
 * installed extension answers, the rack mounts an inert MISSING
 * placeholder that preserves the device's slot, state, and cables
 * verbatim until the plugin returns.
 *
 * <p>{@code usage} is the How-to-use card and must carry at least two
 * lines (what it does, then a concrete patch recipe) totalling more
 * than sixty characters — the same shelf-guidance law every built-in
 * obeys. {@code units} is the faceplate height in rack units of 66px
 * (1–3; knobs and toggles need 2).
 *
 * @since 1.55
 */
public record DeviceDescriptor(String id, String title, String tagline, Color accent,
        DeviceCategory category, String usage, int units, List<PortSpec> ports) {

    public DeviceDescriptor {
        ports = ports == null ? List.of() : List.copyOf(ports);
    }
}
