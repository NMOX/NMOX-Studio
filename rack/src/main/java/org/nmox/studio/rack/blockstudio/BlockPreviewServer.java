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
    /**
     * The whole workspace's valid components, tag→code (v5, the
     * composition loop): the harness imports every OTHER component's
     * module from {@code /lib/<tag>.js}, so a component nesting a
     * sibling's custom tag renders it live instead of an inert unknown
     * element. Read per request, like {@link #code}.
     */
    private final Supplier<java.util.Map<String, String>> library;
    private HttpServer server;

    BlockPreviewServer(Supplier<String> tag, Supplier<String> code,
            Supplier<java.util.Map<String, String>> library) {
        this.tag = tag;
        this.code = code;
        this.library = library;
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
            String path = exchange.getRequestURI().getPath();
            if ("/component.js".equals(path)) {
                body = code.get().getBytes(StandardCharsets.UTF_8);
                type = "text/javascript; charset=utf-8";
            } else if (path.startsWith("/lib/") && path.endsWith(".js")) {
                String wanted = path.substring("/lib/".length(), path.length() - ".js".length());
                String libCode = library.get().get(wanted);
                if (libCode == null) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                body = libCode.getBytes(StandardCharsets.UTF_8);
                type = "text/javascript; charset=utf-8";
            } else {
                String active = tag.get();
                // every valid sibling component's module rides along, so
                // nested custom tags upgrade — the active one is excluded
                // (double customElements.define throws)
                java.util.List<String> others = new java.util.ArrayList<>(library.get().keySet());
                others.remove(active);
                body = harness(active, others).getBytes(StandardCharsets.UTF_8);
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
    static String harness(String tag, java.util.List<String> otherTags) {
        String safe = tag.replace("<", "&lt;").replace(">", "&gt;");
        StringBuilder libs = new StringBuilder();
        for (String other : otherTags) {
            // whitelist sanitizer, not trust: rebuild the tag from the
            // custom-element alphabet so nothing else can reach the page
            // or the /lib URL — tags here already passed validTag, this
            // makes it a structural guarantee (the find-sec-bugs idiom)
            StringBuilder clean = new StringBuilder(other.length());
            for (int i = 0; i < other.length(); i++) {
                char c = other.charAt(i);
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                    clean.append(c);
                }
            }
            if (clean.length() != other.length() || !BlockCodegen.validTag(other)) {
                continue; // not tag-shaped — never ours, skip it
            }
            libs.append("<script type=\"module\" src=\"/lib/").append(clean)
                    .append(".js\"></script>\n");
        }
        return """
                <!doctype html>
                <html>
                <head>
                <meta charset="utf-8">
                <title>%s — NMOX Block Studio preview</title>
                <script type="module" src="/component.js"></script>
                %s<style>
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
                """.formatted(safe, libs, safe, "<" + tag + "></" + tag + ">", tag, tag, tag);
    }
}
