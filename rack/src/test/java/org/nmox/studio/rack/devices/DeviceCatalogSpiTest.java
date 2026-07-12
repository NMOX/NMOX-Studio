package org.nmox.studio.rack.devices;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.device.DeviceCategory;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalog's open half: extensions registered via the service
 * loader (the same discovery a real third-party NBM uses — see the
 * META-INF/services file in test resources) merge in next to the
 * built-ins, and law-breaking descriptors are skipped with a note,
 * never listed. The GOOD fixture also rides DeviceContractTest's
 * catalog parameterization, so every contract law runs against a
 * hosted extension for free.
 */
class DeviceCatalogSpiTest {

    @Test
    @DisplayName("a registered extension appears in the catalog and mounts a hosted device")
    void goodExtensionIsListed() {
        DeviceCatalog.Entry e = DeviceCatalog.byId("org.nmox.fixture.echo").orElseThrow();
        assertThat(e.builtIn()).isFalse();
        assertThat(e.title()).isEqualTo("FIXTURE");
        assertThat(e.category()).isEqualTo(DeviceType.PaletteCategory.OBSERVE);
        assertThat(e.ciStep()).as("extensions never export as CI steps (v1 scope)").isFalse();
        RackDevice device = e.create();
        assertThat(device).isInstanceOf(ExtensionDevice.class);
        assertThat(device.getTypeId()).isEqualTo("org.nmox.fixture.echo");
    }

    @Test
    @DisplayName("law-breaking descriptors are skipped: no dot, taken id, thin usage")
    void invalidExtensionsAreSkipped() {
        List<String> ids = DeviceCatalog.all().stream().map(DeviceCatalog.Entry::id).toList();
        assertThat(ids).doesNotContain("nodots", "org.nmox.fixture.thin");
        // the impostor claiming SOLDER's id must not displace the built-in
        DeviceCatalog.Entry cmd = DeviceCatalog.byId("cmd").orElseThrow();
        assertThat(cmd.builtIn()).isTrue();
        assertThat(cmd.title()).isEqualTo("SOLDER");
    }

    @Test
    @DisplayName("built-ins keep shelf order and extensions append; ids stay unique")
    void orderAndUniqueness() {
        List<DeviceCatalog.Entry> all = DeviceCatalog.all();
        int builtIns = DeviceType.values().length;
        for (int i = 0; i < builtIns; i++) {
            assertThat(all.get(i).builtIn()).isTrue();
            assertThat(all.get(i).id()).isEqualTo(DeviceType.values()[i].getId());
        }
        assertThat(all.stream().skip(builtIns).allMatch(e -> !e.builtIn())).isTrue();
        assertThat(all.stream().map(DeviceCatalog.Entry::id).distinct().count())
                .isEqualTo(all.size());
    }

    @Test
    @DisplayName("every SPI category maps onto a real palette shelf")
    void categoriesCoverTheShelf() {
        for (DeviceCategory c : DeviceCategory.values()) {
            assertThat(DeviceType.PaletteCategory.valueOf(c.name())).isNotNull();
        }
    }

