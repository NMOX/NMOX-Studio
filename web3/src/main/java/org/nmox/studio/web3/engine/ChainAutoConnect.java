package org.nmox.studio.web3.engine;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.spi.LiveServings;

/**
 * Auto-connects the Contract Studio chip to a local chain the rack is
 * serving — the fix for the observed v1.33.0 annoyance where ANVIL
 * starts ten pixels away and the studio doesn't notice until the
 * network combo is re-selected.
 *
 * <p>The decision is pure and transition-based (the storm law): a CHAIN
 * serving whose endpoint matches the selected network's URL
 * <em>appearing</em> while the chip is not connected triggers exactly
 * one connect; that serving <em>disappearing</em> while connected
 * triggers exactly one disconnect (the grey chip). Everything else —
 * repeat notifications, unrelated WEB servings registering, a serving
 * that was already there — is {@link Action#NONE}. Bounded by
 * construction: at most one UI reaction per actual state change.
 *
 * <p>Threading: {@link LiveServings} notifies on its own background
 * thread; the listener snapshots there (never on the EDT) and marshals
 * the extracted CHAIN URLs to the EDT, where the studio's state lives.
 */
public final class ChainAutoConnect implements LiveServings.Listener {

    /** What one registry change asks of the studio. */
    public enum Action { CONNECT, DISCONNECT, NONE }

    /**
     * A decision plus the state to carry to the next one:
     * {@code matchedUrl} is the selected network's URL while a CHAIN
     * serving matches it, null otherwise.
     */
    public record Decision(Action action, String matchedUrl) {
    }

    /** The studio surface this controller drives — all calls on the EDT. */
    public interface Chain {

        /** The selected network's plain URL; null when none or keyring-secret. */
        String selectedUrl();

        /** Whether the chip currently shows a live connection. */
        boolean connected();

        /** The existing connect/re-poll path (what the combo listener calls). */
        void connect();

        /** The existing not-connected chip state — no dialogs. */
        void disconnect();
    }

    private final LiveServings registry;
    private final Chain chain;
    /** EDT-only: the matched URL carried between decisions. */
    private String matchedUrl;
    private boolean attached;

    public ChainAutoConnect(LiveServings registry, Chain chain) {
        this.registry = registry;
        this.chain = chain;
    }

    /** Subscribes to the registry; idempotent across open/close cycles. */
    public void attach() {
        if (!attached) {
            attached = true;
            registry.addListener(this);
        }
    }

    /** Unsubscribes; after this, registry flaps reach nothing. */
    public void detach() {
        if (attached) {
            attached = false;
            registry.removeListener(this);
        }
    }

    /**
     * One evaluation outside a registry event — call OFF the EDT (it
     * reads the registry) right after opening the tab, so a chain that
     * started while the tab was closed still connects.
     */
    public void refresh() {
        servingChanged();
    }

    /** Registry thread: snapshot here, hand the EDT plain strings only. */
    @Override
    public void servingChanged() {
        List<String> chainUrls = new ArrayList<>();
        for (LiveServings.Serving serving : registry.snapshot()) {
            if (serving.kind() == LiveServings.Kind.CHAIN) {
                chainUrls.add(serving.url());
            }
        }
        SwingUtilities.invokeLater(() -> apply(chainUrls));
    }

    /** EDT: decide against the studio's live state and act. */
    private void apply(List<String> chainUrls) {
        Decision decision = decide(chain.selectedUrl(), chainUrls,
                matchedUrl, chain.connected());
        matchedUrl = decision.matchedUrl();
        switch (decision.action()) {
            case CONNECT -> chain.connect();
            case DISCONNECT -> chain.disconnect();
            case NONE -> {
            }
        }
    }

    // ---- the pure core ----------------------------------------------------

    /**
     * The transition table. {@code previousMatch} is the last decision's
     * {@link Decision#matchedUrl()} — passing it back is what makes
     * repeat notifications idempotent.
     */
    public static Decision decide(String selectedUrl, Collection<String> chainUrls,
            String previousMatch, boolean connected) {
        boolean present = selectedUrl != null && anyMatches(chainUrls, selectedUrl);
        boolean wasPresent = selectedUrl != null && previousMatch != null
                && sameEndpoint(previousMatch, selectedUrl);
        String next = present ? selectedUrl : null;
        if (present && !wasPresent && !connected) {
            return new Decision(Action.CONNECT, next);
        }
        if (!present && wasPresent && connected) {
            return new Decision(Action.DISCONNECT, next);
        }
        return new Decision(Action.NONE, next);
    }

    /** True when any of the chain URLs fronts the same endpoint. */
    public static boolean anyMatches(Collection<String> chainUrls, String networkUrl) {
        for (String url : chainUrls) {
            if (sameEndpoint(url, networkUrl)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Host+port equality, tolerant of the localhost spellings
     * (127.0.0.1, localhost, [::1], 0.0.0.0 all mean "this machine"),
     * default ports, and trailing paths. Unparseable URLs match nothing.
     */
    public static boolean sameEndpoint(String a, String b) {
        String keyA = endpointKey(a);
        return keyA != null && keyA.equals(endpointKey(b));
    }

    /** "host:port" with loopback and default-port normalization; null if unparseable. */
    static String endpointKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String candidate = url.contains("://") ? url : "http://" + url;
        try {
            URI uri = new URI(candidate.trim());
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            if ("localhost".equals(host) || "0.0.0.0".equals(host)
                    || "[::1]".equals(host) || "::1".equals(host)) {
                host = "127.0.0.1";
            }
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return host + ":" + port;
        } catch (java.net.URISyntaxException malformed) {
            return null;
        }
    }
}
