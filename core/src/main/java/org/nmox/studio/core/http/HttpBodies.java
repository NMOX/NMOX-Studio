package org.nmox.studio.core.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The one capped HTTP-body read (ledger 56). The unbounded-{@code ofString}
 * bug was fixed across seven sites in four releases, each inlining the same
 * mechanics: read at most a cap from an {@code ofInputStream} body, probe one
 * byte to learn whether the cap bit, decode. This class owns those mechanics;
 * the POLICY for a truncated body — flag it (API Studio), refuse it
 * (JSON-RPC, CouchDB), or shrug (display-only consoles) — deliberately stays
 * at each call site, because that is where the seven genuinely differ.
 *
 * <p>The caller keeps the stream in its own try-with-resources: closing an
 * {@code ofInputStream} body aborts the rest of the transfer, and that
 * close belongs next to the {@code send()} it balances.
 */
public final class HttpBodies {

    /** The house ceiling for API-shaped responses (~8 MB): orders of
     *  magnitude past any legitimate payload the callers parse. */
    public static final int DEFAULT_CAP_BYTES = 8 * 1024 * 1024;

    private HttpBodies() {
    }

    /** A capped read: the decoded text, the byte count actually read, and
     *  whether the source had more (the cap bit). */
    public record Capped(String text, int byteLength, boolean truncated) {
    }

    /**
     * Reads at most {@code capBytes}, probes one more byte for truncation,
     * decodes with {@code charset}. Never reads past cap+1 bytes, so a
     * gigabyte stream costs the cap, not the stream.
     */
    public static Capped read(InputStream in, int capBytes, Charset charset)
            throws IOException {
        byte[] raw = in.readNBytes(capBytes);
        boolean truncated = raw.length == capBytes && in.read() != -1;
        return new Capped(new String(raw, charset), raw.length, truncated);
    }

    /** {@link #read} with UTF-8 — what every JSON-speaking caller wants. */
    public static Capped readUtf8(InputStream in, int capBytes) throws IOException {
        return read(in, capBytes, StandardCharsets.UTF_8);
    }
}
