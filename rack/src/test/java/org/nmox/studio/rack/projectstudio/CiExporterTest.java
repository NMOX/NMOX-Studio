package org.nmox.studio.rack.projectstudio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

class CiExporterTest {

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tmp;

    private java.io.File dirWith(String name, String... manifests) throws java.io.IOException {
        java.nio.file.Path d = java.nio.file.Files.createDirectories(tmp.resolve(name));
        for (String m : manifests) {
            java.nio.file.Files.writeString(d.resolve(m), "# manifest");
        }
        return d.toFile();
    }

    @Test
    @DisplayName("Ledger 46: every post-v1.59 toolchain gets a setup action or an honest NOTE")
    void setupStepsForNewToolchains() throws Exception {
        assertThat(CiExporter.setupSteps(dirWith("gleam", "gleam.toml")).toString())
                .contains("erlef/setup-beam").contains("gleam-version");
        assertThat(CiExporter.setupSteps(dirWith("julia", "Project.toml")).toString())
                .contains("julia-actions/setup-julia");
        assertThat(CiExporter.setupSteps(dirWith("vlang", "v.mod")).toString())
                .contains("vlang/setup-v");
        assertThat(CiExporter.setupSteps(dirWith("fortran", "fpm.toml")).toString())
                .contains("fortran-lang/setup-fpm");
        assertThat(CiExporter.setupSteps(dirWith("ada", "alire.toml")).toString())
                .contains("alire-project/setup-alire");
        assertThat(CiExporter.setupSteps(dirWith("nim", "app.nimble")).toString())
                .contains("setup-nim-action");
        assertThat(CiExporter.setupSteps(dirWith("dlang", "dub.json")).toString())
                .contains("setup-dlang");
        assertThat(CiExporter.setupSteps(dirWith("racket", "info.rkt")).toString())
                .contains("setup-racket");
        assertThat(CiExporter.setupSteps(dirWith("zig", "build.zig")).toString())
                .contains("setup-zig");
        assertThat(CiExporter.setupSteps(dirWith("dart", "pubspec.yaml")).toString())
                .contains("dart-lang/setup-dart");
        assertThat(CiExporter.setupSteps(dirWith("haskell", "stack.yaml")).toString())
                .contains("haskell-actions/setup");
        assertThat(CiExporter.setupSteps(dirWith("ocaml", "dune-project")).toString())
                .contains("ocaml/setup-ocaml");
        assertThat(CiExporter.setupSteps(dirWith("crystal", "shard.yml")).toString())
                .contains("install-crystal");
        // actionless kinds say so instead of failing silently on the runner
        assertThat(CiExporter.setupSteps(dirWith("scala", "build.sbt")).toString())
                .contains("# NOTE:").contains("sbt");
        assertThat(CiExporter.setupSteps(dirWith("swift", "Package.swift")).toString())
                .contains("# NOTE:").contains("swift");
    }

    @Test
    @DisplayName("Ledger 46: the npm-riding functional web dedupes to ONE setup-node")
    void npmFamilyDedupes() throws Exception {
        var steps = CiExporter.setupSteps(dirWith("mixed", "package.json", "elm.json"));
        long nodeSetups = steps.stream().filter(s -> s.contains("actions/setup-node")).count();
        assertThat(nodeSetups).isEqualTo(1);

        // a pure elm project (no package.json) still gets node for npx elm
        assertThat(CiExporter.setupSteps(dirWith("pure-elm", "elm.json")).toString())
                .contains("actions/setup-node");
    }

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
