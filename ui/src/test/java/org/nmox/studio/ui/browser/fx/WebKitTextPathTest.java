package org.nmox.studio.ui.browser.fx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure half of switching WebKit's own text path on (v2.172.0): which
 * library is known, what the native glue appended, where the switch sits. The
 * native half is proven in the assembled app on the bundled runtime; CI's JavaFX
 * comes from Maven jars whose WebKit is another build, which is exactly the
 * case that must stay on the repaired simple path.
 */
class WebKitTextPathTest {

    // The table holds one entry per platform today, so a walk of it and a match on
    // the BYTES always coincide — and that is exactly why returning the walked entry
    // instead of the matched one survived review. An OpenJFX patch bump adds the
    // second entry, both naming the same jlink path, and then the walk reaches the
    // wrong one: its offsets inside the other build's image are an arbitrary call
    // and an arbitrary write. The selection is pinned over a two-entry table.
    @Test
    @DisplayName("with two builds for one platform, the entry that matches the library's BYTES wins — not the first one listed")
    void matchPicksTheBuildTheBytesName() {
        WebKitTextPath.Build first = new WebKitTextPath.Build("osx-aarch64", "jmods-a", "aaaa",
                "lib/libjfxwebkit.dylib", "anchor", 0x10L, 0x20L);
        WebKitTextPath.Build second = new WebKitTextPath.Build("osx-aarch64", "jmods-b", "bbbb",
                "lib/libjfxwebkit.dylib", "anchor", 0x30L, 0x40L);
        java.util.List<WebKitTextPath.Build> table = java.util.List.of(first, second);
        assertThat(WebKitTextPath.match(table, "osx-aarch64", "bbbb")).isSameAs(second);
        assertThat(WebKitTextPath.match(table, "osx-aarch64", "aaaa")).isSameAs(first);
        assertThat(WebKitTextPath.match(table, "osx-aarch64", "cccc")).isNull();
        assertThat(WebKitTextPath.match(table, "windows-x64", "aaaa"))
                .as("the hash alone never decides: the platform must match too").isNull();
    }

    // Both false cases used to be one bare `false`, so the caller could not tell
    // "nothing was written" from "WebKit is on its own path now" — and it repaired
    // the simple path over the second, which measures every complex run twice.
    @Test
    @DisplayName("only an untouched WebKit may have its simple path repaired")
    void onlyUntouchedIsRepairable() {
        assertThat(WebKitTextPath.repairable(WebKitTextPath.Switched.UNTOUCHED))
                .as("nothing was written: the repair is the whole point").isTrue();
        assertThat(WebKitTextPath.repairable(WebKitTextPath.Switched.ON))
                .as("WebKit shapes and measures for itself").isFalse();
        assertThat(WebKitTextPath.repairable(WebKitTextPath.Switched.UNCONFIRMED))
                .as("it may be shaping: repairing over it is worse than either path alone").isFalse();
    }

    @Test
    @DisplayName("the length the native glue appends is removed, whatever the string ends in")
    void lengthSuffixIsStripped() {
        assertThat(WebKitTextPath.stripLengthSuffix("\u0633\u0627\u06443")).isEqualTo("\u0633\u0627\u0644");
        assertThat(WebKitTextPath.stripLengthSuffix("\u06F1\u06F4\u06F0\u06F34")).isEqualTo("\u06F1\u06F4\u06F0\u06F3");
        assertThat(WebKitTextPath.stripLengthSuffix("ab124")).isEqualTo("ab12");       // text ending in digits
        assertThat(WebKitTextPath.stripLengthSuffix("x".repeat(12) + "12")).isEqualTo("x".repeat(12));
        assertThat(WebKitTextPath.stripLengthSuffix("x".repeat(100) + "100")).isEqualTo("x".repeat(100));
        assertThat(WebKitTextPath.stripLengthSuffix("0")).isEmpty();                  // an empty string
        assertThat(WebKitTextPath.stripLengthSuffix("d\u01B0\u0323\u0301n7")).isEqualTo("d\u01B0\u0323\u0301n7"); // 6 != 7: not a suffix
        assertThat(WebKitTextPath.stripLengthSuffix("abc")).isEqualTo("abc");
        assertThat(WebKitTextPath.stripLengthSuffix(null)).isNull();
    }

    @Test
    @DisplayName("platforms are named the way OpenJFX names its downloads")
    void platformNames() {
        assertThat(WebKitTextPath.platform("Mac OS X", "aarch64")).isEqualTo("osx-aarch64");
        assertThat(WebKitTextPath.platform("Mac OS X", "x86_64")).isEqualTo("osx-x64");
        assertThat(WebKitTextPath.platform("Windows 11", "amd64")).isEqualTo("windows-x64");
        assertThat(WebKitTextPath.platform("Linux", "amd64")).isEqualTo("linux-x64");
    }

