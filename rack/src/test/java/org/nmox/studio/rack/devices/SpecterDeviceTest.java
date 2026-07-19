package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPECTER, the E2E console: engine resolution follows the project's own
 * config then its dependencies, every verb speaks the resolved engine's
 * real command, and verbs an engine doesn't have return null so the
 * device greys honestly instead of running the wrong thing.
 */
class SpecterDeviceTest {

    @TempDir
    Path dir;

    private int writes;

    private void deps(String json) throws IOException {
        Path pkg = dir.resolve("package.json");
        Files.writeString(pkg, "{\"devDependencies\": " + json + "}");
        // ProjectInspector's package.json cache keys on mtime alone;
        // test-speed rewrites land inside one tick — bump it explicitly
        assertThat(pkg.toFile().setLastModified(
                System.currentTimeMillis() + (++writes) * 2000L)).isTrue();
    }

    @Test
    @DisplayName("ENGINE=auto: config file wins (playwright first), then dependencies, else null")
    void engineResolution() throws Exception {
        assertThat(SpecterDevice.resolveEngine(dir.toFile(), "auto"))
                .as("nothing at all → no engine, the honest-grey case").isNull();

        deps("{\"cypress\": \"^13.0.0\"}");
        assertThat(SpecterDevice.resolveEngine(dir.toFile(), "auto")).isEqualTo("cypress");

        deps("{\"@playwright/test\": \"^1.44.0\", \"cypress\": \"^13.0.0\"}");
        assertThat(SpecterDevice.resolveEngine(dir.toFile(), "auto"))
                .as("playwright outranks cypress on a dependency tie").isEqualTo("playwright");

        Files.writeString(dir.resolve("cypress.config.ts"), "export default {}");
        assertThat(SpecterDevice.resolveEngine(dir.toFile(), "auto"))
                .as("a config file beats dependencies").isEqualTo("cypress");

        Files.writeString(dir.resolve("playwright.config.ts"), "export default {}");
        assertThat(SpecterDevice.resolveEngine(dir.toFile(), "auto"))
                .as("playwright config checked before cypress config").isEqualTo("playwright");

        assertThat(SpecterDevice.resolveEngine(dir.toFile(), "cypress"))
                .as("an explicit knob choice always wins").isEqualTo("cypress");
        assertThat(SpecterDevice.resolveEngine(null, "auto")).isNull();
    }

    @Test
    @DisplayName("Playwright verbs: test/headed/report/codegen/install, codegen aims at the live URL")
    void playwrightCommands() {
        assertThat(SpecterDevice.command("playwright", "run", false, null))
                .containsExactly("npx", "playwright", "test");
        assertThat(SpecterDevice.command("playwright", "run", true, null))
                .containsExactly("npx", "playwright", "test", "--headed");
        assertThat(SpecterDevice.command("playwright", "report", false, null))
                .containsExactly("npx", "playwright", "show-report", "--host", "127.0.0.1");
        assertThat(SpecterDevice.command("playwright", "record", false, "http://localhost:5173/"))
                .containsExactly("npx", "playwright", "codegen", "http://localhost:5173/");
        assertThat(SpecterDevice.command("playwright", "record", false, null))
                .as("no live serving → codegen opens blank").containsExactly("npx", "playwright", "codegen");
        assertThat(SpecterDevice.command("playwright", "browsers", false, null))
                .containsExactly("npx", "playwright", "install");
    }

    @Test
    @DisplayName("Cypress verbs: run/open/install; report and record grey (null)")
    void cypressCommands() {
        assertThat(SpecterDevice.command("cypress", "run", false, null))
                .containsExactly("npx", "cypress", "run");
        assertThat(SpecterDevice.command("cypress", "run", true, null))
                .as("HEADED on cypress is cypress open — its interactive runner/recorder")
                .containsExactly("npx", "cypress", "open");
        assertThat(SpecterDevice.command("cypress", "browsers", false, null))
                .containsExactly("npx", "cypress", "install");
        assertThat(SpecterDevice.command("cypress", "report", false, null))
                .as("cypress ships no report server — grey, never the wrong command").isNull();
        assertThat(SpecterDevice.command("cypress", "record", false, null))
                .as("cypress open IS the recorder — grey with the pointer").isNull();
        assertThat(SpecterDevice.command(null, "run", false, null)).isNull();
    }

