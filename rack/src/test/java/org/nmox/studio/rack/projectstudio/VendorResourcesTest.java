package org.nmox.studio.rack.projectstudio;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bundled classic-library builds against accidental corruption
 * or substitution: each file the Classic Kit ships must exist, be
 * plausibly sized, and carry its library's own name/version marker in
 * the opening bytes. The full provenance (source URL, SHA-256, byte
 * count) lives in NOTICE-vendor.md beside the files; this test is the
 * cheap always-on tripwire in front of it.
 */
class VendorResourcesTest {

    /**
     * Name/version markers, all within the first 2 KB of the real
     * builds. jQuery/MooTools/Prototype announce themselves in a banner
     * comment; the banner-less minified builds are pinned by the
     * version assignment their code carries (Backbone's
     * {@code VERSION="1.6.0"} at ~offset 500, Knockout's
     * {@code version="3.5.1"} at ~offset 1060) — which is why the whole
     * file is scanned (Alpine\u2019s version property sits past 21 KB).
     */
    private static final Map<String, List<String>> MARKERS = Map.of(
            "jquery-3.7.1.min.js", List.of("jQuery v3.7.1"),
            "mootools-core-1.6.0-compat.min.js", List.of("MooTools", "1.6.0"),
            "prototype-1.7.3.js", List.of("Prototype JavaScript framework", "1.7.3"),
            "backbone-1.6.0.min.js", List.of("Backbone", "VERSION=\"1.6.0\""),
            "underscore-1.13.7.min.js", List.of("underscore", "1.13.7"),
            "knockout-3.5.1.js", List.of("version=\"3.5.1\""),
            // v1.92.1 (review find): the two v1.92.0 additions joined the
            // tripwire — both carry a runtime version property
            "alpinejs-3.14.9.min.js", List.of("version:\"3.14.9\""),
            "htmx-2.0.4.min.js", List.of("version:\"2.0.4\""));

    private static final int MIN_PLAUSIBLE_BYTES = 15_000; // smallest real build (underscore ~19 KB)

    @Test
    @DisplayName("Every library in the catalog (plus Underscore) has a bundled build")
    void everyCatalogEntryIsBundled() throws Exception {
        for (ClassicKit.Lib lib : ClassicKit.libraries()) {
            assertThat(ClassicKit.vendorBytes(lib.vendorFile()))
                    .as(lib.id()).isNotEmpty();
        }
        assertThat(ClassicKit.vendorBytes(ClassicKit.underscore().vendorFile())).isNotEmpty();
    }

    @Test
    @DisplayName("Each bundled build is plausibly sized and opens with its own marker")
    void bundledBuildsCarryTheirMarkers() throws Exception {
        for (Map.Entry<String, List<String>> pin : MARKERS.entrySet()) {
            byte[] bytes = ClassicKit.vendorBytes(pin.getKey());
            assertThat(bytes.length)
                    .as(pin.getKey() + " is a real build, not a stub or error page")
                    .isGreaterThan(MIN_PLAUSIBLE_BYTES);
            String head = new String(bytes, 0, bytes.length,
                    StandardCharsets.UTF_8);
            for (String marker : pin.getValue()) {
                assertThat(head).as(pin.getKey() + " carries " + marker).contains(marker);
            }
        }
    }

    @Test
    @DisplayName("The NOTICE rides beside the files and names every one of them")
    void noticeCoversEveryFile() throws Exception {
        byte[] notice;
        try (var in = ClassicKit.class.getResourceAsStream("vendor/NOTICE-vendor.md")) {
            assertThat(in).as("NOTICE-vendor.md is bundled").isNotNull();
            notice = in.readAllBytes();
        }
        String text = new String(notice, StandardCharsets.UTF_8);
        for (String file : MARKERS.keySet()) {
            assertThat(text).as("NOTICE entry for " + file).contains(file);
        }
        assertThat(text).contains("SHA-256").contains("MIT");
    }

    @Test
    @DisplayName("jQuery 1.x is NOT bundled — the kit's whole point is the supported line")
    void noEolJqueryBundled() {
        assertThat(ClassicKit.class.getResource("vendor/jquery-1.12.4.min.js")).isNull();
        assertThat(ClassicKit.libraries())
                .filteredOn(lib -> "jquery".equals(lib.id()))
                .singleElement()
                .satisfies(lib -> assertThat(lib.version()).startsWith("3."));
    }
}
