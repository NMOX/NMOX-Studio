package org.nmox.studio.ui.site;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Serves the product's own bundled website on localhost (v2.40.0,
 * David's call: "have a built website ship with the product and have
 * people be able to navigate it on their localhost"). A tiny GET-only
 * static server over the JDK's httpserver — the Block Studio preview's
 * sibling — bound to loopback on a probed free port, registered in the
 * ServingRegistry so the ⇄ chip lights on the product's own story.
 *
 * <p>The laws: loopback only (never an interface a network can see);
 * GET/HEAD only; every path resolved canonically INSIDE the site root
 * (the v1.310.0 containment rule — {@code ../} traversal answers 404,
 * never a file); unknown types serve as octet-stream; a missing file
 * is a plain 404. Daemon threads, one instance per JVM, stopped by
 * {@link #stop()} or process exit.
 */
public final class SiteServer {

    private static final Map<String, String> MIME = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    private final File root;
    private HttpServer server;

    public SiteServer(File root) {
        this.root = root;
    }

    /** Starts on a free loopback port; returns the base URL. */
    public synchronized String start() throws IOException {
        if (server != null) {
            return url();
        }
        int port = org.nmox.studio.core.util.FreePorts.firstFreeFrom(8600);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                String method = exchange.getRequestMethod();
                if (!"GET".equals(method) && !"HEAD".equals(method)) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith("/")) {
                    path += "index.html";
                }
                // a HEAD answer carries a length of -1 (no body): passing
                // the real length makes the JDK log a WARNING per request
                // and write nothing anyway — a browser's probe HEADs would
                // spam messages.log (live-probed, the v2.40.1 review find)
                boolean head = "HEAD".equals(method);
                File target = resolveInside(root, path.substring(1));
                if (target == null || !target.isFile()) {
                    byte[] miss = "404 — not part of this site".getBytes(
                            java.nio.charset.StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, head ? -1 : miss.length);
                    if (!head) {
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(miss);
                        }
                    }
                    return;
                }
                String name = target.getName();
                String ext = name.substring(name.lastIndexOf('.') + 1);
                exchange.getResponseHeaders().set("Content-Type",
                        MIME.getOrDefault(ext, "application/octet-stream"));
                byte[] bytes = Files.readAllBytes(target.toPath());
                exchange.sendResponseHeaders(200, head ? -1 : bytes.length);
                if (!head) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                }
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "nmox-site-server");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return url();
    }

    /**
     * Canonical containment (the v1.310.0 rule): the resolved target
     * must stay inside the site root, or the answer is null → 404.
     * Package-private so the refusal is behaviorally testable.
     */
    static File resolveInside(File root, String rel) {
        try {
            File base = root.getCanonicalFile();
            File target = new File(root, rel).getCanonicalFile();
            return target.toPath().startsWith(base.toPath()) ? target : null;
        } catch (IOException bad) {
            return null;
        }
    }

    /** The actual bound address — the loopback law's witness. */
    synchronized java.net.InetAddress boundAddress() {
        return server == null ? null : server.getAddress().getAddress();
    }

    public synchronized String url() {
        return server == null ? null
                : "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
