package org.nmox.studio.ui.browser.devtools;

import java.util.function.Consumer;

/**
 * The DevTools panel's one door to the page: run a script in the
 * engine, hear the string result back on the EDT. Implemented by
 * {@code FxBrowserPanel} (marshal to the FX Application Thread,
 * executeScript, marshal the result to the EDT); kept as an interface
 * so the whole DevTools UI has zero JavaFX imports and the panel can
 * be exercised with a fake runner.
 */
public interface ScriptRunner {

    /**
     * Runs {@code js} in the page. Exactly one of the callbacks fires,
     * on the EDT: {@code onResult} with the completion value rendered
     * as a string, or {@code onError} with a short failure message
     * (engine not ready, script threw).
     */
    void run(String js, Consumer<String> onResult, Consumer<String> onError);
}
