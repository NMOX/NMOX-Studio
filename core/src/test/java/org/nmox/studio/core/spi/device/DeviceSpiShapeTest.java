package org.nmox.studio.core.spi.device;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The frozen Device SPI's declarative shapes, pinned from core's own
 * side (the rack's contract tests exercise them through a host; these
 * pin what a third-party plugin compiled against core alone can rely
 * on): the seven-shelf category roster, every {@link DeviceLogic}
 * callback defaulting to a no-op, and {@link DeviceDescriptor}'s
 * defensive port-list copy. This is a serialized surface — patches
 * store ids, plugins compile against the enums — so any change here
 * failing is a compatibility break, not a refactor.
 */
class DeviceSpiShapeTest {

    @Test
    @DisplayName("the shelf roster is the seven categories the built-in fleet files under")
    void categoryRosterFrozen() {
        assertThat(DeviceCategory.values()).extracting(Enum::name).containsExactly(
                "AUTOMATE", "VERIFY", "SERVE", "FRAMEWORKS", "OBSERVE", "SHIP", "UTILITY");
    }

    @Test
    @DisplayName("port directions and signals are the fixed patch-bay lexicon")
    void portLexiconFrozen() {
        assertThat(PortSpec.Direction.values()).extracting(Enum::name)
                .containsExactly("IN", "OUT");
        assertThat(PortSpec.Signal.values()).extracting(Enum::name)
                .containsExactly("TRIGGER", "DATA", "GATE");
        PortSpec port = new PortSpec("run", "RUN", PortSpec.Direction.IN,
                PortSpec.Signal.TRIGGER);
        assertThat(port.id()).isEqualTo("run");
        assertThat(port.label()).isEqualTo("RUN");
    }

    @Test
    @DisplayName("a bare DeviceLogic is safely inert: every callback defaults to a no-op")
    void logicDefaultsAreNoOps() {
        DeviceLogic bare = new DeviceLogic() {
        };
        assertThatCode(() -> {
            bare.onAttached(null);
            bare.onTrigger("in", true);
            bare.onData("in", "line");
            bare.onGate("enable", true);
            bare.onProjectChanged(new File("."));
            bare.onDispose();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a descriptor's port list is copied and null becomes empty — plugins can't mutate it later")
    void descriptorDefendsItsPorts() {
        List<PortSpec> ports = new ArrayList<>();
        ports.add(new PortSpec("out", "OUT", PortSpec.Direction.OUT, PortSpec.Signal.DATA));
        DeviceDescriptor descriptor = new DeviceDescriptor("com.example.thing",
                "THING", "a thing", Color.CYAN, DeviceCategory.OBSERVE,
                "What it does.\nPatch OUT into a MONITOR to watch it live.", 1, ports);

        ports.clear();
        assertThat(descriptor.ports()).hasSize(1);
        assertThatThrownBy(() -> descriptor.ports().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        DeviceDescriptor portless = new DeviceDescriptor("com.example.mute",
                "MUTE", "silent", Color.GRAY, DeviceCategory.UTILITY,
                "Does nothing.\nMount it to prove the shelf accepts it.", 1, null);
        assertThat(portless.ports()).isEmpty();
    }
}
