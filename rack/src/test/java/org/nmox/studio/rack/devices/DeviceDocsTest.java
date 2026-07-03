package org.nmox.studio.rack.devices;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documentation that cannot lie: docs/devices.md is generated from the
 * DeviceType catalog itself - titles, descriptions, usage recipes, and
 * the real jacks on every back panel. This test fails when the
 * committed file drifts from the catalog; regenerate with
 *
 *   mvn -pl rack test -Dtest=DeviceDocsTest -Dnmox.docs.write=true
 */
class DeviceDocsTest {

    @Test
    @DisplayName("docs/devices.md matches the device catalog exactly")
    void deviceReferenceIsCurrent() throws Exception {
        String generated = generate();
        File doc = new File("../docs/devices.md");
        if (Boolean.getBoolean("nmox.docs.write")) {
            Files.writeString(doc.toPath(), generated);
            return;
        }
        assertThat(doc).as("docs/devices.md missing — regenerate with -Dnmox.docs.write=true").exists();
        assertThat(Files.readString(doc.toPath()))
                .as("docs/devices.md is stale — regenerate with -Dnmox.docs.write=true")
                .isEqualTo(generated);
    }

    static String generate() {
        StringBuilder md = new StringBuilder();
        md.append("# The Device Reference\n\n");
        md.append("Every unit in the rack, straight from the catalog — this file is\n");
        md.append("generated from `DeviceType` by `DeviceDocsTest` and CI fails if it\n");
        md.append("drifts. Do not edit by hand; regenerate with:\n\n");
        md.append("```\nmvn -pl rack test -Dtest=DeviceDocsTest -Dnmox.docs.write=true\n```\n");

        Map<DeviceType.PaletteCategory, List<DeviceType>> byCategory = new LinkedHashMap<>();
        for (DeviceType.PaletteCategory cat : DeviceType.PaletteCategory.values()) {
            byCategory.put(cat, new java.util.ArrayList<>());
        }
        for (DeviceType type : DeviceType.values()) {
            byCategory.get(type.getPaletteCategory()).add(type);
        }
        for (var entry : byCategory.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            md.append("\n## ").append(entry.getKey()).append("\n");
            for (DeviceType type : entry.getValue()) {
                RackDevice device = type.create();
                md.append("\n### ").append(type.getTitle())
                        .append(" — ").append(type.getDescription()).append("\n\n");
                for (String line : type.getUsage().split("\n")) {
                    md.append("> ").append(line).append("\n");
                }
                StringBuilder ins = new StringBuilder();
                StringBuilder outs = new StringBuilder();
                for (Port p : device.getPorts()) {
                    StringBuilder side = p.getDirection() == Port.Direction.IN ? ins : outs;
                    if (side.length() > 0) {
                        side.append(", ");
                    }
                    side.append("`").append(p.getLabel()).append("` (")
                            .append(p.getType().name().toLowerCase()).append(")");
                }
                md.append("\n");
                if (ins.length() > 0) {
                    md.append("- **In:** ").append(ins).append("\n");
                }
                if (outs.length() > 0) {
                    md.append("- **Out:** ").append(outs).append("\n");
                }
                device.dispose();
            }
        }
        return md.toString();
    }
}
