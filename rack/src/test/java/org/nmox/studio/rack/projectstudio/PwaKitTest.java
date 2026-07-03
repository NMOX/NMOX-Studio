package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The PWA Kit, held to its claims: an installability-complete
 * manifest, a service worker that precaches what the project actually
 * has, idempotent head wiring, and the never-overwrite contract.
 */
class PwaKitTest {

    private static PwaKit.Options options(File artwork, boolean everything) {
        return new PwaKit.Options("Demo App", "A Longer Short Name", "#112233",
                "#0a0a0a", "DA", artwork, PwaKit.Strategy.APP_SHELL,
                everything, everything, everything, everything);
    }

    @Test
    @DisplayName("Manifest is installability-complete: both icon purposes, scope, colors")
    void manifestComplete() {
        JSONObject m = new JSONObject(PwaKit.manifest(options(null, true)));
        assertThat(m.getString("name")).isEqualTo("Demo App");
        assertThat(m.getString("short_name")).hasSize(12);
        assertThat(m.getString("start_url")).isEqualTo("/");
        assertThat(m.getString("scope")).isEqualTo("/");
        assertThat(m.getString("display")).isEqualTo("standalone");
        assertThat(m.getString("theme_color")).isEqualTo("#112233");
        assertThat(m.getJSONArray("icons")).hasSize(4);
        long maskable = m.getJSONArray("icons").toList().stream()
                .map(o -> ((java.util.Map<?, ?>) o).get("purpose"))
                .filter("maskable"::equals).count();
        assertThat(maskable).isEqualTo(2);
    }

    @Test
    @DisplayName("App-shell worker is cache-first with an offline navigation fallback")
    void appShellWorker() {
        String sw = PwaKit.serviceWorker(List.of("/", "/index.html"), PwaKit.Strategy.APP_SHELL);
        assertThat(sw).contains("const CACHE = 'nmox-pwa-v1'");
        assertThat(sw).contains("'/index.html',");
        assertThat(sw).contains("cache.addAll(PRECACHE)");
        assertThat(sw).contains("skipWaiting");
        assertThat(sw).contains("clients.claim");
        assertThat(sw).contains("caches.match('/offline.html')");
        assertThat(sw.indexOf("caches.match(request)"))
                .as("cache consulted before the network")
                .isLessThan(sw.indexOf("fetch(request)"));
    }

    @Test
    @DisplayName("Network-first worker hits the network before the cache")
    void networkFirstWorker() {
        String sw = PwaKit.serviceWorker(List.of("/"), PwaKit.Strategy.NETWORK_FIRST);
        assertThat(sw).contains("Network first");
        assertThat(sw.indexOf("fetch(request)"))
                .as("network consulted before the cache")
                .isLessThan(sw.indexOf("caches.match(request)"));
    }

    @Test
    @DisplayName("Precache list is built from the files the project actually has")
    void precacheFromRealFiles(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("index.html"), "<html></html>");
        Files.writeString(tmp.resolve("style.css"), "body{}");
        Files.writeString(tmp.resolve("main.js"), "//");
        Files.writeString(tmp.resolve("sw.js"), "// never precaches itself");
        Files.writeString(tmp.resolve(".env"), "SECRET=1");
        Files.writeString(tmp.resolve("eslint.config.mjs"), "// dev-time, not app content");
        Files.createDirectory(tmp.resolve("src"));

        List<String> list = PwaKit.precacheList(tmp.toFile(), options(null, true));
        assertThat(list).startsWith("/")
                .contains("/index.html", "/style.css", "/main.js",
                        "/offline.html", "/site.webmanifest", "/icon-192.png", "/icon-512.png")
                .doesNotContain("/sw.js", "/.env", "/src", "/eslint.config.mjs");
        assertThat(list).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Head wiring inserts every missing piece before </head>, once")
    void wiringIsIdempotent() {
        String html = "<html><head>\n<title>x</title>\n</head><body></body></html>";
        String wired = PwaKit.wireHead(html, options(null, true));
        assertThat(wired).contains("rel=\"manifest\"")
                .contains("name=\"theme-color\"")
                .contains("apple-touch-icon")
                .contains("serviceWorker")
                .contains("navigator.serviceWorker.register('/sw.js')");
        assertThat(wired.indexOf("rel=\"manifest\""))
                .isLessThan(wired.toLowerCase().indexOf("</head>"));
        assertThat(PwaKit.wireHead(wired, options(null, true)))
                .as("second pass changes nothing").isEqualTo(wired);
    }

