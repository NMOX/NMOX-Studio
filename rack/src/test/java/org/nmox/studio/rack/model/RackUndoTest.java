package org.nmox.studio.rack.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rack undo: the safety net that deleting a device or cable was missing.
 * Every interactive edit reverses, redoes, and — critically — bringing a
 * removed device back restores the cables it carried.
 */
class RackUndoTest {

    private Rack rack;

    @BeforeEach
    void setUp() {
        rack = new Rack();
        rack.enableUndoCapture();
    }

    @AfterEach
    void tearDown() {
        rack.shutdown();
    }

    @Test
    @DisplayName("Adding a device is undoable and redoable")
    void addUndoRedo() {
        assertThat(rack.canUndo()).isFalse();
        RackDevice d = DeviceType.CONSOLE.create();
        rack.addDevice(d);
        assertThat(rack.getDevices()).containsExactly(d);
        assertThat(rack.undoLabel()).contains("Add");

        rack.undo();
        assertThat(rack.getDevices()).isEmpty();
        assertThat(rack.canRedo()).isTrue();

        rack.redo();
        assertThat(rack.getDevices()).containsExactly(d);
    }

    @Test
    @DisplayName("Undoing a device removal brings back the device AND its cables")
    void removeRestoresCables() {
        RackDevice run = DeviceType.RUN.create();
        RackDevice console = DeviceType.CONSOLE.create();
        rack.addDevice(run);
        rack.addDevice(console);
        Cable cable = rack.connect(run.getPort("out"), console.getPort("in"));
        assertThat(cable).isNotNull();
        assertThat(rack.getCables()).hasSize(1);

        rack.removeDevice(run);
        assertThat(rack.getDevices()).containsExactly(console);
        assertThat(rack.getCables()).as("removing a device severs its cables").isEmpty();

        rack.undo();
        assertThat(rack.getDevices()).contains(run);
        assertThat(rack.getCables()).as("undo re-patches the severed cable").hasSize(1);
        assertThat(rack.getCables().get(0)).isSameAs(cable);
    }

    @Test
    @DisplayName("Patching and unpatching a cable both undo, keeping the same cable object")
    void cableUndo() {
        RackDevice run = DeviceType.RUN.create();
        RackDevice console = DeviceType.CONSOLE.create();
        rack.addDevice(run);
        rack.addDevice(console);

        Cable cable = rack.connect(run.getPort("out"), console.getPort("in"));
        rack.undo();
        assertThat(rack.getCables()).as("undo of connect unpatches").isEmpty();
        rack.redo();
        assertThat(rack.getCables()).containsExactly(cable);

        rack.disconnect(cable);
        assertThat(rack.getCables()).isEmpty();
        rack.undo();
        assertThat(rack.getCables()).as("undo of disconnect re-patches the same cable")
                .containsExactly(cable);
    }

    @Test
    @DisplayName("A new edit clears the redo stack; history is bounded")
    void redoClearedByNewEdit() {
        rack.addDevice(DeviceType.CONSOLE.create());
        rack.undo();
        assertThat(rack.canRedo()).isTrue();
        rack.addDevice(DeviceType.RUN.create());
        assertThat(rack.canRedo()).as("a fresh edit drops the redo branch").isFalse();
    }

    @Test
    @DisplayName("Capture stays off during bulk load; clearUndoHistory wipes it")
    void bulkLoadNotUndoable() {
        Rack fresh = new Rack(); // capture off by default
        try {
            fresh.addDevice(DeviceType.CONSOLE.create());
            assertThat(fresh.canUndo()).as("load-time edits don't record").isFalse();
        } finally {
            fresh.shutdown();
        }
        rack.addDevice(DeviceType.CONSOLE.create());
        rack.clearUndoHistory();
        assertThat(rack.canUndo()).isFalse();
    }
}
