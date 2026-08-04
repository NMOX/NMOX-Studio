package org.nmox.studio.core.http;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Makes {@code localhost} URLs load in the Browser regardless of which
 * loopback stack the dev server actually bound.
 *
 * <p>The WebView's URL loader connects to only the FIRST address
 * {@code localhost} resolves to — it has no happy-eyeballs fallback the
 * way desktop browsers do. Java resolves {@code localhost} to
 * {@code [127.0.0.1, ::1]} on macOS, and Angular 21's esbuild dev
 * server ({@code ng serve}) binds ONLY {@code [::1]} when told to
 * listen on {@code localhost} — measured 2026-08-04 on the shipped
 * 1.258.0 runtime: the engine reported "Connection refused by server"
 * for {@code http://localhost:4321/} while the same page loaded via
 * {@code http://[::1]:4321/}, and curl/Chrome (which try both stacks)
 * worked either way. So the exact URL the CLI prints — the one SCOPE
 * announces and users type — failed in the in-app Browser for the
 * product's chosen framework.
 *
 * <p>{@link #resolve} probes the loopback stacks in the LOADER'S OWN
 * resolution order and rewrites the URL only when the as-typed form
 * would fail: if the first-resolved stack answers, the URL is returned
 * unchanged (no behavior change, no Host-header change); if only the
 * OTHER loopback answers, the host is rewritten to that literal
 * ({@code [::1]} or {@code 127.0.0.1} — both already count as local for
 * the v1.228.0 save-to-reload check, pinned by the ui module's
 * LocalUrls test); if neither
 * answers, the URL is returned unchanged so the user sees the honest
 * connection error for what they typed. Non-localhost URLs are never
 * probed and never touched.
 *
 * <p>Probing opens sockets, so callers must run {@link #resolve} OFF
 * the EDT; {@link #needsProbe} is the cheap EDT-safe pre-check.
 */
public final class LoopbackUrls {

    /** Per-stack connect budget; two worst-case probes stay near 300ms. */
    static final int PROBE_TIMEOUT_MS = 150;

    private LoopbackUrls() {
    }

    /** Seam: how a stack is probed. Tests swap this; production connects. */
    interface Prober {
        boolean connects(String hostLiteral, int port);
    }

    static Prober prober = (hostLiteral, port) -> {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(hostLiteral, port), PROBE_TIMEOUT_MS);
            return true;
        } catch (Exception refusedOrTimedOut) {
            return false;
        }
    };

    /**
     * Seam: whether the loader's first-resolved localhost address is
     * IPv4 (that is the address the WebView will actually dial).
     */
    static java.util.function.BooleanSupplier firstResolvedIsV4 = () -> {
        try {
            return InetAddress.getAllByName("localhost")[0] instanceof Inet4Address;
        } catch (Exception unresolvable) {
            return true;
        }
    };

    /** EDT-safe: true only for http(s) URLs whose host is localhost. */
    public static boolean needsProbe(String url) {
        URI uri = parse(url);
        return uri != null;
    }

    /**
     * OFF-EDT. The URL to hand the engine: unchanged when the typed
     * form will work (or nothing answers), rewritten to the answering
     * loopback literal when only the other stack is listening.
     */
    public static String resolve(String url) {
        URI uri = parse(url);
        if (uri == null) {
            return url;
        }
        int port = uri.getPort() >= 0 ? uri.getPort()
                : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        boolean v4First = firstResolvedIsV4.getAsBoolean();
        String first = v4First ? "127.0.0.1" : "::1";
        String other = v4First ? "::1" : "127.0.0.1";
        if (prober.connects(first, port)) {
            return url; // the loader's own dial will succeed as typed
        }
        if (prober.connects(other, port)) {
            try {
                // the URI ctor brackets IPv6 literals and keeps
                // path/query/fragment verbatim (pinned by test)
                return new URI(uri.getScheme(), uri.getUserInfo(), other,
                        uri.getPort(), uri.getPath(), uri.getQuery(),
                        uri.getFragment()).toString();
            } catch (java.net.URISyntaxException impossible) {
                return url;
            }
        }
        return url; // honest failure for what the user typed
    }

    /** The parsed URI when this is a probe-worthy localhost URL, else null. */
    private static URI parse(String url) {
        if (url == null) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException notAUrl) {
            return null;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        return "localhost".equalsIgnoreCase(uri.getHost()) ? uri : null;
    }
}
