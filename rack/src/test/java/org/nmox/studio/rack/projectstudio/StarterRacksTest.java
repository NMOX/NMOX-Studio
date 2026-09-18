package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rack a project starts with is decided by its kind, and every kind is
 * decided (v2.176.0). The two doors that mount a starter — the New Project
 * wizard and a patchless aim — read the same wirings, so this is also the gate
 * that keeps a template's rack and its detected-kind twin from drifting apart.
 */
class StarterRacksTest {

    @Test
    @DisplayName("every project kind either has a starter rack or sits in BARE with its reason — a new toolchain cannot fall through by accident")
    void everyKindIsDecided() {
        for (ProjectKind kind : ProjectKind.values()) {
            Optional<StarterRacks.Starter> starter = StarterRacks.forKind(kind, new File("."));
            if (StarterRacks.BARE.contains(kind)) {
                assertThat(starter).as(kind + " is blessed bare").isEmpty();
            } else {
                assertThat(starter).as(kind + " has a starter").isPresent();
            }
        }
        assertThat(StarterRacks.BARE)
                .as("only the two kinds with nothing to wire keep the bare rack")
                .containsExactlyInAnyOrderElementsOf(EnumSet.of(ProjectKind.LEARN, ProjectKind.NONE));
    }

    @Test
    @DisplayName("every starter mounts: devices and cables non-empty, every cable legal")
    void everyStarterMounts(@TempDir Path tmp) {
        for (ProjectKind kind : ProjectKind.values()) {
            StarterRacks.forKind(kind, tmp.toFile()).ifPresent(starter -> mount(tmp, starter, kind.name()));
        }
        // the Node family is decided by what the project declares, not its kind
        for (String id : List.of("angular", "vite", "express", "node")) {
            mount(tmp, new StarterRacks.Starter(id, wiringFor(id)), id);
        }
    }

    private static java.util.function.Consumer<Rack> wiringFor(String id) {
        return switch (id) {
            case "angular" -> StarterRacks.ANGULAR;
            case "vite" -> StarterRacks.VITE;
            case "express" -> StarterRacks.EXPRESS;
            default -> StarterRacks.NODE;
        };
    }

    private static void mount(Path tmp, StarterRacks.Starter starter, String label) {
        Rack rack = new Rack();
        rack.setProjectDir(tmp.toFile());
        try {
            JSONObject patch = starter.buildPatch();
            RackIO.fromJson(rack, patch);
            assertThat(rack.getDevices()).as(label + " devices").isNotEmpty();
            assertThat(rack.getCables()).as(label + " cables").isNotEmpty();
            // no MissingDevice placeholders: every device id the wiring names exists
            assertThat(rack.getDevices()).as(label + " names only catalogue devices")
                    .noneMatch(d -> d instanceof org.nmox.studio.rack.model.MissingDevice);
            // and every cable the wiring asked for survived the round trip — a
            // cable RackIO could not connect (wrong port, wrong type) is dropped
            // silently, which is exactly the drift this test exists to see
            assertThat(rack.getCables()).as(label + " keeps every cable")
                    .hasSize(patch.getJSONArray("cables").length());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a Node project is read from what it declares: angular.json → HALO, vite → SURGE, an HTTP framework → the health ping, else scripts")
    void nodeFamilyIsSniffed(@TempDir Path tmp) throws Exception {
        Path angular = Files.createDirectory(tmp.resolve("ng"));
        Files.writeString(angular.resolve("package.json"), "{\"dependencies\":{\"@angular/core\":\"^21\"}}");
        Files.writeString(angular.resolve("angular.json"), "{}");
        assertThat(StarterRacks.forProject(angular.toFile()).orElseThrow().id()).isEqualTo("angular");

        Path vite = Files.createDirectory(tmp.resolve("vite"));
        Files.writeString(vite.resolve("package.json"), "{\"devDependencies\":{\"vite\":\"^6\"}}");
        assertThat(StarterRacks.forProject(vite.toFile()).orElseThrow().id()).isEqualTo("vite");

        Path api = Files.createDirectory(tmp.resolve("api"));
        Files.writeString(api.resolve("package.json"), "{\"dependencies\":{\"express\":\"^5\"}}");
        assertThat(StarterRacks.forProject(api.toFile()).orElseThrow().id()).isEqualTo("express");

        Path plain = Files.createDirectory(tmp.resolve("plain"));
        Files.writeString(plain.resolve("package.json"), "{\"scripts\":{\"dev\":\"node .\"}}");
        assertThat(StarterRacks.forProject(plain.toFile()).orElseThrow().id()).isEqualTo("node");
    }

    @Test
    @DisplayName("a toolchain project gets the polyglot loop; a bare directory gets nothing")
    void toolchainsAndBareDirectories(@TempDir Path tmp) throws Exception {
        Path rust = Files.createDirectory(tmp.resolve("crate"));
        Files.writeString(rust.resolve("Cargo.toml"), "[package]\nname = \"x\"\n");
        assertThat(StarterRacks.forProject(rust.toFile()).orElseThrow().id()).isEqualTo("polyglot");

        Path go = Files.createDirectory(tmp.resolve("gomod"));
        Files.writeString(go.resolve("go.mod"), "module x\n");
        assertThat(StarterRacks.forProject(go.toFile()).orElseThrow().id()).isEqualTo("polyglot");

        Path empty = Files.createDirectory(tmp.resolve("empty"));
        assertThat(StarterRacks.forProject(empty.toFile()))
                .as("no manifest, no toolchain to wire — the bare MONITOR a first launch shows")
                .isEmpty();
    }

    @Test
    @DisplayName("the wizard's templates and the aim path read the SAME wirings — one home, no drift")
    void templatesShareTheWirings() throws Exception {
        // the shared builders are one-liners over StarterRacks; a template that
        // re-inlined its rack would be a second home the aim path could not see
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/projectstudio/ProjectTemplates.java"));
        for (String wiring : List.of("POLYGLOT", "VITE", "ANGULAR", "EXPRESS", "STATIC", "ELIXIR")) {
            assertThat(src).as("templates read StarterRacks." + wiring).contains("StarterRacks." + wiring);
        }
        assertThat(src.split("rack\\.connect\\(").length - 1)
                .as("only TS_LIBRARY keeps a wiring of its own (tsc + vitest, no aim-path twin)")
                .isEqualTo(6);
    }
}
