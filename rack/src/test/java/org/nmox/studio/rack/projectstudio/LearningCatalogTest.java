package org.nmox.studio.rack.projectstudio;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The learning catalog, held to its promises: it loads, covers the top
 * languages/frameworks/libraries, and every entry is internally
 * consistent — a REPL space has a launchable command, a run space has
 * a run command, and Common Lisp (the worked example) is present and
 * correct.
 */
class LearningCatalogTest {

    @Test
    @DisplayName("The bundled catalog loads a broad, well-formed set of spaces")
    void catalogLoads() {
        List<LearningCatalog.Space> all = LearningCatalog.all();
        assertThat(all).hasSizeGreaterThanOrEqualTo(50);
        assertThat(all).extracting(LearningCatalog.Space::slug).doesNotHaveDuplicates();
        assertThat(LearningCatalog.byCategory(LearningCatalog.Category.LANGUAGE)).isNotEmpty();
        assertThat(LearningCatalog.byCategory(LearningCatalog.Category.FRAMEWORK)).isNotEmpty();
        assertThat(LearningCatalog.byCategory(LearningCatalog.Category.LIBRARY)).isNotEmpty();
    }

    @Test
    @DisplayName("Every space can actually be launched: command, files, tutorial")
    void everySpaceIsLaunchable() {
        for (LearningCatalog.Space s : LearningCatalog.all()) {
            assertThat(s.driver().command())
                    .as("%s has a non-empty launch command", s.slug()).isNotEmpty();
            assertThat(s.files())
                    .as("%s ships at least one sample file", s.slug()).isNotEmpty();
            assertThat(s.tutorial())
                    .as("%s has a tutorial", s.slug()).isNotBlank();
            if (s.driver().kind() == LearningCatalog.DriverKind.REPL) {
                assertThat(s.driver().prompt())
                        .as("%s is a REPL and names its prompt", s.slug()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("Common Lisp is the worked example: clisp REPL, real snippets")
    void commonLispIsPresent() {
        LearningCatalog.Space lisp = LearningCatalog.find("lisp-clisp");
        assertThat(lisp).isNotNull();
        assertThat(lisp.driver().kind()).isEqualTo(LearningCatalog.DriverKind.REPL);
        assertThat(lisp.driver().command()).containsExactly("clisp");
        assertThat(lisp.driver().snippets()).anyMatch(s -> s.contains("(defun square"));
        assertThat(lisp.install()).containsKeys("mac", "linux", "windows");
    }

    @Test
    @DisplayName("Solidity rides chisel: REPL driver, Counter.sol sample, all three OS installs")
    void solidityIsPresent() {
        LearningCatalog.Space sol = LearningCatalog.find("solidity");
        assertThat(sol).isNotNull();
        assertThat(sol.driver().kind()).isEqualTo(LearningCatalog.DriverKind.REPL);
        assertThat(sol.driver().command()).containsExactly("chisel");
        assertThat(sol.driver().prompt()).isEqualTo("➜");
        assertThat(sol.driver().snippets()).anyMatch(s -> s.contains("keccak256"));
        assertThat(sol.install()).containsKeys("mac", "linux", "windows");
        sol.install().forEach((os, cmd) ->
                assertThat(cmd).as("install for %s names foundry", os).contains("foundry"));
        assertThat(sol.files()).anyMatch(f -> f.path().equals("Counter.sol"));
        assertThat(sol.tutorial()).contains("chisel");
    }

    @Test
    @DisplayName("Every REPL driver names a launchable command token")
    void replDriversNameACommand() {
        for (LearningCatalog.Space s : LearningCatalog.all()) {
            if (s.driver().kind() == LearningCatalog.DriverKind.REPL) {
                assertThat(s.driver().command())
                        .as("%s repl has a command", s.slug()).isNotEmpty();
                assertThat(s.driver().command().get(0))
                        .as("%s repl command token is non-blank", s.slug()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("The well-known interpreters carry install commands for all three OSes")
    void wellKnownInterpretersCarryInstallCommands() {
        for (String slug : List.of("lisp-clisp", "python", "javascript-node")) {
            LearningCatalog.Space space = LearningCatalog.find(slug);
            assertThat(space).as("%s is in the catalog", slug).isNotNull();
            assertThat(space.install())
                    .as("%s install map covers mac+linux+windows", slug)
                    .containsKeys("mac", "linux", "windows");
            space.install().forEach((os, cmd) -> assertThat(cmd)
                    .as("%s install for %s is non-blank", slug, os).isNotBlank());
        }
    }

    @Test
    @DisplayName("The catalog is immutable: no caller can corrupt the shared cache")
    void catalogIsImmutable() {
        List<LearningCatalog.Space> all = LearningCatalog.all();
        LearningCatalog.Space first = all.get(0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> all.add(first))
                .as("all() add").isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> all.remove(0))
                .as("all() remove").isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> first.files().clear())
                .as("space files").isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> first.driver().command().add("x"))
                .as("driver command").isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> first.driver().snippets().clear())
                .as("driver snippets").isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> first.install().put("os", "cmd"))
                .as("install map").isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("parse tolerates unknown/missing fields without throwing")
    void parseIsLenient() {
        org.json.JSONObject root = new org.json.JSONObject(
                "{\"spaces\":[{\"slug\":\"x\",\"name\":\"X\",\"category\":\"language\","
                + "\"driver\":{\"kind\":\"repl\",\"command\":[\"x\"]}}]}");
        List<LearningCatalog.Space> spaces = LearningCatalog.parse(root);
        assertThat(spaces).hasSize(1);
        assertThat(spaces.get(0).files()).isEmpty();
        assertThat(spaces.get(0).driver().snippets()).isEmpty();
    }

    @Test
    @DisplayName("a space whose tutorial sends the learner to a test lane ships a test for it to run")
    void aNamedTestLaneHasATest() {
        // 3.13.0 found the Gleam tutorial calling VERITAS "already wired" in a space with no
        // test: `gleam test` exited 1. Proven with gleam 1.19.2 on 2026-11-19: 1 passed.
        LearningCatalog.Space gleam = LearningCatalog.find("gleam");
        assertThat(gleam.tutorial()).contains("VERITAS (`gleam test`");
        assertThat(gleam.files()).extracting(LearningCatalog.SampleFile::path)
                .contains("test/hello_test.gleam");
        assertThat(gleam.files()).filteredOn(f -> f.path().equals("gleam.toml")).singleElement()
                .satisfies(f -> assertThat(f.content()).contains("[dev-dependencies]").contains("gleeunit"));
        assertThat(gleam.files()).filteredOn(f -> f.path().equals("test/hello_test.gleam")).singleElement()
                .satisfies(f -> assertThat(f.content()).contains("hello.squares("));
        assertThat(gleam.files()).filteredOn(f -> f.path().equals("src/hello.gleam")).singleElement()
                .satisfies(f -> assertThat(f.content()).contains("pub fn squares("));
    }

    @Test
    @DisplayName("the Playwright space installs the one browser its tests use, and says what the download costs")
    void playwrightInstallsWhatItUses() {
        LearningCatalog.Space space = LearningCatalog.find("playwright");
        assertThat(LearningSpace.installHint(space))
                .as("no `projects` in the space's config, so Chromium alone runs: Firefox and WebKit were downloaded for nothing")
                .endsWith("npx playwright install chromium");
        assertThat(space.tutorial()).contains("npx playwright install chromium").contains("about 600 MB");
        assertThat(space.files()).filteredOn(f -> f.path().equals("playwright.config.ts")).singleElement()
                .satisfies(f -> assertThat(f.content())
                        .as("once the config names projects, the install line must name their browsers")
                        .doesNotContain("projects"));
    }
}
