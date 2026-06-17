package org.nmox.studio.rack.projectstudio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

class CiExporterTest {

    @Test
    @DisplayName("Exports cable-ordered steps with the devices' own commands")
    void exportsOrderedWorkflow() {
        Rack rack = new Rack();
        RackDevice deps = DeviceType.PACKAGE_MANAGER.create();
        RackDevice build = DeviceType.BUILD.create();
        RackDevice test = DeviceType.TEST.create();
        // rack order is deliberately scrambled vs cable order
        rack.addDevice(test);
        rack.addDevice(deps);
        rack.addDevice(build);
        rack.connect(deps.getPort("ok"), build.getPort("run"));
        rack.connect(build.getPort("ok"), test.getPort("run"));

        String yaml = CiExporter.toWorkflowYaml(rack);

        assertThat(yaml).contains("actions/checkout@v4");
        int installAt = yaml.indexOf("CRATE");
        int buildAt = yaml.indexOf("FORGE");
        int testAt = yaml.indexOf("VERITAS");
        assertThat(installAt).isGreaterThan(-1);
        assertThat(installAt).isLessThan(buildAt);
        assertThat(buildAt).isLessThan(testAt);
        // steps carry real run commands
        assertThat(yaml).contains("run: ");
        rack.shutdown();
    }

    @Test
    @DisplayName("Devices that are not pipeline steps never become CI steps")
    void nonStepDevicesExcluded() {
        Rack rack = new Rack();
        rack.addDevice(DeviceType.BROWSER.create());
        rack.addDevice(DeviceType.CONSOLE.create());
        rack.addDevice(DeviceType.TEMPO.create());

        String yaml = CiExporter.toWorkflowYaml(rack);

        assertThat(yaml).doesNotContain("SCOPE").doesNotContain("MONITOR").doesNotContain("TEMPO");
        rack.shutdown();
    }

    @Test
    @DisplayName("Database devices become CI steps")
    void databaseDeviceExported() {
        Rack rack = new Rack();
        rack.addDevice(DeviceType.DATABASE.create());

        String yaml = CiExporter.toWorkflowYaml(rack);

        assertThat(yaml).contains("NEPTUNE");
        rack.shutdown();
    }
}
