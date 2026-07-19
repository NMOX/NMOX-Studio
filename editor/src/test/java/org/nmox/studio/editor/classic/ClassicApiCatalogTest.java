package org.nmox.studio.editor.classic;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.classic.ClassicApiCatalog.Entry;
import org.nmox.studio.editor.classic.ClassicApiCatalog.Library;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classic catalog is data, and data rots silently: a mangled JSON
 * edit would disable completion for a whole library with no compile
 * error. These pin that the bundle loads, every library keeps its
 * documented coverage floor, and the entry shapes stay what the matcher
 * and items expect.
 */
class ClassicApiCatalogTest {

    @Test
    @DisplayName("Catalog loads all six classic libraries")
    void librariesPresent() {
        Map<String, Library> libs = ClassicApiCatalog.libraries();
        assertThat(libs.keySet()).containsExactlyInAnyOrder(
                "jquery", "mootools", "prototype", "backbone", "underscore", "knockout",
                "alpine", "htmx");
    }

    @Test
    @DisplayName("Every library keeps its coverage floor")
    void coverageFloors() {
        assertThat(ClassicApiCatalog.library("jquery").entries()).hasSizeGreaterThanOrEqualTo(80);
        assertThat(ClassicApiCatalog.library("mootools").entries()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(ClassicApiCatalog.library("prototype").entries()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(ClassicApiCatalog.library("backbone").entries()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(ClassicApiCatalog.library("underscore").entries()).hasSizeGreaterThanOrEqualTo(50);
        assertThat(ClassicApiCatalog.library("knockout").entries()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(ClassicApiCatalog.library("alpine").entries()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(ClassicApiCatalog.library("htmx").entries()).hasSizeGreaterThanOrEqualTo(12);
    }

    @Test
    @DisplayName("Every entry has a non-blank name and signature; names are unique per library")
    void entryShapes() {
        for (Library lib : ClassicApiCatalog.libraries().values()) {
            assertThat(lib.display()).as(lib.id() + " display").isNotBlank();
            Set<String> seen = new HashSet<>();
            for (Entry e : lib.entries()) {
                assertThat(e.name()).as(lib.id() + " entry name").isNotBlank();
                assertThat(e.sig()).as(lib.id() + "/" + e.name() + " sig").isNotBlank();
                assertThat(seen.add(e.name())).as(lib.id() + " duplicate " + e.name()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("The headline signatures read like the library docs")
    void headlineSignatures() {
        assertThat(sigOf("jquery", "$.ajax")).isEqualTo("$.ajax(url[, settings])");
        assertThat(sigOf("jquery", ".addClass")).isEqualTo(".addClass(className)");
        assertThat(sigOf("underscore", "_.debounce")).isEqualTo("_.debounce(function, wait[, immediate])");
        assertThat(sigOf("knockout", "ko.observable")).isEqualTo("ko.observable([initialValue])");
        assertThat(sigOf("backbone", "Backbone.Model.extend"))
                .isEqualTo("Backbone.Model.extend(properties[, classProperties])");
        assertThat(sigOf("mootools", "$$")).isEqualTo("$$(selector)");
        assertThat(sigOf("prototype", "Ajax.Request")).isEqualTo("new Ajax.Request(url[, options])");
    }

    @Test
    @DisplayName("An unknown library id is null, not an exception")
    void unknownLibrary() {
        assertThat(ClassicApiCatalog.library("dojo")).isNull();
    }

    private static String sigOf(String lib, String name) {
        return ClassicApiCatalog.library(lib).entries().stream()
                .filter(e -> e.name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError(lib + " lacks " + name))
                .sig();
    }
}
