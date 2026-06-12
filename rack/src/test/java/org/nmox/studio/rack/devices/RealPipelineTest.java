package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.engine.ToolLocator;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The gauntlet: the rack's pipeline run against REAL tools - node and
 * npm, no mocks, no network. Install readies a project, build produces
 * artifacts, the test tally counts real output, the dev server
 * announces a real URL and dies without orphans. If these pass, a web
 * developer's day-one loop works.
 */
class RealPipelineTest {

    @TempDir
    static Path projectDir;

    private Rack rack;

    @BeforeAll
    static void requireTools() throws Exception {
        assumeTrue(toolOnPath("node"), "node not installed; gauntlet skipped");
        assumeTrue(toolOnPath("npm"), "npm not installed; gauntlet skipped");
        writeFixtureProject(projectDir);
    }

    private static boolean toolOnPath(String tool) {
        for (String dir : ToolLocator.augmentedPath().split(File.pathSeparator)) {
            if (new File(dir, tool).canExecute()) {
                return true;
            }
        }
        return false;
    }

    /** A zero-dependency npm project: no registry, no network, all real. */
    private static void writeFixtureProject(Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                {
                  "name": "gauntlet-fixture",
                  "version": "1.0.0",
                  "scripts": {
                    "build": "node build.js",
                    "test": "node test.js",
                    "dev": "node server.js"
                  }
                }
                """);
        Files.writeString(dir.resolve("build.js"), """
                const fs = require('fs');
                fs.mkdirSync('dist', { recursive: true });
                fs.writeFileSync('dist/out.txt', 'artifact');
                console.log('built in 0.1s');
                """);
        Files.writeString(dir.resolve("test.js"), """
                console.log('3 passed');
                """);
        Files.writeString(dir.resolve("server.js"), """
                const http = require('http');
                const srv = http.createServer((req, res) => res.end('ok'));
                srv.listen(0, '127.0.0.1', () => {
                    const port = srv.address().port;
                    console.log('PID ' + process.pid);
                    console.log('Local: http://localhost:' + port + '/');
                });
                """);
    }

    @BeforeEach
    void freshRack() {
        rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
    }

    @AfterEach
    void tearDown() {
        rack.shutdown();
    }

    /** Captures everything patched into it; a cable-level assertion point. */
    private static final class ProbeDevice extends RackDevice {

        final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();
        final CountDownLatch hit = new CountDownLatch(1);

        ProbeDevice() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("in", "IN", SignalType.TRIGGER);
            addInPort("data", "DATA", SignalType.DATA);
            addInPort("gate", "GATE", SignalType.GATE);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(in.getId() + ":" + signal.type() + ":" + signal.payload());
            hit.countDown();
        }
    }

    private static void await(CountDownLatch latch, int seconds, String what) throws Exception {
        assertThat(latch.await(seconds, TimeUnit.SECONDS)).as(what).isTrue();
    }

    @Test
    @DisplayName("CRATE installs a real npm project and fires OK down the cable")
    void installReadiesTheProject() throws Exception {
        PackageManagerDevice crate = new PackageManagerDevice();
        ProbeDevice probe = new ProbeDevice();
        rack.addDevice(crate);
        rack.addDevice(probe);
        rack.connect(crate.getPort("ok"), probe.getPort("in"));

        crate.receive(crate.getPort("run"), Signal.trigger(true));

        await(probe.hit, 90, "install must finish and fire OK");
        assertThat(new File(projectDir.toFile(), "package-lock.json"))
                .as("npm install must produce a lockfile").exists();
    }

    @Test
    @DisplayName("FORGE resolves AUTO to the build script and produces the artifact")
    void buildProducesArtifacts() throws Exception {
        BuildDevice forge = new BuildDevice();
        ProbeDevice probe = new ProbeDevice();
        rack.addDevice(forge);
        rack.addDevice(probe);
        rack.connect(forge.getPort("ok"), probe.getPort("in"));

        forge.receive(forge.getPort("run"), Signal.trigger(true));

        await(probe.hit, 60, "build must finish and fire OK");
        assertThat(new File(projectDir.toFile(), "dist/out.txt"))
                .as("the build script's artifact").exists();
    }

    @Test
    @DisplayName("VERITAS runs the real test script and OK carries the verdict")
    void testRunnerFiresOk() throws Exception {
        TestDevice veritas = new TestDevice();
        ProbeDevice probe = new ProbeDevice();
        rack.addDevice(veritas);
        rack.addDevice(probe);
        rack.connect(veritas.getPort("ok"), probe.getPort("in"));

        veritas.receive(veritas.getPort("run"), Signal.trigger(true));

        await(probe.hit, 60, "test run must finish and fire OK");
    }

    @Test
    @DisplayName("SURGE serves, announces the REAL printed URL, and stops without orphans")
    void devServerLifecycle() throws Exception {
        DevServerDevice surge = new DevServerDevice();
        ProbeDevice urlProbe = new ProbeDevice();
        rack.addDevice(surge);
        rack.addDevice(urlProbe);
        rack.connect(surge.getPort("url"), urlProbe.getPort("data"));

        surge.receive(surge.getPort("run"), Signal.trigger(true));

        // the server prints its OS-assigned port; SURGE must re-announce it
        long deadline = System.currentTimeMillis() + 30_000;
        String realUrl = null;
        long childPid = -1;
        while (System.currentTimeMillis() < deadline && (realUrl == null || childPid < 0)) {
            for (String s : urlProbe.received) {
                if (s.contains("http://localhost:") && !s.endsWith(":5173")) {
                    realUrl = s.substring(s.indexOf("http"));
                }
            }
            childPid = serverPidFromLog();
            Thread.sleep(200);
        }
        assertThat(realUrl).as("SURGE must announce the server's real URL").isNotNull();
        assertThat(childPid).as("server must log its PID").isGreaterThan(0);
        assertThat(ProcessHandle.of(childPid)).as("server alive while serving").isPresent();

        // STOP by cable, like the rack would
        surge.receive(surge.getPort("stop"), Signal.trigger(true));

        long pid = childPid;
        long stopDeadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < stopDeadline
                && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            Thread.sleep(200);
        }
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                .as("no orphaned dev server after STOP").isFalse();
    }

    @Test
    @DisplayName("panic() reaps the whole process tree synchronously - the shutdown guarantee")
    void panicReapsDescendants() throws Exception {
        DevServerDevice surge = new DevServerDevice();
        rack.addDevice(surge);

        // npm run dev -> node server.js: a parent with a child, exactly
        // the tree a real dev server leaves behind
        surge.receive(surge.getPort("run"), Signal.trigger(true));
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && busPid < 0) {
            Thread.sleep(200);
        }
        assertThat(busPid).as("server must come up and log its PID").isGreaterThan(0);
        long pid = busPid;
        assertThat(ProcessHandle.of(pid)).isPresent();

        // panic is the JVM-shutdown path: it must return only when the
        // tree is dead, because nothing runs after the hooks
        surge.panic();

        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                .as("panic must leave no survivors, synchronously").isFalse();
    }

    private long serverPidFromLog() {
        // SURGE's child logs "PID <n>" on the rack bus via stdout; the
        // probe only hears the URL jack, so read it from the bus instead
        return busPid;
    }

    private volatile long busPid = -1;
    private final org.nmox.studio.rack.engine.RackBus.Listener pidTap = (device, line, err) -> {
        if (line.startsWith("PID ")) {
            try {
                busPid = Long.parseLong(line.substring(4).trim());
            } catch (NumberFormatException ignored) {
            }
        }
    };

    @BeforeEach
    void tapBus() {
        busPid = -1;
        org.nmox.studio.rack.engine.RackBus.subscribe(pidTap);
    }

    @AfterEach
    void untapBus() {
        org.nmox.studio.rack.engine.RackBus.unsubscribe(pidTap);
    }
}
