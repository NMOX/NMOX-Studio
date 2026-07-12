package org.nmox.studio.core.spi.device;

import java.io.File;

/**
 * The device's behavior: callbacks the host invokes as the rack lives.
 * Every method defaults to a no-op — implement only what the device
 * reacts to.
 *
 * <p><b>Threading:</b> signal callbacks arrive on the rack's router
 * thread, never the EDT — mutate the UI only through the face handles,
 * which marshal internally. {@code onProjectChanged} may arrive on any
 * thread. Callbacks must return promptly; long work belongs in
 * {@link DeviceServices#exec}.
 *
 * @since 1.55
 */
public interface DeviceLogic {

    /** A TRIGGER arrived on an IN port ({@code ok} carries pass/fail). */
    default void onTrigger(String portId, boolean ok) {
    }

    /** A DATA line arrived on an IN port. */
    default void onData(String portId, String text) {
    }

    /** A GATE level changed on an IN port. */
    default void onGate(String portId, boolean high) {
    }

    /** The rack aimed at a different project directory. */
    default void onProjectChanged(File dir) {
    }

    /**
     * The device left the rack. The host has already stopped the
     * device's process; release anything else here. Undo of a remove
     * re-mounts the SAME instance, so leave it revivable.
     */
    default void onDispose() {
    }
}
