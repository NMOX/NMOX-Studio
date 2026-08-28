package org.nmox.studio.core.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * Picks a free TCP port for the serving lanes that used to hardcode
 * one (v1.320.0).
 *
 * <p>Why this exists: the STATIC lane runs
 * {@code python3 -m http.server 8000} and the PHP lane
 * {@code php -S 127.0.0.1:8000} — and unlike {@code http-server}
 * (which scans upward when its port is busy, the v1.264.0 fix),
 * neither python nor php will try another port. The learner walk of
 * learning space #89 — the catalog's FRONT DOOR, whose whole promise
 * is "nothing to configure" — died on its first Run because something
 * else on the machine held 8000. The port-in-use humanizer fired and
 * was honest, but "change the port" is not actionable in a space with
 * nothing to configure. The lane must simply pick a port that works.
 *
 * <p>The probe binds on the wildcard address, because that is what
 * {@code python3 -m http.server} itself binds ({@code *:port}) — a
 * loopback-only probe would call a port free that python then fails
 * to take from a wildcard listener.
 */
public final class FreePorts {

    /** How many consecutive ports to try before giving up honestly. */
    static final int RANGE = 20;

    private FreePorts() {
    }

    /**
     * The first free port at or above {@code from}, probed by actually
     * binding it. When the whole range is busy, returns {@code from}
     * unchanged — the spawn then fails exactly as before and the
     * port-in-use humanizer explains it; inventing a random port far
     * from the documented one would be harder to explain than the
     * honest failure.
     */
    public static int firstFreeFrom(int from) {
        for (int port = from; port < from + RANGE; port++) {
            if (isFree(port)) {
                return port;
            }
        }
        return from;
    }

    private static boolean isFree(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            // WILDCARD bind is load-bearing, not sloppiness (probed
            // v2.43.6): with SO_REUSEADDR on macOS a LOOPBACK bind
            // succeeds over an existing wildcard holder, so a
            // loopback-probing isFree hands back busy ports — the exact
            // v1.320 bug this class exists to fix. The socket never
            // listens: bind + immediate close, no accept.
            socket.bind(new java.net.InetSocketAddress((InetAddress) null, port));
            return true;
        } catch (IOException busy) {
            return false;
        }
    }
}
