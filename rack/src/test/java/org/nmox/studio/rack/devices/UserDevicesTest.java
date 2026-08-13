package org.nmox.studio.rack.devices;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.device.DeviceExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The drop-in half: what the shelf gets from {@code ~/.nmox/devices.d}.
 * The family law under test is skip-with-note — one bad file must never
 * cost the user their other devices, or the built-in fleet.
 */
class UserDevicesTest {

    private static final String USAGE =
            "GO runs the command and lights FAIL when the exit code is not zero.\\n"
            + "Patch DONE onward to chain another device after this one.";

    @AfterEach
    void reset() {
        UserDevices.dirOverride = null;
        UserDevices.invalidate();
    }

    private static String device(String id, String title) {
        return "{\"id\":\"" + id + "\",\"title\":\"" + title + "\",\"tagline\":\"t\","
                + "\"category\":\"AUTOMATE\",\"usage\":\"" + USAGE + "\","
                + "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\",\"command\":[\"npm\"]}]}";
    }

    private void aim(File dir) {
        UserDevices.dirOverride = dir;
        UserDevices.invalidate();
    }

    @Test
    @DisplayName("a well-formed file becomes a device on the shelf")
    void loadsDevice(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, "hello.json").toPath(),
                device("com.example.hello", "HELLO"));
        aim(dir);

        List<DeviceExtension> all = UserDevices.all();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).descriptor().title()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("one malformed file is skipped; its neighbours still load")
    void skipsWithNote(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, "a-good.json").toPath(),
                device("com.example.good", "GOOD"));
        Files.writeString(new File(dir, "b-broken.json").toPath(), "{oops");
        Files.writeString(new File(dir, "c-lawless.json").toPath(),
                "{\"id\":\"com.example.lawless\",\"title\":\"L\",\"tagline\":\"t\","
                + "\"category\":\"AUTOMATE\",\"usage\":\"short\",\"buttons\":[]}");
        Files.writeString(new File(dir, "d-good.json").toPath(),
                device("com.example.also", "ALSO"));
        aim(dir);

        assertThat(UserDevices.all())
                .extracting(e -> e.descriptor().title())
                .containsExactly("GOOD", "ALSO");
    }

    @Test
    @DisplayName("devices load in filename order, and non-JSON is ignored")
    void filenameOrder(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, "2-second.json").toPath(),
                device("com.example.second", "SECOND"));
        Files.writeString(new File(dir, "1-first.json").toPath(),
                device("com.example.first", "FIRST"));
        Files.writeString(new File(dir, "notes.txt").toPath(), "not a device");
        aim(dir);

        assertThat(UserDevices.all())
                .extracting(e -> e.descriptor().title())
                .containsExactly("FIRST", "SECOND");
    }

    @Test
    @DisplayName("the real drop-in directory is ~/.nmox/devices.d")
    void realPath() {
        // Every other test aims dirOverride somewhere temporary, which would
        // happily mask a typo in the ONE path users are told to create. The
        // documented location is part of the contract, so assert it.
        UserDevices.dirOverride = null;
        assertThat(UserDevices.dropInDir().getPath())
                .isEqualTo(new File(System.getProperty("user.home"),
                        ".nmox/devices.d").getPath());
    }

    @Test
    @DisplayName("a missing directory is simply no devices, never an error")
    void missingDirectory(@TempDir File dir) {
        aim(new File(dir, "does-not-exist"));
        assertThat(UserDevices.all()).isEmpty();
    }

    @Test
    @DisplayName("an edited file is picked up once the signature changes")
    void reloadsOnChange(@TempDir File dir) throws Exception {
        File f = new File(dir, "one.json");
        Files.writeString(f.toPath(), device("com.example.one", "BEFORE"));
        aim(dir);
        assertThat(UserDevices.all().get(0).descriptor().title()).isEqualTo("BEFORE");

        Files.writeString(f.toPath(), device("com.example.one", "AFTER"));
        UserDevices.invalidate();
        assertThat(UserDevices.all().get(0).descriptor().title()).isEqualTo("AFTER");
    }
}