    @Test
    @DisplayName("Head wiring adds only what is missing and survives a missing </head>")
    void wiringOnlyAddsMissing() {
        String html = "<html><head><meta name=\"theme-color\" content=\"#fff\"></head></html>";
        String wired = PwaKit.wireHead(html, options(null, true));
        assertThat(wired).containsOnlyOnce("theme-color");
        assertThat(wired).contains("rel=\"manifest\"");
        String headless = "<html><body>no head here</body></html>";
        assertThat(PwaKit.wireHead(headless, options(null, true))).isEqualTo(headless);
    }

    @Test
    @DisplayName("Offline page carries the app's name and theme color")
    void offlinePage() {
        String page = PwaKit.offlineHtml(options(null, true));
        assertThat(page).contains("Demo App").contains("#112233")
                .contains("<!doctype html>");
    }

    @Test
    @DisplayName("A full run writes everything; a second run overwrites nothing")
    void neverOverwrite(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("index.html"),
                "<html><head><title>d</title></head><body></body></html>");

        List<PwaKit.Outcome> first = PwaKit.write(tmp.toFile(), options(null, true));
        assertThat(first).hasSize(9);
        assertThat(first).allMatch(PwaKit.Outcome::written);
        assertThat(ImageIO.read(tmp.resolve("icon-512.png").toFile()).getWidth())
                .isEqualTo(512);
        assertThat(ImageIO.read(tmp.resolve("apple-touch-icon.png").toFile()).getWidth())
                .isEqualTo(180);
        assertThat(tmp.resolve("sw.js")).exists();
        assertThat(tmp.resolve("offline.html")).exists();
        String indexAfter = Files.readString(tmp.resolve("index.html"));
        assertThat(indexAfter).contains("serviceWorker");

        List<PwaKit.Outcome> second = PwaKit.write(tmp.toFile(), options(null, true));
        assertThat(second).noneMatch(PwaKit.Outcome::written);
        assertThat(second).extracting(PwaKit.Outcome::status)
                .contains("already exists, untouched", "already wired");
        assertThat(Files.readString(tmp.resolve("index.html")))
                .as("wiring untouched on the second pass").isEqualTo(indexAfter);
    }

    @Test
    @DisplayName("Hostile wizard input is defused: colors fall back, names are escaped")
    void hostileInputsAreDefused() {
        PwaKit.Options evil = new PwaKit.Options("<script>alert(1)</script>", "x",
                "\"><script>steal()</script>", "#000", "X", null,
                PwaKit.Strategy.APP_SHELL, true, true, true, true);
        assertThat(PwaKit.offlineHtml(evil))
                .doesNotContain("<script>alert").contains("&lt;script&gt;");
        String wired = PwaKit.wireHead("<html><head></head></html>", evil);
        assertThat(wired).doesNotContain("steal()");
        assertThat(wired).contains("content=\"#1a1a1e\"");
        assertThat(new JSONObject(PwaKit.manifest(evil)).getString("theme_color"))
                .isEqualTo("#1a1a1e");
        assertThat(PwaKit.safeColor("#ABC", "#000")).isEqualTo("#abc");
        assertThat(PwaKit.serviceWorker(List.of("/o'reilly.html"), PwaKit.Strategy.APP_SHELL))
                .as("quotes in filenames can't break the precache literal")
                .contains("'/o\\'reilly.html',");
    }

    @Test
    @DisplayName("Wiring without an index.html reports the skip honestly")
    void wiringWithoutIndex(@TempDir Path tmp) throws Exception {
        PwaKit.Options wireOnly = new PwaKit.Options("X", "X", "#000", "#000", "X",
                null, PwaKit.Strategy.APP_SHELL, false, false, false, true);
        List<PwaKit.Outcome> outcomes = PwaKit.write(tmp.toFile(), wireOnly);
        assertThat(outcomes).singleElement()
                .satisfies(o -> assertThat(o.status()).isEqualTo("skipped — no index.html"));
    }
}
