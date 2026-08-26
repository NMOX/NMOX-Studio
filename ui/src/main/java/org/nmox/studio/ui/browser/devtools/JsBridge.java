package org.nmox.studio.ui.browser.devtools;

import java.util.concurrent.Executor;

/**
 * The Java object the page talks to: installed as
 * {@code window.nmoxBridge} via {@code JSObject.setMember} on every
 * successful load, then called by the injected {@link DevScripts}
 * wrappers. Class and methods MUST be public — WebView invokes them
 * reflectively from page JS.
 *
 * <p>THE WEBVIEW GOTCHA this class's owner must respect:
 * {@code JSObject.setMember} holds the Java object WEAKLY. If nothing
 * on the Java side keeps a strong reference, the bridge is silently
 * garbage-collected and every console/network capture just stops —
 * no error, no log. {@code FxBrowserPanel} therefore holds this
 * bridge in a final field for the panel's whole life.
 *
 * <p>Threading: upcalls arrive on the FX Application Thread. This
 * class marshals every call through the injected {@code executor}
 * (the EDT in production, a direct executor in tests) BEFORE touching
 * the models, which are EDT-confined. Strings from the page are
 * untrusted: pre-capped here (8k) on the calling thread so a hostile
 * megabyte string never even crosses to the EDT, then capped again by
 * the models.
 */
public final class JsBridge {

    private final Executor executor;
    private final ConsoleModel console;
    private final NetworkModel network;

    private final RuntimeErrors runtimeErrors = new RuntimeErrors();

    /** The page-error → editor pipeline (v2.39.0). */
    public RuntimeErrors runtimeErrors() {
        return runtimeErrors;
    }

    public JsBridge(Executor executor, ConsoleModel console, NetworkModel network) {
        this.executor = executor;
        this.console = console;
        this.network = network;
    }

    /** Page console output (wrapped console.log/info/warn/error/debug). */
    public void log(String level, String text) {
        String lvl = ConsoleModel.normalizeLevel(level);
        String capped = ConsoleModel.truncate(text);
        long at = System.currentTimeMillis();
        executor.execute(() -> console.add(lvl, capped, at));
    }

    /** One finished network request as JSON from the fetch/XHR wrappers. */
    public void net(String json) {
        String capped = ConsoleModel.truncate(json);
        executor.execute(() -> network.addFromJson(capped));
    }

    /** A page error (window.onerror / unhandledrejection). */
    public void err(String text) {
        String capped = ConsoleModel.truncate(text);
        long at = System.currentTimeMillis();
        executor.execute(() -> {
            console.add("error", capped, at);
            // the same capture also lands in the EDITOR when it
            // resolves to a served project file (v2.39.0)
            runtimeErrors.onError(capped);
        });
    }
}
