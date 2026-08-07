package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User-authored rack presets (v1.294.0, the extensibility arc): a
 * {@code .json} file in {@code ~/.nmox/presets.d} joins the Presets
 * menu, and the file format IS the Save Patch format — capture a
 * wiring you like, copy the file, have it everywhere.
 */
class UserPresetsTest {

    @Test
    @DisplayName("listing: .json files in filename order, named sans extension")
    void listsJsonFilesInOrder(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("b-bench.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve("a-loop.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve("notes.txt"), "not a preset", StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve(".json"), "{}", StandardCharsets.UTF_8);

        assertThat(UserPresets.listFrom(tmp.toFile()))
                .extracting(UserPresets.Custom::name)
                .as("only .json files count, blank names excluded, filename order")
                .containsExactly("a-loop", "b-bench");
    }

    @Test
    @DisplayName("a missing drop-in dir lists nothing — no dir is created as a side effect")
    void missingDirIsEmpty(@TempDir Path tmp) {
        File absent = tmp.resolve("never-created").toFile();
        assertThat(UserPresets.listFrom(absent)).isEmpty();
        assertThat(absent).as("listing must not create directories").doesNotExist();
    }

    @Test
    @DisplayName("a saved patch IS a valid preset: toJson round-trips through the drop-in shape")
    void savedPatchRoundTripsAsPreset(@TempDir Path tmp) throws Exception {
        // build a small real rack, save it the way Save Patch does…
        Rack original = new Rack();
        original.addDevice(org.nmox.studio.rack.devices.DeviceType.CONSOLE.create());
        File preset = tmp.resolve("my-bench.json").toFile();
        Files.writeString(preset.toPath(), RackIO.toJson(original).toString(2),
                StandardCharsets.UTF_8);

        // …then apply it to a fresh rack exactly as the menu item does
        Rack fresh = new Rack();
        RackIO.fromJson(fresh, RackIO.readDocument(preset));

        assertThat(fresh.getDevices())
                .as("the drop-in promise: no schema to learn, Save Patch output"
                        + " is the preset format")
                .hasSize(1);
        assertThat(fresh.getDevices().get(0).getTypeId())
                .isEqualTo(original.getDevices().get(0).getTypeId());
    }

    @Test
    @DisplayName("the menu wires customs through loadPatch and keeps the replace-confirm")
    void menuWiring() throws Exception {
        // CRLF checkouts (the windows lane) — normalize before asserting
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "RackTopComponent.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertThat(src)
                .as("customs must join the menu, or the drop-in dir is dead")
                .contains("UserPresets.list()");
        assertThat(src)
                .as("a custom preset must apply through the off-EDT read path"
                        + " Load Patch already uses, never a same-thread parse")
                .contains("loadPatch(custom.file())");
        assertThat(src)
                .as("a preset click destroys the current wiring with undo"
                        + " powerless (v1.280.0) — customs need the same"
                        + " confirm the built-ins have")
                .contains("confirmReplace(\"the \" + custom.name()");
        assertThat(src)
                .as("the drop-in scan is file IO and stays off the EDT")
                .contains("RequestProcessor.getDefault().post");
    }
}
