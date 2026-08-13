package org.nmox.studio.rack.devices;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A user's JSON device is a citizen: it reaches the same catalog every
 * other device does, is resolvable by patch-file id, and faces the same
 * {@link DeviceCatalog#validate} laws as the built-in fleet — including
 * the one that stops it from stealing a built-in's identity.
 */
class UserDeviceCatalogTest {

    private static final String USAGE =
            "GO runs the command and lights FAIL when the exit code is not zero.\\n"
            + "Patch DONE onward to chain another device after this one.";

    @AfterEach
    void reset() {
        UserDevices.dirOverride = null;
        UserDevices.invalidate();
    }

    private void write(File dir, String name, String id, String title) throws Exception {
        Files.writeString(new File(dir, name).toPath(),
                "{\"id\":\"" + id + "\",\"title\":\"" + title + "\",\"tagline\":\"t\","
                + "\"category\":\"AUTOMATE\",\"usage\":\"" + USAGE + "\","
                + "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\",\"command\":[\"npm\"]}]}");
        UserDevices.dirOverride = dir;
        UserDevices.invalidate();
    }

    @Test
    @DisplayName("a user device joins the catalog and resolves by its patch id")
    void joinsCatalog(@TempDir File dir) throws Exception {
        write(dir, "hello.json", "com.example.hello", "HELLO");

        assertThat(DeviceCatalog.all()).extracting(DeviceCatalog.Entry::id)
                .contains("com.example.hello");
        assertThat(DeviceCatalog.byId("com.example.hello")).isPresent()
                .get().extracting(DeviceCatalog.Entry::title).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("the built-in fleet is never displaced by a user file")
    void builtInsSurvive(@TempDir File dir) throws Exception {
        // Measured, not assumed: the test classpath also carries a fixture
        // DeviceExtension, so the baseline is whatever the catalog holds
        // before the drop-in dir is aimed — the claim under test is that a
        // user file ADDS one and takes nothing away.
        int before = DeviceCatalog.all().size();
        long builtInsBefore = DeviceCatalog.all().stream()
                .filter(DeviceCatalog.Entry::builtIn).count();
        assertThat(builtInsBefore).isEqualTo(DeviceType.values().length);

        write(dir, "hello.json", "com.example.hello", "HELLO");

        assertThat(DeviceCatalog.all()).hasSize(before + 1);
        assertThat(DeviceCatalog.all().stream().filter(DeviceCatalog.Entry::builtIn).count())
                .isEqualTo(builtInsBefore);
    }

    @Test
    @DisplayName("a user device is marked as not built-in — the shelf can tell them apart")
    void notBuiltIn(@TempDir File dir) throws Exception {
        write(dir, "hello.json", "com.example.hello", "HELLO");

        assertThat(DeviceCatalog.byId("com.example.hello")).get()
                .extracting(DeviceCatalog.Entry::builtIn).isEqualTo(false);
    }

    @Test
    @DisplayName("two files claiming one id: the first wins, the second is skipped")
    void duplicateIdsRefused(@TempDir File dir) throws Exception {
        write(dir, "a.json", "com.example.dup", "FIRST");
        Files.writeString(new File(dir, "b.json").toPath(),
                "{\"id\":\"com.example.dup\",\"title\":\"SECOND\",\"tagline\":\"t\","
                + "\"category\":\"AUTOMATE\",\"usage\":\"" + USAGE + "\","
                + "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\",\"command\":[\"npm\"]}]}");
        UserDevices.invalidate();

        assertThat(DeviceCatalog.all()).extracting(DeviceCatalog.Entry::title)
                .contains("FIRST").doesNotContain("SECOND");
    }

    @Test
    @DisplayName("mounting uses the file's CURRENT contents, not the parse the shelf listed")
    void mountUsesFreshParse(@TempDir File dir) throws Exception {
        write(dir, "hello.json", "com.example.hello", "HELLO");
        DeviceCatalog.Entry entry = DeviceCatalog.byId("com.example.hello").orElseThrow();

        // the user edits the file while the palette still holds the old
        // entry — the v2.0.0 walk's stale-mount finding: the next mount
        // must reflect THIS content, not the capture
        Files.writeString(new File(dir, "hello.json").toPath(),
                "{\"id\":\"com.example.hello\",\"title\":\"HELLO\",\"tagline\":\"t\","
                + "\"category\":\"AUTOMATE\",\"units\":3,\"usage\":\"" + USAGE + "\","
                + "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\",\"command\":[\"npm\"]}]}");
        UserDevices.invalidate();

        assertThat(entry.create().getUnits())
                .as("the stale entry must mount the edited file's 3U face")
                .isEqualTo(3);
    }
}
