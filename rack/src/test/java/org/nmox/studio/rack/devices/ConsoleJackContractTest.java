package org.nmox.studio.rack.devices;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 50: a declared input jack is a promise. Every catalog device
 * that shows a STOP or ENABLE IN jack on its faceplate must actually
 * handle signals on it — {@code CommandDevice.receive} only speaks
 * "run", so declaring the jacks without overriding receive() ships
 * dead brass (the v1.65.0–v1.89.0 console family, fixed in v1.90.0).
 * Structural on purpose: it parameterizes over the whole catalog, so
 * the next console added without wiring fails this gate.
 */
class ConsoleJackContractTest {

    @Test
    @DisplayName("Every device declaring a STOP or ENABLE in-jack overrides receive()")
    void declaredJacksAreHandled() {
        List<String> dead = new ArrayList<>();
        for (DeviceCatalog.Entry entry : DeviceCatalog.all()) {
            RackDevice device = entry.create();
            try {
                boolean declares = device.getPorts().stream()
                        .filter(p -> p.getDirection() == Port.Direction.IN)
                        .map(Port::getId)
                        .anyMatch(id -> id.equals("stop") || id.equals("enable"));
                if (!declares) {
                    continue;
                }
                Class<?> declarer;
                try {
                    declarer = device.getClass()
                            .getMethod("receive", Port.class, Signal.class)
                            .getDeclaringClass();
                } catch (NoSuchMethodException impossible) {
                    throw new AssertionError(impossible);
                }
                if (declarer == CommandDevice.class || declarer == RackDevice.class) {
                    dead.add(entry.id() + " (" + entry.title() + ")");
                }
            } finally {
                device.dispose();
            }
        }
        assertThat(dead)
                .as("devices declaring STOP/ENABLE jacks that no receive() handles")
                .isEmpty();
    }

    @Test
    @DisplayName("Rear INPUTS and OUTPUTS never collide — jack-heavy devices compress their pitch")
    void rearGroupsNeverOverlap() {
        for (DeviceCatalog.Entry entry : DeviceCatalog.all()) {
            RackDevice device = entry.create();
            try {
                int ins = 0, outs = 0, maxInX = Integer.MIN_VALUE, minOutX = Integer.MAX_VALUE;
                for (Port p : device.getPorts()) {
                    if (p.getDirection() == Port.Direction.IN) {
                        ins++;
                        maxInX = Math.max(maxInX, p.getX());
                    } else {
                        outs++;
                        minOutX = Math.min(minOutX, p.getX());
                    }
                }
                if (ins == 0 || outs == 0) {
                    continue;
                }
                // half a label (~40px tiny font) each side plus breathing room
                assertThat(minOutX - maxInX)
                        .as(entry.id() + " (" + entry.title() + "): rear groups need clear air")
                        .isGreaterThanOrEqualTo(44);
            } finally {
                device.dispose();
            }
        }
    }
}