    @Test
    @DisplayName("The currency cluster tracks the resolved engine's package")
    void versionPackage() {
        assertThat(SpecterDevice.versionPackage("playwright")).isEqualTo("@playwright/test");
        assertThat(SpecterDevice.versionPackage("cypress")).isEqualTo("cypress");
    }

    @Test
    @DisplayName("CI export is always headless even with the HEADED toggle on (v1.89.0 HIGH)")
    void ciExportAlwaysHeadless() throws Exception {
        Files.writeString(dir.resolve("playwright.config.ts"), "export default {}");
        org.nmox.studio.rack.model.Rack rack = new org.nmox.studio.rack.model.Rack();
        rack.setProjectDir(dir.toFile());
        SpecterDevice specter = new SpecterDevice();
        rack.addDevice(specter);
        try {
            specter.headed.setOn(true);
            assertThat(specter.buildCommand())
                    .as("the button honors the toggle").contains("--headed");
            assertThat(specter.exportCommand())
                    .as("CI runners are headless — the toggle must not leak")
                    .containsExactly("npx", "playwright", "test");
        } finally {
            rack.removeDevice(specter);
        }
    }

    @Test
    @DisplayName("RECORD never aims codegen at SPECTER's own report serving (v1.89.0 MED)")
    void recordSkipsOwnServing() throws Exception {
        org.nmox.studio.rack.model.Rack rack = new org.nmox.studio.rack.model.Rack();
        rack.setProjectDir(dir.toFile());
        SpecterDevice specter = new SpecterDevice();
        rack.addDevice(specter);
        org.nmox.studio.rack.service.ServingRegistry reg =
                org.nmox.studio.rack.service.ServingRegistry.getDefault();
        try {
            // SPECTER's own REPORT serving alone: RECORD must not target it
            specter.onLine("Serving HTML report at http://127.0.0.1:9323");
            assertThat(specter.liveServingUrl())
                    .as("own report serving is invisible to RECORD").isNull();
            // a real dev server joins: RECORD aims there
            reg.register(new org.nmox.studio.rack.service.ServingRegistry.Serving(
                    "test-velocity", "VELOCITY", "http://localhost:5173/",
                    org.nmox.studio.rack.service.ServingRegistry.Kind.WEB, dir.toFile()));
            assertThat(specter.liveServingUrl()).isEqualTo("http://localhost:5173/");
        } finally {
            reg.deregister("test-velocity");
            rack.removeDevice(specter);
        }
    }

    @Test
    @DisplayName("Monorepo: configs are read beside the NODE manifest, so config still beats deps (v1.89.0 MED)")
    void monorepoConfigWins() throws Exception {
        Path web = Files.createDirectories(dir.resolve("web"));
        Files.writeString(web.resolve("package.json"),
                "{\"devDependencies\": {\"@playwright/test\": \"^1.44.0\"}}");
        Files.writeString(web.resolve("cypress.config.ts"), "export default {}");
        assertThat(SpecterDevice.resolveEngine(dir.toFile(), "auto"))
                .as("the cypress config beside the manifest beats the stale playwright dep")
                .isEqualTo("cypress");
    }

    @Test
    @DisplayName("RUN with no E2E setup greys honestly: buildCommand null, status says why")
    void greysWithoutEngine(@TempDir Path bare) throws Exception {
        org.nmox.studio.rack.model.Rack rack = new org.nmox.studio.rack.model.Rack();
        Files.writeString(bare.resolve("package.json"), "{}");
        rack.setProjectDir(bare.toFile());
        SpecterDevice specter = new SpecterDevice();
        rack.addDevice(specter);
        try {
            assertThat(specter.buildCommand())
                    .as("no config, no dependency → nothing to run").isNull();
        } finally {
            rack.removeDevice(specter);
        }
    }
}