    @Test
    @DisplayName("validation names each law it refuses")
    void validationSpeaksClearly() {
        java.util.Set<String> taken = java.util.Set.of("cmd");
        assertThat(DeviceCatalog.validate(null, taken)).contains("null");
        assertThat(DeviceCatalog.validate(new org.nmox.studio.core.spi.device.DeviceDescriptor(
                "noDots", "T", "", null, DeviceCategory.UTILITY,
                "long enough usage text for the shelf law to be satisfied here.\nrecipe",
                1, List.of()), taken)).contains("reverse-DNS");
        assertThat(DeviceCatalog.validate(new org.nmox.studio.core.spi.device.DeviceDescriptor(
                "com.x.y", "T", "", null, DeviceCategory.UTILITY, "thin", 1, List.of()),
                taken)).contains("shelf law");
        assertThat(DeviceCatalog.validate(new org.nmox.studio.core.spi.device.DeviceDescriptor(
                "com.x.y", "T", "", null, DeviceCategory.UTILITY,
                "long enough usage text for the shelf law to be satisfied here.\nrecipe",
                1, List.of(new org.nmox.studio.core.spi.device.PortSpec(
                        "up", "UP", org.nmox.studio.core.spi.device.PortSpec.Direction.OUT,
                        org.nmox.studio.core.spi.device.PortSpec.Signal.GATE))),
                taken)).contains("RUNNING");
        assertThat(DeviceCatalog.validate(new org.nmox.studio.core.spi.device.DeviceDescriptor(
                "com.x.y", "T", "", null, DeviceCategory.UTILITY,
                "long enough usage text for the shelf law to be satisfied here.\nrecipe",
                1, List.of(new org.nmox.studio.core.spi.device.PortSpec(
                        "start", "START", org.nmox.studio.core.spi.device.PortSpec.Direction.IN,
                        org.nmox.studio.core.spi.device.PortSpec.Signal.TRIGGER))),
                taken)).contains("stop");
    }

    @Test
    @DisplayName("too many ports on one side is refused before jacks paint off the panel")
    void portCountIsCapped() {
        java.util.List<org.nmox.studio.core.spi.device.PortSpec> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < DeviceCatalog.MAX_PORTS_PER_SIDE + 1; i++) {
            tooMany.add(new org.nmox.studio.core.spi.device.PortSpec(
                    "in" + i, "IN" + i, org.nmox.studio.core.spi.device.PortSpec.Direction.IN,
                    org.nmox.studio.core.spi.device.PortSpec.Signal.DATA));
        }
        String problem = DeviceCatalog.validate(new org.nmox.studio.core.spi.device.DeviceDescriptor(
                "com.x.ports", "T", "", null, DeviceCategory.UTILITY,
                "long enough usage text for the shelf law to be satisfied here.\nrecipe",
                1, tooMany), java.util.Set.of());
        assertThat(problem).as("overflowing port count refused").contains("per side");

        // and a device that fills the panel to the cap mounts with every jack
        // inside the plate — the contract law that validation now pre-empts
        java.util.List<org.nmox.studio.core.spi.device.PortSpec> atCap = new java.util.ArrayList<>();
        for (int i = 0; i < DeviceCatalog.MAX_PORTS_PER_SIDE; i++) {
            atCap.add(new org.nmox.studio.core.spi.device.PortSpec(
                    "o" + i, "O" + i, org.nmox.studio.core.spi.device.PortSpec.Direction.OUT,
                    org.nmox.studio.core.spi.device.PortSpec.Signal.DATA));
        }
        org.nmox.studio.core.spi.device.DeviceDescriptor d =
                new org.nmox.studio.core.spi.device.DeviceDescriptor(
                        "com.x.atcap", "T", "", null, DeviceCategory.UTILITY,
                        "long enough usage text for the shelf law to be satisfied here.\nrecipe",
                        1, atCap);
        assertThat(DeviceCatalog.validate(d, java.util.Set.of())).isNull();
        ExtensionDevice mounted = new ExtensionDevice(new org.nmox.studio.core.spi.device.DeviceExtension() {
            @Override
            public org.nmox.studio.core.spi.device.DeviceDescriptor descriptor() {
                return d;
            }

            @Override
            public org.nmox.studio.core.spi.device.DeviceLogic build(
                    org.nmox.studio.core.spi.device.DeviceFace face,
                    org.nmox.studio.core.spi.device.DeviceServices services) {
                return new org.nmox.studio.core.spi.device.DeviceLogic() {
                };
            }
        });
        int w = mounted.getPreferredSize().width;
        for (org.nmox.studio.rack.model.Port p : mounted.getPorts()) {
            assertThat(p.getX()).as("jack " + p.getId() + " x").isBetween(0, w);
        }
    }
}
