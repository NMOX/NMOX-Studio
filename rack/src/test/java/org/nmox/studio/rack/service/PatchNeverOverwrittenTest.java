package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.SwingUtilities;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A patch this session could not read, or one git has not finished
 * merging, is never written over (3.4, questions 1 and 2).
 *
 * <p>Measured before 3.4: a {@code .nmoxrack.json} holding git's conflict
 * markers was MOVED to {@code .bak}, so the project's patch disappeared and
 * {@code git commit -am} recorded the deletion as the merge; a patch this
 * user could not read (mode 000), a directory wearing its name, or bytes that
 * did not decode threw from the read before the rack was emptied, so the
 * PREVIOUS project's devices stayed mounted — and Save Patch then replaced
 * the unreadable file with them.
 */
class PatchNeverOverwrittenTest {

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

    /** A patch file as git leaves it mid-merge. Markers built, never typed. */
    static String conflicted() {
        return "{\n  \"version\": 1,\n"
                + "<".repeat(7) + " HEAD\n  \"devices\": [ { \"type\": \"reflex\", \"state\": {} } ],\n"
                + "=".repeat(7) + "\n  \"devices\": [ { \"type\": \"lint\", \"state\": {} } ],\n"
                + ">".repeat(7) + " theirs\n  \"cables\": []\n}\n";
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    @DisplayName("a conflicted patch is left byte-for-byte, the rack is empty, Save refuses — and resolving it in git reloads")
    void conflictedPatchIsLeftAloneUntilResolved(@TempDir Path tmp) throws Exception {
        Path previous = Files.createDirectory(tmp.resolve("previous"));
        Path merged = Files.createDirectory(tmp.resolve("merged"));
        Path patch = merged.resolve(RackIO.DEFAULT_FILENAME);
        String bytes = conflicted();
        Files.writeString(patch, bytes, StandardCharsets.UTF_8);

        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.setProjectDir(previous.toFile());
            rack.addDevice(DeviceType.REPL.create());
            rack.setProjectDir(merged.toFile());

            assertThat(rack.getDevices()).as("the previous project's devices are gone").isEmpty();
            assertThat(Files.readString(patch)).as("the merge is left exactly as git wrote it").isEqualTo(bytes);
            assertThat(merged.resolve(RackIO.DEFAULT_FILENAME + ".bak")).as("nothing moved or copied").doesNotExist();
            assertThat(service.saveRefusal(patch.toFile()))
                    .as("Save Patch refuses by name")
                    .contains(".nmoxrack.json").contains("merge conflicts");

            // resolved in git: the pulse sees the change and the rack reloads by itself
            JSONPatch.write(patch, "reflex");
            service.tickPatchLock();
            flushEdt();
            assertThat(rack.getDevices()).extracting(RackDevice::getTypeId).containsExactly("reflex");
            assertThat(service.saveRefusal(patch.toFile())).as("a patch that loaded may be saved").isNull();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a directory wearing the patch's name: the previous devices go and Save refuses")
    void directoryAtThePatchName(@TempDir Path tmp) throws Exception {
        Path previous = Files.createDirectory(tmp.resolve("previous"));
        Path odd = Files.createDirectory(tmp.resolve("odd"));
        Files.createDirectory(odd.resolve(RackIO.DEFAULT_FILENAME));
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.setProjectDir(previous.toFile());
            rack.addDevice(DeviceType.REPL.create());
            rack.setProjectDir(odd.toFile());
            assertThat(rack.getDevices()).as("never the previous project's pipeline under this name").isEmpty();
            assertThat(service.saveRefusal(odd.resolve(RackIO.DEFAULT_FILENAME).toFile()))
                    .contains("not read in this session");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a patch this user cannot read (mode 000): the previous devices go, no copy, and Save refuses")
    void unreadablePatch(@TempDir Path tmp) throws Exception {
        Path previous = Files.createDirectory(tmp.resolve("previous"));
        Path locked = Files.createDirectory(tmp.resolve("locked"));
        Path patch = locked.resolve(RackIO.DEFAULT_FILENAME);
        JSONPatch.write(patch, "lint");
        try {
            Files.setPosixFilePermissions(patch, java.util.Set.of());
        } catch (UnsupportedOperationException noPosix) {
            assumeTrue(false, "a POSIX file system is needed to make a file unreadable");
        }
        assumeTrue(!Files.isReadable(patch), "running as a user who reads anything (root)");
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.setProjectDir(previous.toFile());
            rack.addDevice(DeviceType.REPL.create());
            rack.setProjectDir(locked.toFile());
            assertThat(rack.getDevices()).isEmpty();
            assertThat(locked.resolve(RackIO.DEFAULT_FILENAME + ".bak")).doesNotExist();
            assertThat(service.saveRefusal(patch.toFile())).isNotNull();
        } finally {
            Files.setPosixFilePermissions(patch, java.util.EnumSet.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a broken patch whose bytes were copied aside may be written over — the copy is safe")
    void corruptWithCopyStaysWritable(@TempDir Path tmp) throws Exception {
        Path proj = Files.createDirectory(tmp.resolve("broken"));
        Path patch = proj.resolve(RackIO.DEFAULT_FILENAME);
        Files.writeString(patch, "{ not a patch");
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.setProjectDir(proj.toFile());
            assertThat(proj.resolve(RackIO.DEFAULT_FILENAME + ".bak")).hasContent("{ not a patch");
            assertThat(patch).as("a copy, never a move").hasContent("{ not a patch");
            assertThat(service.saveRefusal(patch.toFile())).isNull();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a second rescue never overwrites the first, and the same bytes reuse their copy")
    void rescuesNeverOverwriteEachOther(@TempDir Path tmp) throws Exception {
        File patch = tmp.resolve(RackIO.DEFAULT_FILENAME).toFile();
        Files.writeString(patch.toPath(), "{ first");
        catchLoad(patch);
        catchLoad(patch); // re-read on every aim: no second copy of the same bytes
        Files.writeString(patch.toPath(), "{ second");
        catchLoad(patch);
        assertThat(tmp.resolve(RackIO.DEFAULT_FILENAME + ".bak")).hasContent("{ first");
        assertThat(tmp.resolve(RackIO.DEFAULT_FILENAME + ".2.bak")).hasContent("{ second");
        assertThat(tmp.resolve(RackIO.DEFAULT_FILENAME + ".3.bak")).doesNotExist();
    }

    private static void catchLoad(File patch) {
        Rack rack = new Rack();
        try {
            RackIO.load(rack, patch);
        } catch (java.io.IOException expected) {
            // the refusal is the point; the rescue is what this test reads
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("every new refusal speaks in the reader's language and names the file once")
    void theNewRefusalsSpeak(@TempDir Path tmp) throws Exception {
        File patch = new File("/projects/shop/.nmoxrack.json");
        Path merged = tmp.resolve("p.nmoxrack.json");
        Files.writeString(merged, conflicted());
        Exception conflict = catchRead(merged.toFile());
        assertThat(conflict).isInstanceOf(RackIO.PatchConflictedException.class);
        assertThat(RackService.patchNotLoadedText(patch, conflict))
                .containsOnlyOnce(".nmoxrack.json").contains("merge conflicts").contains("git");
        Exception dir = catchRead(Files.createDirectory(tmp.resolve("d.nmoxrack.json")).toFile());
        assertThat(dir).isInstanceOf(RackIO.PatchUnreadableException.class);
        assertThat(RackService.patchNotLoadedText(patch, dir)).contains("could not be read");
        assertThat(RackService.cablesSentence(patch, new RackIO.CableReport(2, 1)))
                .contains(".nmoxrack.json").contains("2").contains("1");
    }

    @Test
    @DisplayName("Save Patch asks before it writes: the refusal sits ahead of the only write of the project's patch")
    void saveButtonAsksFirst() throws Exception {
        // the seam's behaviour is pinned above; this is the other half the
        // v1.321.0 law asks for — that the one writer of .nmoxrack.json asks
        String src = org.nmox.studio.rack.GateSources.stripComments(Files.readString(Path.of("src", "main", "java",
                "org", "nmox", "studio", "rack", "RackTopComponent.java"), StandardCharsets.UTF_8));
        int save = src.indexOf("Bundle.RackTopComponent_savePatch()");
        assertThat(save).isPositive();
        String listener = src.substring(save, src.indexOf("bar.add(save)", save));
        assertThat(listener).contains("saveRefusal(target)").contains("AtomicFiles.writeString(");
        assertThat(listener.indexOf("saveRefusal(target)"))
                .isLessThan(listener.indexOf("AtomicFiles.writeString("));
    }

    private static Exception catchRead(File f) {
        try {
            RackIO.readDocument(f);
            return null;
        } catch (Exception refused) {
            return refused;
        }
    }

    /** A valid one-device patch. */
    static final class JSONPatch {
        static void write(Path p, String type) throws java.io.IOException {
            Files.writeString(p, "{\"version\":1,\"devices\":[{\"type\":\"" + type
                    + "\",\"state\":{}}],\"cables\":[]}", StandardCharsets.UTF_8);
        }
    }
}
