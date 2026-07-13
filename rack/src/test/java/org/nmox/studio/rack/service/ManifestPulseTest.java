package org.nmox.studio.rack.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The manifest pulse end to end through a REAL FileWatcher: a kit
 * writing many manifests at once is ONE coalesced dispatch (the storm
 * law), filename-matched — a .json extension alone gets nobody in.
 */
class ManifestPulseTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("STORM LAW: a burst of manifest writes is exactly one dispatch, manifests only")
    void burstOfWritesDispatchesOnce() throws Exception {
        List<List<Path>> batches = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch first = new CountDownLatch(1);
        ManifestPulse pulse = new ManifestPulse(root.toFile(), 250, 500, batch -> {
            batches.add(batch);
            first.countDown();
        });
        pulse.start();
        try {
            Thread.sleep(400); // let the baseline scan settle before writing

            // the kit: ten manifests, plus files that must NOT pulse
            String[] manifests = {"package.json", "package-lock.json", "bower.json",
                "composer.json", "composer.lock", "foundry.toml", ".gas-snapshot",
                "Gruntfile.js", "gulpfile.js", "webpack.config.js"};
            for (String name : manifests) {
                Files.writeString(root.resolve(name), "{}");
            }
            Files.writeString(root.resolve("app.js"), "code();");        // source, not manifest
            Files.writeString(root.resolve("data.json"), "{}");          // extension trap
            Files.createDirectories(root.resolve("sub"));
            Files.writeString(root.resolve("sub/package.json"), "{}");   // nested manifest counts

            assertThat(first.await(5, TimeUnit.SECONDS)).isTrue();
            // wait out a full extra poll+window: stragglers would land now
            Thread.sleep(1_000);

            assertThat(batches).as("one coalesced dispatch for the whole kit").hasSize(1);
            List<String> names = batches.get(0).stream()
                    .map(p -> p.getFileName().toString()).toList();
            assertThat(names).contains(manifests);
            assertThat(names).contains("package.json");                  // nested one too
            assertThat(names).doesNotContain("app.js", "data.json");
        } finally {
            pulse.stop();
        }
    }

    @Test
    @DisplayName(".env edits pulse too — the status note and studio listeners hang off this")
    void envChangeDispatches() throws Exception {
        Files.writeString(root.resolve(".env"), "A=1\n");
        List<List<Path>> batches = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch first = new CountDownLatch(1);
        ManifestPulse pulse = new ManifestPulse(root.toFile(), 250, 300, batch -> {
            batches.add(batch);
            first.countDown();
        });
        pulse.start();
        try {
            Thread.sleep(400);
            Files.writeString(root.resolve(".env"), "A=2\n");
            assertThat(first.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(batches.get(0)).extracting(p -> p.getFileName().toString())
                    .containsExactly(".env");
        } finally {
            pulse.stop();
        }
    }

    @Test
    @DisplayName("stop() ends the pulse; later writes dispatch nothing")
    void stopSilences() throws Exception {
        List<List<Path>> batches = Collections.synchronizedList(new java.util.ArrayList<>());
        ManifestPulse pulse = new ManifestPulse(root.toFile(), 250, 300, batches::add);
        pulse.start();
        Thread.sleep(400);
        pulse.stop();
        Files.writeString(root.resolve("package.json"), "{}");
        Thread.sleep(800);
        assertThat(batches).isEmpty();
    }

    @Test
    @DisplayName("the manifest set is exactly the nineteen configured names")
    void manifestNameSet() {
        assertThat(ManifestPulse.MANIFEST_NAMES).containsExactlyInAnyOrder(
                "package.json", "package-lock.json",
                "pnpm-lock.yaml", "yarn.lock", "pnpm-workspace.yaml", "bower.json",
                "composer.json", "composer.lock",
                "foundry.toml", ".gas-snapshot", ".env",
                "Gruntfile.js", "Gruntfile.coffee",
                "gulpfile.js", "gulpfile.babel.js", "gulpfile.mjs",
                "webpack.config.js", "webpack.config.cjs", "webpack.config.mjs");
    }
}
