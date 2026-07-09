package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.service.ServingRegistry;
import org.nmox.studio.rack.service.ServingRegistry.Kind;
import org.nmox.studio.rack.service.ServingRegistry.Serving;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serve devices and the serving registry: registration rides the exact
 * URL-announce moment, the exit path deregisters (so STOP — which ends
 * in onFinished via the process's exit code — always cleans up), and
 * the extracted {@link ServeUrls} scan behaves byte-identically to the
 * four patterns it replaced.
 */
class ServingDevicesTest {

    @TempDir
    Path projectDir;

    private Rack aimedRack() throws IOException {
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        return rack;
    }

    /** This project's servings only — the registry is a JVM singleton. */
    private List<Serving> mine() {
        File dir = projectDir.toFile();
        return ServingRegistry.getDefault().snapshot().stream()
                .filter(s -> s.projectDir().equals(dir)).toList();
    }

    @Test
    @DisplayName("NEXUS registers on its URL announce; stop (onFinished) deregisters")
    void nexusRegistersAndStopDeregisters() throws IOException {
        Rack rack = aimedRack();
        try {
            NextDevice next = new NextDevice();
            rack.addDevice(next);
            next.onLine("  ▲ Next.js  - Local: http://localhost:3000");
            assertThat(mine()).extracting(Serving::url, Serving::kind)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(
                            "http://localhost:3000", Kind.WEB));

            next.onFinished(143); // the STOP button's SIGTERM exit
            assertThat(mine()).as("stop deregisters").isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("SURGE registers its knob URL on first output, then replaces it with the printed one")
    void surgeReplacesKnobUrlWithPrinted() throws IOException {
        Rack rack = aimedRack();
        try {
            DevServerDevice surge = new DevServerDevice();
            rack.addDevice(surge);
            surge.onLine("VITE v5  ready in 300 ms");
            assertThat(mine()).extracting(Serving::url)
                    .containsExactly("http://localhost:5173"); // the PORT knob default

            surge.onLine("  ➜  Local:   http://localhost:5175/");
            assertThat(mine()).as("one serving per device, real URL wins")
                    .extracting(Serving::url).containsExactly("http://localhost:5175/");

            surge.onFinished(0);
            assertThat(mine()).isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ANVIL registers its chain as CHAIN with the announced address")
    void anvilRegistersChain() throws IOException {
        Rack rack = aimedRack();
        try {
            AnvilDevice anvil = new AnvilDevice();
            rack.addDevice(anvil);
            anvil.onLine("Listening on 127.0.0.1:8545");
            assertThat(mine()).extracting(Serving::url, Serving::kind, Serving::deviceTitle)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(
                            "http://127.0.0.1:8545", Kind.CHAIN, "ANVIL"));

            anvil.onFinished(130);
            assertThat(mine()).isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("IGNITION's static lane registers python's http.server; exit deregisters")
    void ignitionStaticLaneRegisters() throws IOException {
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.onLine("Serving HTTP on :: port 8000 (http://[::]:8000/) ...");
            assertThat(mine()).extracting(Serving::url)
                    .containsExactly("http://localhost:8000");

            run.onFinished(0);
            assertThat(mine()).isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("the static lane runs python unbuffered, or its banner never reaches onLine")
    void ignitionStaticLaneIsUnbuffered() throws IOException {
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.selectTargetForTest("static");

            List<String> cmd = run.buildCommandForTest();

            // python block-buffers stdout when it is not a TTY, and the
            // "Serving HTTP on" banner is a stdout print(). Without -u it
            // sits in the buffer: the lane serves, the access log (stderr)
            // scrolls, and READY/URL/serving-chip never fire. Observed live.
            assertThat(cmd).containsExactly(
                    "python3", "-u", "-m", "http.server", "8000");
            assertThat(cmd.indexOf("-u")).isLessThan(cmd.indexOf("-m"));
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("IGNITION's webpack lane announces the printed dev-server URL once")
    void ignitionWebpackLaneRegisters() throws IOException {
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.webpackLane = true; // what primaryAction sets for `npx webpack serve`
            run.onLine("<i> [webpack-dev-server] Project is running at:");
            assertThat(mine()).as("no URL on that line yet").isEmpty();

            run.onLine("<i> [webpack-dev-server] Loopback: http://localhost:8080/");
            assertThat(mine()).extracting(Serving::url)
                    .containsExactly("http://localhost:8080/");

            // announce-once: a second printed URL does not re-register
            run.onLine("<i> On Your Network (IPv4): http://localhost:8081/");
            assertThat(mine()).extracting(Serving::url)
                    .containsExactly("http://localhost:8080/");

            run.onFinished(0);
            assertThat(mine()).isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("IGNITION's php lane registers its pinned address on the Development Server banner")
    void ignitionPhpLaneRegisters() throws IOException {
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.phpLane = true; // what primaryAction sets for `php -S 127.0.0.1:8000`
            run.onLine("[Sat Jul  4 12:00:00 2026] PHP 8.3.8 Development Server (http://127.0.0.1:8000) started");
            assertThat(mine()).extracting(Serving::url, Serving::kind)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(
                            "http://127.0.0.1:8000", Kind.WEB));

            // announce-once: request-log lines and repeated banners don't re-register
            run.onLine("[Sat Jul  4 12:00:05 2026] 127.0.0.1:52114 [200]: GET /");
            assertThat(mine()).hasSize(1);

            run.onFinished(143); // the STOP button's SIGTERM exit
            assertThat(mine()).as("stop deregisters").isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("IGNITION's other lanes never register (no URL is ever announced)")
    void ignitionPlainLaneSilent() throws IOException {
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.onLine("go: downloading github.com/x/y v1.2.3");
            run.onLine("listening logs mention http://localhost:9999 sometimes");
            // php's banner in some other lane's log: the phpLane gate holds
            run.onLine("PHP 8.3.8 Development Server (http://127.0.0.1:8000) started");
            assertThat(mine()).as("non-serving lanes stay out of the registry").isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- ServeUrls: the extracted common scan ----------------

    @Test
    @DisplayName("ServeUrls pins the shared pattern: localhost/127.0.0.1 with port, first match")
    void serveUrlsPins() {
        assertThat(ServeUrls.firstLocalUrl("  ➜  Local:   http://localhost:5173/"))
                .isEqualTo("http://localhost:5173/");
        assertThat(ServeUrls.firstLocalUrl("started server on http://127.0.0.1:3000"))
                .isEqualTo("http://127.0.0.1:3000");
        assertThat(ServeUrls.firstLocalUrl("https://localhost:8443/app up"))
                .isEqualTo("https://localhost:8443/app");
        assertThat(ServeUrls.firstLocalUrl("compiled successfully")).isNull();
        assertThat(ServeUrls.firstLocalUrl("visit http://example.com:80/"))
                .as("only local hosts").isNull();
        // WHY ARTISAN keeps its own pattern: the shared scan would drag
        // artisan's closing bracket and period into the URL
        assertThat(ServeUrls.firstLocalUrl("Server running on [http://127.0.0.1:8000]."))
                .isEqualTo("http://127.0.0.1:8000].");
    }
}
