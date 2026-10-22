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
import org.nmox.studio.rack.model.RackDevice;
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
    void everyKindIsDecided(@TempDir Path tmp) {
        for (ProjectKind kind : ProjectKind.values()) {
            Optional<StarterRacks.Starter> starter = StarterRacks.forKind(kind, tmp.toFile());
            if (StarterRacks.BARE.contains(kind)) {
                assertThat(starter).as(kind + " is blessed bare").isEmpty();
            } else if (kind == ProjectKind.NONE) {
                // decided by the folder (3.8.0): an empty one keeps the bare rack
                assertThat(starter).as("NONE with nothing to run is bare").isEmpty();
            } else {
                assertThat(starter).as(kind + " has a starter").isPresent();
            }
        }
        assertThat(StarterRacks.BARE)
                .as("only the learning space, whose driver writes its own patch, keeps the bare rack unconditionally")
                .containsExactlyInAnyOrderElementsOf(EnumSet.of(ProjectKind.LEARN));
    }

    @Test
    @DisplayName("a folder with no manifest but one obvious script starts with SOLDER dialled to it, feeding MONITOR (3.8.0)")
    void scriptFolderGetsSolder(@TempDir Path tmp) throws Exception {
        Path scripts = Files.createDirectory(tmp.resolve("scripts"));
        Files.writeString(scripts.resolve("hello.py"), "print('hi')\n");
        Files.writeString(scripts.resolve("notes.md"), "# notes\n");
        StarterRacks.Starter starter = StarterRacks.forProject(scripts.toFile()).orElseThrow();
        assertThat(starter.id()).isEqualTo("script");
        Rack rack = new Rack();
        rack.setProjectDir(scripts.toFile());
        starter.wiring().accept(rack);
        assertThat(rack.getDevices()).extracting(RackDevice::getTypeId).containsExactly("cmd", "console");
        RackDevice solder = rack.getDevices().get(0);
        assertThat(solder.getState()).containsEntry("command", "python3 hello.py");
        assertThat(rack.getCables()).as("SOLDER OUT feeds MONITOR IN").hasSize(1);
        assertThat(rack.getCables().get(0).getFrom().getDevice()).isSameAs(solder);
        assertThat(solder.getState().getOrDefault("armed", "false"))
                .as("the starter arrives at rest: GO is the user's gesture").isEqualTo("false");

        // the ambiguous folder is honest: two conventional entries, no guess, the bare rack
        Path two = Files.createDirectory(tmp.resolve("two"));
        Files.writeString(two.resolve("main.py"), "");
        Files.writeString(two.resolve("app.js"), "");
        assertThat(StarterRacks.forProject(two.toFile())).isEmpty();

        // a manifest still outranks a script beside it: this is a Node project, not a script folder
        Path node = Files.createDirectory(tmp.resolve("node"));
        Files.writeString(node.resolve("package.json"), "{}");
        Files.writeString(node.resolve("hello.py"), "");
        assertThat(StarterRacks.forProject(node.toFile()).orElseThrow().id()).isEqualTo("node");

        // a root index.html is a STATIC site before it is a script folder
        Path site = Files.createDirectory(tmp.resolve("site"));
        Files.writeString(site.resolve("index.html"), "<p>hi</p>");
        Files.writeString(site.resolve("app.js"), "");
        assertThat(StarterRacks.forProject(site.toFile()).orElseThrow().id()).isEqualTo("classic");
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
    @DisplayName("all() and presetBehind() between them answer every starter forKind can hand out — the gallery lists each wiring exactly once")
    void everyStarterIsListedOnceOrIsAPreset(@TempDir Path tmp) throws Exception {
        List<String> listed = StarterRacks.all().stream().map(StarterRacks.Starter::id).toList();
        assertThat(listed).doesNotHaveDuplicates();
        java.util.Set<String> handedOut = new java.util.TreeSet<>(List.of("angular", "vite", "express", "node"));
        for (ProjectKind kind : ProjectKind.values()) {
            StarterRacks.forKind(kind, tmp.toFile()).ifPresent(s -> handedOut.add(s.id()));
        }
        // NONE answers only for a folder with a script in it (3.8.0): hand one over so the
        // derived starter is in the population this law reads
        Files.writeString(tmp.resolve("hello.py"), "");
        StarterRacks.forKind(ProjectKind.NONE, tmp.toFile()).ifPresent(s -> handedOut.add(s.id()));
        assertThat(handedOut).contains("script");
        for (String id : handedOut) {
            boolean own = listed.contains(id);
            boolean preset = StarterRacks.presetBehind(id).isPresent();
            boolean derived = StarterRacks.DERIVED.contains(id);
            assertThat((own ? 1 : 0) + (preset ? 1 : 0) + (derived ? 1 : 0))
                    .as("starter '" + id + "' is listed by all(), is a preset re-used, or is derived from the folder — exactly one")
                    .isEqualTo(1);
        }
        assertThat(StarterRacks.presetBehind("lamp")).contains(RackPresets.LAMP_BENCH);
        assertThat(StarterRacks.presetBehind("classic")).contains(RackPresets.CLASSIC_WEB);
        // a listed starter serializes to the very patch forKind's twin does: one wiring, two doors
        StarterRacks.Starter polyglot = StarterRacks.all().stream().filter(s -> s.id().equals("polyglot"))
                .findFirst().orElseThrow();
        assertThat(wiring(polyglot.buildPatch()))
                .isEqualTo(wiring(StarterRacks.forKind(ProjectKind.RUST, tmp.toFile()).orElseThrow().buildPatch()));
    }

    /**
     * A patch with its device ids taken out (3.4): every device is minted a
     * fresh id when it is built, so two builds of one wiring differ in
     * exactly those and in nothing a wiring is made of.
     */
    private static String wiring(org.json.JSONObject patch) {
        org.json.JSONObject copy = new org.json.JSONObject(patch.toString());
        for (Object d : copy.optJSONArray("devices")) {
            ((org.json.JSONObject) d).remove("id");
        }
        for (Object c : copy.optJSONArray("cables")) {
            ((org.json.JSONObject) c).remove("fromId");
            ((org.json.JSONObject) c).remove("toId");
        }
        return copy.toString();
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

        Path workspace = Files.createDirectory(tmp.resolve("NMOX"));
        Files.writeString(workspace.resolve("README.md"), "# NMOX\n");
        assertThat(StarterRacks.forProject(workspace.toFile()))
                .as("the fresh ~/NMOX workspace, a README alone: still the bare rack")
                .isEmpty();
    }

    @Test
    @DisplayName("the wizard's templates and the aim path read the SAME wirings — one home, no drift")
    void templatesShareTheWirings() throws Exception {
        // the shared builders are one-liners over StarterRacks; a template that
        // re-inlined its rack would be a second home the aim path could not see
        // comments stripped: a re-inlined rack under a comment naming the
        // shared wiring satisfied this gate on the 2026-09-17 arc review
        String src = org.nmox.studio.rack.GateSources.stripComments(
                Files.readString(Path.of("src/main/java/org/nmox/studio/rack/projectstudio/ProjectTemplates.java"))
                        .replace("\r\n", "\n"));
        for (String wiring : List.of("POLYGLOT", "VITE", "ANGULAR", "EXPRESS", "STATIC", "ELIXIR")) {
            assertThat(src).as("templates read StarterRacks." + wiring).contains("buildPatchFrom(StarterRacks." + wiring + ")");
        }
        assertThat(src.split("rack\\.connect\\(").length - 1)
                .as("only TS_LIBRARY keeps a wiring of its own (tsc + vitest, no aim-path twin)")
                .isEqualTo(6);
        // the outcome, not the mechanism: a rack built by any helper is still
        // a lambda handed to buildPatchFrom — exactly one such lambda exists
        assertThat(src.split("buildPatchFrom\\(rack ->").length - 1)
                .as("inline rack lambdas in the templates: TS_LIBRARY's and no other")
                .isEqualTo(1);
    }
}
