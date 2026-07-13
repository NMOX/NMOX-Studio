package org.nmox.studio.rack.devices;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalog seam: every string-id lookup in the product (patch load,
 * palette drop, Quick Search, how-to card, CI export) routes through
 * DeviceCatalog, and today the catalog must mirror the DeviceType enum
 * exactly — same members, same order, same facts. When the device SPI
 * ships, extension entries merge in behind this same interface.
 */
class DeviceCatalogTest {

    @Test
    @DisplayName("the catalog's built-in prefix mirrors the enum: same members, same shelf order")
    void catalogMirrorsTheEnum() {
        List<DeviceCatalog.Entry> all = DeviceCatalog.all();
        DeviceType[] types = DeviceType.values();
        // extensions (here: the SPI test fixtures) append AFTER the fleet
        assertThat(all.size()).isGreaterThanOrEqualTo(types.length);
        for (int i = 0; i < types.length; i++) {
            DeviceCatalog.Entry e = all.get(i);
            DeviceType t = types[i];
            assertThat(e.id()).isEqualTo(t.getId());
            assertThat(e.title()).isEqualTo(t.getTitle());
            assertThat(e.description()).isEqualTo(t.getDescription());
            assertThat(e.accent()).isEqualTo(t.getAccent());
            assertThat(e.category()).isEqualTo(t.getPaletteCategory());
            assertThat(e.usage()).isEqualTo(t.getUsage());
        }
    }

    @Test
    @DisplayName("byId answers every cataloged id and creates the right device")
    void byIdRoundTrips() {
        for (DeviceType t : DeviceType.values()) {
            DeviceCatalog.Entry e = DeviceCatalog.byId(t.getId()).orElseThrow(
                    () -> new AssertionError("catalog misses " + t.getId()));
            assertThat(e.create().getTypeId()).isEqualTo(t.getId());
        }
    }

    @Test
    @DisplayName("an unknown id answers empty, never null and never a throw")
    void unknownIdIsEmpty() {
        assertThat(DeviceCatalog.byId("com.acme.no-such-device")).isEmpty();
        assertThat(DeviceCatalog.byId("")).isEmpty();
    }

    @Test
    @DisplayName("exactly the fourteen step kinds export to CI — the exporter's old hardcoded set, now the catalog's knowledge")
    void ciStepCapabilityIsExact() {
        List<String> ciSteps = DeviceCatalog.all().stream()
                .filter(DeviceCatalog.Entry::ciStep)
                .map(DeviceCatalog.Entry::id)
                .toList();
        assertThat(ciSteps).containsExactlyInAnyOrder(
                "package-manager", "build", "test", "typecheck", "lint", "format",
                "npm-script", "run", "angular", "nextjs", "vite", "phoenix", "audit",
                "database", "cmd");
    }
}
