package org.nmox.studio.rack.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A patch that loaded cleanly and THEN changed on disk — a {@code git pull}
 * with the IDE open (3.4, question 1; the hostile review of the first cut).
 *
 * <p>Measured before the fix: the conflict check ran only at LOAD, and the
 * only watcher was the one a FAILED load created. A clean load bound the rack
 * writable for the rest of the session, so a pull that left the patch
 * conflicted was never looked at again: {@code saveRefusal} stayed null and
 * Save Patch replaced both people's racks with the one on screen.
 */
class PatchPulledUnderTheIdeTest {

    private Locale before;

    @BeforeEach
    void english() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    private static JSONObject snapshotOf(Rack rack) throws Exception {
        JSONObject[] out = new JSONObject[1];
        java.awt.EventQueue.invokeAndWait(() -> out[0] = RackIO.toJson(rack));
        return out[0];
    }

    @Test
    @DisplayName("a conflict arriving after a clean load: Save refuses it, and the write refuses it too, leaving git's bytes")
    void conflictArrivingAfterACleanLoadIsRefused(@TempDir Path tmp) throws Exception {
        Path proj = Files.createDirectory(tmp.resolve("proj"));
        Path patch = proj.resolve(RackIO.DEFAULT_FILENAME);
        PatchNeverOverwrittenTest.JSONPatch.write(patch, "reflex");
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.setProjectDir(proj.toFile());
            service.awaitPatchIdle();
            assertThat(rack.getDevices()).hasSize(1);
            assertThat(service.saveRefusal(patch.toFile())).as("a clean load may be saved").isNull();

            // git pull in the IDE's terminal leaves the patch conflicted
            String merge = PatchNeverOverwrittenTest.conflicted();
            Files.writeString(patch, merge, StandardCharsets.UTF_8);
            service.tickPatchLock();

            assertThat(service.saveRefusal(patch.toFile()))
                    .as("Save Patch must refuse a patch git has not finished merging")
                    .isNotNull().contains("merge conflicts");
            assertThat(rack.getDevices()).as("the rack on screen is kept, not emptied").hasSize(1);
            assertThatThrownBy(() -> service.writePatch(patch.toFile(), snapshotOf(rack)))
                    .isInstanceOf(RackService.PatchWriteRefusedException.class);
            assertThat(Files.readString(patch)).as("both people's racks are still in the file").isEqualTo(merge);
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("even before the watcher has looked, the write asks the disk: a pull between the click and the write refuses it")
    void theWriteAsksTheDiskItself(@TempDir Path tmp) throws Exception {
        Path proj = Files.createDirectory(tmp.resolve("proj"));
        Path patch = proj.resolve(RackIO.DEFAULT_FILENAME);
        PatchNeverOverwrittenTest.JSONPatch.write(patch, "reflex");
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.setProjectDir(proj.toFile());
            service.awaitPatchIdle();
            PatchNeverOverwrittenTest.JSONPatch.write(patch, "lint"); // a teammate's rack, pulled
            String theirs = Files.readString(patch);
            assertThatThrownBy(() -> service.writePatch(patch.toFile(), snapshotOf(rack)))
                    .isInstanceOf(RackService.PatchWriteRefusedException.class)
                    .hasMessageContaining("changed on disk");
            assertThat(Files.readString(patch)).isEqualTo(theirs);
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("our own save is ours: the next save goes ahead, and the watcher reloads nothing")
    void ownWritesStayOurs(@TempDir Path tmp) throws Exception {
        Path proj = Files.createDirectory(tmp.resolve("proj"));
        Path patch = proj.resolve(RackIO.DEFAULT_FILENAME);
        PatchNeverOverwrittenTest.JSONPatch.write(patch, "reflex");
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.setProjectDir(proj.toFile());
            service.awaitPatchIdle();
            java.awt.EventQueue.invokeAndWait(() -> rack.addDevice(DeviceType.LINT.create()));
            service.writePatch(patch.toFile(), snapshotOf(rack));
            service.tickPatchLock();
            assertThat(service.saveRefusal(patch.toFile())).isNull();
            service.writePatch(patch.toFile(), snapshotOf(rack)); // no refusal
            assertThat(rack.getDevices()).extracting(RackDevice::getTypeId).containsExactly("reflex", "lint");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a clean rack follows a pulled patch; unsaved work stays and locks Save until Load Patch")
    void foreignChangeReloadsACleanRackAndLocksADirtyOne(@TempDir Path tmp) throws Exception {
        Path proj = Files.createDirectory(tmp.resolve("proj"));
        Path patch = proj.resolve(RackIO.DEFAULT_FILENAME);
        PatchNeverOverwrittenTest.JSONPatch.write(patch, "reflex");
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.setProjectDir(proj.toFile());
            service.awaitPatchIdle();

            PatchNeverOverwrittenTest.JSONPatch.write(patch, "lint");
            service.tickPatchLock();
            assertThat(rack.getDevices()).as("nothing unsaved: the file wins")
                    .extracting(RackDevice::getTypeId).containsExactly("lint");
            assertThat(service.saveRefusal(patch.toFile())).isNull();

            java.awt.EventQueue.invokeAndWait(() -> rack.addDevice(DeviceType.TEST.create()));
            Files.writeString(patch, "{\"version\":1,\"devices\":[{\"type\":\"format\",\"state\":{}}],\"cables\":[]}");
            service.tickPatchLock();
            assertThat(rack.getDevices()).as("the unsaved rack stays on screen")
                    .extracting(RackDevice::getTypeId).containsExactly("lint", "test");
            assertThat(service.saveRefusal(patch.toFile())).contains("changed on disk");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a patch from a newer NMOX Studio is shown, and never written over")
    void newerFormatIsReadOnly(@TempDir Path tmp) throws Exception {
        Path proj = Files.createDirectory(tmp.resolve("proj"));
        Path patch = proj.resolve(RackIO.DEFAULT_FILENAME);
        Files.writeString(patch, "{\"version\":" + (RackIO.FORMAT + 1)
                + ",\"devices\":[{\"type\":\"reflex\",\"state\":{},\"laterField\":1}],\"cables\":[]}");
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.setProjectDir(proj.toFile());
            service.awaitPatchIdle();
            assertThat(rack.getDevices()).extracting(RackDevice::getTypeId).containsExactly("reflex");
            assertThat(service.saveRefusal(patch.toFile())).contains("newer NMOX Studio");
        } finally {
            rack.shutdown();
        }
    }
}
