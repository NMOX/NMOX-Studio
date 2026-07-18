package org.nmox.studio.rack.blockstudio;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * The live-preview loop: a loopback-only HTTP server (the JDK's own —
 * no process spawned, the boot laws hold trivially) serving a two-file
 * site from memory: {@code /} is a dark harness page that mounts the
 * component, {@code /component.js} is whatever the studio's generator
 * currently says. The code supplier is read per request, so editing
 * blocks and refreshing the browser shows the new component — no build
 * step, no temp files.
 *
 * <p>Bound to 127.0.0.1 with an ephemeral port; never reachable off the
 * machine. One server per studio; start/stop is idempatent and the
 * studio stops it on tab close and on project switch (the serve-device
 * law: a stopped preview deregisters everywhere it announced itself).
 */
final class BlockPreviewServer {

    private final Supplier<String> tag;
    private final Supplier<String> code;
    private HttpServer server;

    BlockPreviewServer(Supplier<String> tag, Supplier<String> code) {
        this.tag = tag;
        this.code = code;
    }

    /** Starts on an ephemeral loopback port; returns the URL. */
    synchronized String start() throws IOException {
        if (server != null) {
            return url();
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // daemon threads: an in-process loopback preview must never be the
        // thing keeping the JVM alive (the JDK default executor is non-daemon)
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Block Preview");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", exchange -> {
            byte[] body;
            String type;
            if ("/component.js".equals(exchange.getRequestURI().getPath())) {
                body = code.get().getBytes(StandardCharsets.UTF_8);
                type = "text/javascript; charset=utf-8";
            } else {
                body = harness(tag.get()).getBytes(StandardCharsets.UTF_8);
                type = "text/html; charset=utf-8";
            }
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return url();
    }

    synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    synchronized boolean running() {
        return server != null;
    }

    synchronized String url() {
        return server == null ? null
                : "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    /**
     * The harness page: mounts the component twice (once bare, once with
     * slotted light-DOM content so SLOT pieces show something) and logs
     * every CustomEvent the component dispatches — the DISPATCH piece's
     * output is visible without opening devtools.
     */
    static String harness(String tag) {
        String safe = tag.replace("<", "&lt;").replace(">", "&gt;");
        return """
                <!doctype html>
                <html>
                <head>
                <meta charset="utf-8">
                <title>%s — NMOX Block Studio preview</title>
                <script type="module" src="/component.js"></script>
                <style>
                  body { font-family: system-ui, sans-serif; background: #14171c; color: #d8dee9;
                         margin: 0; padding: 2rem; }
                  h1 { font-size: 1rem; color: #7aa2f7; font-weight: 500; }
                  .stage { border: 1px solid #2a2f3a; border-radius: 8px; padding: 1.5rem;
                           margin-bottom: 1rem; background: #1a1e26; }
                  .label { font-size: .75rem; color: #565f89; margin-bottom: .75rem;
                           text-transform: uppercase; letter-spacing: .08em; }
                  #events { font: .8rem ui-monospace, monospace; color: #9ece6a;
                            white-space: pre-wrap; }
                </style>
                </head>
                <body>
                <h1>&lt;%s&gt; — refresh after editing blocks</h1>
                <div class="stage"><div class="label">component</div>%s</div>
                <div class="stage"><div class="label">with slotted content</div>
                  <%s><span>slotted from the page</span></%s></div>
                <div class="stage"><div class="label">events dispatched</div>
                  <div id="events">(none yet)</div></div>
                <script>
                  const log = document.getElementById('events');
                  let n = 0;
                  addEventListener('load', () => {
                    for (const el of document.querySelectorAll('%s')) {
                      const orig = el.dispatchEvent.bind(el);
                      el.dispatchEvent = ev => {
                        if (ev instanceof CustomEvent) {
                          log.textContent = (n++ ? log.textContent + '\\n' : '')
                              + ev.type + '  detail: ' + JSON.stringify(ev.detail);
                        }
                        return orig(ev);
                      };
                    }
                  });
                </script>
                </body>
                </html>
                """.formatted(safe, safe, "<" + tag + "></" + tag + ">", tag, tag, tag);
    }
}
