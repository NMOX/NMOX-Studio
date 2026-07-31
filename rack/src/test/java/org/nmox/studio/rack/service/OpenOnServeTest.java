package org.nmox.studio.rack.service;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.212.0 loop-closer: pressing Run opens the served page.
 *
 * <p>The interesting behaviour isn't "it opens" — it's everything it
 * declines to open. A serving can appear because the session resumed
 * after a crash, because a preset booted three devices, or because a
 * different project came up; none of those are a user asking to see a
 * page. These pin the arm's boundaries.
 */
class OpenOnServeTest {

    private static final File PROJECT = new File("/tmp/nmox-openonserve/app");
    private static final File OTHER = new File("/tmp/nmox-openonserve/other");

    private ServingRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ServingRegistry.getDefault();
        for (ServingRegistry.Serving s : registry.snapshot()) {
            registry.deregister(s.deviceId());
        }
        registry.awaitIdle();
        OpenOnServe.resetForTest();
    }

    @AfterEach
    void tearDown() {
        for (ServingRegistry.Serving s : registry.snapshot()) {
            registry.deregister(s.deviceId());
        }
        registry.awaitIdle();
        OpenOnServe.resetForTest();
    }

    private static ServingRegistry.Serving web(String id, String url, File dir) {
        return new ServingRegistry.Serving(id, id, url,
                ServingRegistry.Kind.WEB, dir);
    }

    /**
     * Captures what the opener WOULD open. The production path hands the
     * URL to the EmbeddedBrowser facade, which is absent in a plain unit
     * test (find() == null) — so we observe the decision through the
     * registry state the opener acts on, and assert the arm's lifecycle
     * instead of the browser call.
     */
    private static AtomicReference<String> armAndWatch(File dir) {
        OpenOnServe.getDefault().arm(dir);
        return new AtomicReference<>();
    }

    @Test
    @DisplayName("a new web serving for the armed project spends the arm")
    void newServingForArmedProjectIsConsumed() {
        armAndWatch(PROJECT);
        assertThat(OpenOnServe.getDefault().isArmedForTest()).isTrue();

        registry.register(web("VELOCITY", "http://localhost:5173/", PROJECT));
        registry.awaitIdle();

        assertThat(OpenOnServe.getDefault().isArmedForTest())
                .as("the arm is spent once the page has been shown")
                .isFalse();
    }

    @Test
    @DisplayName("a serving for a DIFFERENT project leaves the arm standing")
    void otherProjectDoesNotConsumeTheArm() {
        armAndWatch(PROJECT);

        registry.register(web("VELOCITY", "http://localhost:5173/", OTHER));
        registry.awaitIdle();

        assertThat(OpenOnServe.getDefault().isArmedForTest())
                .as("running project A must not open project B's page")
                .isTrue();
    }

    @Test
    @DisplayName("a serving that already existed when Run was pressed is not re-opened")
    void preExistingServingIsNotReopened() {
        registry.register(web("VELOCITY", "http://localhost:5173/", PROJECT));
        registry.awaitIdle();

        armAndWatch(PROJECT);
        // a coarse notification with no NEW url — e.g. some unrelated
        // device registering elsewhere
        registry.register(web("OTHER-DEV", "http://localhost:9999/", OTHER));
        registry.awaitIdle();

        assertThat(OpenOnServe.getDefault().isArmedForTest())
                .as("the page was already up; pressing Run opens nothing new")
                .isTrue();
    }

    @Test
    @DisplayName("a CHAIN serving (a devnet RPC) is never opened as a page")
    void chainServingIsIgnored() {
        armAndWatch(PROJECT);

        registry.register(new ServingRegistry.Serving("ANVIL", "ANVIL",
                "http://127.0.0.1:8545", ServingRegistry.Kind.CHAIN, PROJECT));
        registry.awaitIdle();

        assertThat(OpenOnServe.getDefault().isArmedForTest())
                .as("an EVM JSON-RPC endpoint is not a page to look at")
                .isTrue();
    }

    @Test
    @DisplayName("nothing is armed until a Run asks for it")
    void unarmedByDefault() {
        assertThat(OpenOnServe.getDefault().isArmedForTest())
                .as("session resurrection and presets must not open tabs")
                .isFalse();

        registry.register(web("VELOCITY", "http://localhost:5173/", PROJECT));
        registry.awaitIdle();

        assertThat(OpenOnServe.getDefault().isArmedForTest()).isFalse();
    }

    @Test
    @DisplayName("re-arming replaces the previous arm (newest gesture wins)")
    void rearmReplaces() {
        armAndWatch(PROJECT);
        armAndWatch(OTHER);

        // the FIRST project serving no longer matches the live arm
        registry.register(web("VELOCITY", "http://localhost:5173/", PROJECT));
        registry.awaitIdle();
        assertThat(OpenOnServe.getDefault().isArmedForTest()).isTrue();

        registry.register(web("NEXUS", "http://localhost:3000/", OTHER));
        registry.awaitIdle();
        assertThat(OpenOnServe.getDefault().isArmedForTest()).isFalse();
    }
}
