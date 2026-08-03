package org.nmox.studio.ui.browser.fx;

/**
 * The save-to-reload locality predicate (v1.234.0 review): a real host
 * check, not a substring match. The v1.228.0 predicate was
 * {@code url.contains("//localhost")}, which also matched
 * {@code http://localhost.evil.example/} and any URL merely carrying
 * {@code //localhost} in its path or query — the failure was only a
 * spurious reload of a remote page, but a locality test that isn't
 * one is a bug waiting for a bigger consumer. Pure so it unit-tests
 * without a WebView.
 */
public final class LocalUrls {

    private LocalUrls() {
    }

    /** True when {@code url}'s HOST is the local machine's loopback. */
    public static boolean isLocal(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String host = java.net.URI.create(url.trim()).getHost();
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "[::1]".equals(host);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