    @Test
    @DisplayName("only an exact library on its own platform is known; Linux and anything else keep the simple path")
    void onlyExactBuildsMatch() {
        WebKitTextPath.Build mac = WebKitTextPath.KNOWN.get(0);
        assertThat(WebKitTextPath.match("osx-aarch64", mac.sha256())).isSameAs(mac);
        assertThat(WebKitTextPath.match("osx-aarch64", mac.sha256().toUpperCase(java.util.Locale.ROOT))).isSameAs(mac);
        assertThat(WebKitTextPath.match("windows-x64", mac.sha256())).isNull();
        assertThat(WebKitTextPath.match("osx-aarch64", "0".repeat(64))).isNull();
        assertThat(WebKitTextPath.KNOWN).extracting(WebKitTextPath.Build::platform)
                .containsExactly("osx-aarch64", "windows-x64");
        for (WebKitTextPath.Build build : WebKitTextPath.KNOWN) {
            assertThat(build.sha256()).matches("[0-9a-f]{64}");
            assertThat(build.jmods()).matches("[0-9a-f]{64}");
            assertThat(build.stateOffset()).isPositive();
        }
    }

    @Test
    @DisplayName("each library sits where jlink files that jmod's native library")
    void libraryPathsFollowJlink() {
        // jlink drops the jmod entry's first segment, then files a .dll under bin
        // (Windows) and everything else under lib: the Windows jmod carries
        // lib/javafx/jfxwebkit.dll, so the image carries bin/javafx/jfxwebkit.dll.
        // v2.172.0 looked in bin/jfxwebkit.dll and never found it.
        assertThat(WebKitTextPath.KNOWN.get(0).library()).isEqualTo("lib/libjfxwebkit.dylib");
        assertThat(WebKitTextPath.KNOWN.get(1).library()).isEqualTo("bin/javafx/jfxwebkit.dll");
    }

    @Test
    @DisplayName("the setter and state addresses are the measured offsets from the anchor, as read from the disassembly")
    void addressesFollowTheAnchor() {
        WebKitTextPath.Build mac = WebKitTextPath.KNOWN.get(0);
        long slide = 0x100000000L;
        long[] at = WebKitTextPath.addresses(mac, slide + 0xa938cL);
        assertThat(at[0]).isEqualTo(slide + 0x1c405acL);
        assertThat(at[1]).isEqualTo(slide + 0x675af58L);
        WebKitTextPath.Build win = WebKitTextPath.KNOWN.get(1);
        long[] w = WebKitTextPath.addresses(win, 0x7ff800a128f0L);
        assertThat(w[0]).isEqualTo(0x7ff800a128f0L);
        assertThat(w[1]).isEqualTo(0x7ff800a128f0L + 0x185a2db64L - 0x180a128f0L);
    }

    @Test
    @DisplayName("an unknown runtime is not a known build: no library, or a library of another build")
    void unknownRuntimesAreNotKnown() throws Exception {
        Path home = Files.createTempDirectory("nmox-jre");
        try {
            assertThat(WebKitTextPath.knownBuild(home)).isNull();
            Path lib = home.resolve("lib").resolve("libjfxwebkit.dylib");
            Files.createDirectories(lib.getParent());
            Files.writeString(lib, "not WebKit");
            Path bin = home.resolve("bin").resolve("javafx").resolve("jfxwebkit.dll");
            Files.createDirectories(bin.getParent());
            Files.writeString(bin, "not WebKit");
            assertThat(WebKitTextPath.knownBuild(home)).isNull();
            assertThat(WebKitTextPath.sha256(lib)).isEqualTo(
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest("not WebKit".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        } finally {
            try (var walk = Files.walk(home)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    @DisplayName("each known build names the jmods archive the release lanes pin, so a JavaFX bump re-measures the offsets")
    void knownBuildsFollowTheReleasePins() throws Exception {
        String script = Files.readString(Path.of("..", "packaging", "tools", "bundle-jre.sh"));
        Matcher mac = Pattern.compile("Darwin-arm64\\)\\s+FX_PLATFORM=\"osx-aarch64\"\\s+FX_SHA256=\"([0-9a-f]{64})\"").matcher(script);
        assertThat(mac.find()).as("bundle-jre.sh pins the osx-aarch64 jmods").isTrue();
        String workflow = Files.readString(Path.of("..", ".github", "workflows", "release.yml"));
        Matcher win = Pattern.compile("\\$fxSha256 = \"([0-9a-f]{64})\"").matcher(workflow);
        assertThat(win.find()).as("release.yml pins the windows-x64 jmods").isTrue();
        assertThat(WebKitTextPath.KNOWN.get(0).jmods()).as("osx-aarch64 offsets measured on the pinned jmods").isEqualTo(mac.group(1));
        assertThat(WebKitTextPath.KNOWN.get(1).jmods()).as("windows-x64 offsets measured on the pinned jmods").isEqualTo(win.group(1));
    }
}
