package org.nmox.studio.rack.devices;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackShare;

/**
 * Which state keys of a device type START something when a patch restores
 * them — the resolver {@link RackShare} uses so a rack from another machine
 * arrives at rest (v2.179.0).
 *
 * <p>The answer is DERIVED, never listed: a built-in type is instantiated once
 * through its catalog factory and asked for {@link RackDevice#selfStartingKeys()}
 * — the keys its constructor registered through {@code paramSelfStarting}, right
 * beside the switch. v2.176.0 kept the names in a set inside {@code RackShare}
 * ({@code armed}, {@code running}) and TAIL's {@code follow}, which starts
 * polling a path the SENDER chose, was not in it.
 *
 * <p><b>Everything that is not a built-in answers {@link RackShare#EVERY_SWITCH}</b>
 * — every switch-shaped value arrives off. That is a decision, and it covers
 * two cases for two reasons:
 * <ul>
 * <li><b>An installed extension</b> (a Device SPI plugin, or a JSON device
 * file). {@code ExtensionDevice.Face.toggle} restores a toggle with
 * {@code setOn}, which runs the plugin's {@code ToggleHandle.onChange}
 * Runnable: arbitrary plugin code holding {@code DeviceServices}, so it can
 * reach {@code services.exec} or start its own timer. The host cannot tell a
 * flag from a watcher, so every extension toggle is self-starting. The type is
 * deliberately NOT instantiated to read its keys: that would run the plugin's
 * {@code build()} while the receiver is still reading the manifest, before
 * they agreed to mount anything. (JSON device files declare no toggles at all
 * today — knobs and buttons only — so for them this changes nothing.)</li>
 * <li><b>A type this install does not have.</b> It mounts as a MISSING
 * placeholder that keeps its state verbatim, the next Save Patch writes that
 * state beside the project, and the real device may be installed later and
 * restore it. Nobody here can say which of its keys are switches, so every
 * {@code true} is set off now rather than armed for later.</li>
 * </ul>
 * The cost of the conservative answer is small and visible: a text field of a
 * plugin device holding exactly {@code true} arrives as {@code false}, and the
 * manifest counts the device among those arriving at rest.
 */
public final class SelfStarting {

    private static final Logger LOG = Logger.getLogger(SelfStarting.class.getName());

    /** Built-in type id → its declared keys. Built-ins never change at runtime, so the answer is kept. */
    private static final Map<String, Set<String>> BUILT_IN = new ConcurrentHashMap<>();

    private SelfStarting() {
    }

    /** The keys a shared rack must set off for {@code typeId}; never null. */
    public static Set<String> keysFor(String typeId) {
        if (typeId == null) {
            return RackShare.EVERY_SWITCH;
        }
        Set<String> kept = BUILT_IN.get(typeId);
        if (kept != null) {
            return kept;
        }
        DeviceCatalog.Entry entry = DeviceCatalog.byId(typeId).orElse(null);
        if (entry == null || !entry.builtIn()) {
            return RackShare.EVERY_SWITCH;
        }
        RackDevice probe;
        try {
            probe = entry.create();
        } catch (RuntimeException ex) {
            // a built-in that cannot be constructed here cannot be mounted either;
            // answer conservatively and do not remember the failure
            LOG.log(Level.WARNING, "could not read the self-starting keys of " + typeId, ex);
            return RackShare.EVERY_SWITCH;
        }
        Set<String> keys = Set.copyOf(probe.selfStartingKeys());
        try {
            probe.dispose(); // never attached: nothing runs, this only releases what the constructor took
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "probe dispose of " + typeId, ex);
        }
        BUILT_IN.put(typeId, keys);
        return keys;
    }
}
