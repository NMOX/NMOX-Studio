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
     * comment, and so does Knockout since 3.5.3 (the official release
     * asset; the 3.5.1 file was cdnjs's own re-minification, which has
     * no banner); the banner-less minified builds are pinned by the
     * version assignment their code carries (Backbone's
     * {@code VERSION="1.6.1"} at ~offset 500) — which is why the whole
     * file is scanned (Alpine\u2019s version property sits past 21 KB).
     */
    private static final Map<String, List<String>> MARKERS = Map.of(
            "jquery-3.7.1.min.js", List.of("jQuery v3.7.1"),
            "mootools-core-1.6.0-compat.min.js", List.of("MooTools", "1.6.0"),
            "prototype-1.7.3.js", List.of("Prototype JavaScript framework", "1.7.3"),
            "backbone-1.6.1.min.js", List.of("Backbone", "VERSION=\"1.6.1\""),
            "underscore-1.13.8.min.js", List.of("underscore", "1.13.8"),
            "knockout-3.5.3.js", List.of("Knockout JavaScript library v3.5.3", "version=\"3.5.3\""),
            // v1.92.1 (review find): the two v1.92.0 additions joined the
            // tripwire — both carry a runtime version property
            "alpinejs-3.17.5.min.js", List.of("version:\"3.17.5\""),
            "htmx-2.0.11.min.js", List.of("version:\"2.0.11\""));

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

    /**
     * The fetch record is a claim about bytes, and until 2026-11-13 no
     * test read it: a file replaced without its row (or a row edited
     * without its file) passed, because the marker tripwire above only
     * asks for a version string. Every row is now recomputed.
     */
    @Test
    @DisplayName("Every bundled build has the byte count and SHA-256 its NOTICE row records")
    void bytesMatchTheFetchRecord() throws Exception {
        String text;
        try (var in = ClassicKit.class.getResourceAsStream("vendor/NOTICE-vendor.md")) {
            assertThat(in).as("NOTICE-vendor.md is bundled").isNotNull();
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        java.util.regex.Matcher row = java.util.regex.Pattern
                .compile("(?m)^\\| (\\S+\\.js)[^|]*\\| https://\\S+ \\| (\\d+) \\| ([0-9a-f]{64}) \\|\\s*$")
                .matcher(text.replace("\r\n", "\n"));
        java.util.Set<String> recorded = new java.util.TreeSet<>();
        while (row.find()) {
            String file = row.group(1);
            byte[] bytes = ClassicKit.vendorBytes(file);
            assertThat(bytes.length).as(file + " byte count").isEqualTo(Integer.parseInt(row.group(2)));
            assertThat(java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
                    .as(file + " SHA-256").isEqualTo(row.group(3));
            recorded.add(file);
        }
        assertThat(recorded)
                .as("the fetch record covers exactly the files the tripwire names")
                .containsExactlyInAnyOrderElementsOf(MARKERS.keySet());
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
