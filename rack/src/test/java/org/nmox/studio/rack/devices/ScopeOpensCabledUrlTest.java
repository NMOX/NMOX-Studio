package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCOPE opens the URL the cable delivered, not the LCD's stale default.
 *
 * <p>Every preset and template wires {@code server.url → SCOPE.url} and
 * {@code server.ready → SCOPE.open}. The router is one FIFO thread: the
 * {@code url} signal used to write the LCD through {@code onEdt}
 * (invokeLater), while {@code open} one signal later read the LCD
 * synchronously on the router — so a first serve opened the factory
 * default. The Angular template on 4200 opened {@code http://localhost:5173}
 * (the 2026-09-17 rack walk). These tests hold the EDT hostage so the
 * paint can never win the race, which is the worst case the router can
 * produce and the one the fix must survive.
 */
class ScopeOpensCabledUrlTest {

    /** A stand-in dev server: emits URL then READY, as every real server now does. */
    private static final class Server extends RackDevice {
        Server() {
            super("stub-server", "STUB", "STUB", new Color(0, 0, 0), 1);
            addOutPort("url", "URL", SignalType.DATA);
            addOutPort("ready", "READY", SignalType.TRIGGER);
        }

        void serve(String url) {
            emit("url", Signal.data(url));
            emit("ready", Signal.trigger());
        }
    }

    private static CountDownLatch blockEdt() {
        CountDownLatch release = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
        return release;
    }

    @Test
    @DisplayName("url then open on the router thread, EDT blocked: SCOPE opens the cabled URL")
    void opensTheDeliveredUrlBeforeTheEdtPaintsIt() throws Exception {
        Rack rack = new Rack();
        try {
            BrowserDevice scope = new BrowserDevice();
            AtomicReference<String> opened = new AtomicReference<>();
            scope.systemBrowser = opened::set;
            rack.addDevice(scope);
            CountDownLatch release = blockEdt();
            try {
                // the exact delivery order the router produces for a server's url → ready
                scope.receive(scope.getPort("url"), Signal.data("http://localhost:4200/"));
                scope.receive(scope.getPort("open"), Signal.trigger());
                assertThat(opened.get())
                        .as("OPEN must read the URL the cable delivered, not the LCD's default")
                        .isEqualTo("http://localhost:4200/");
            } finally {
                release.countDown();
            }
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Through real cables: a server announcing URL then READY pops SCOPE at that URL")
    void throughTheRouter() throws Exception {
        Rack rack = new Rack();
        try {
            Server server = new Server();
            BrowserDevice scope = new BrowserDevice();
            AtomicReference<String> opened = new AtomicReference<>();
            scope.systemBrowser = opened::set;
            rack.addDevice(server);
            rack.addDevice(scope);
            rack.connect(server.getPort("url"), scope.getPort("url"));
            rack.connect(server.getPort("ready"), scope.getPort("open"));
            CountDownLatch release = blockEdt();
            try {
                server.serve("http://[::1]:4200/");
                rack.awaitRouterIdle();
                assertThat(opened.get()).isEqualTo("http://[::1]:4200/");
            } finally {
                release.countDown();
            }
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A cable carrying something that is not a URL says NOT A URL on the LCD and opens nothing")
    void nonUrlPayloadSpeaks() throws Exception {
        Rack rack = new Rack();
        try {
            BrowserDevice scope = new BrowserDevice();
            AtomicReference<String> opened = new AtomicReference<>();
            scope.systemBrowser = opened::set;
            rack.addDevice(scope);
            scope.receive(scope.getPort("url"), Signal.data("12 passed, 0 failed"));
            SwingUtilities.invokeAndWait(() -> { });
            String lcd = scope.getState().get("url");
            assertThat(lcd).startsWith(CabledUrl.REFUSAL).contains("12 passed");
            scope.receive(scope.getPort("open"), Signal.trigger());
            assertThat(opened.get()).as("nothing to open").isNull();
        } finally {
            rack.shutdown();
        }
    }
}
