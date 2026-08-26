package org.nmox.studio.ui.browser.devtools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.core.spi.LiveServings;
import org.nmox.studio.rack.engine.DiagnosticsBus;

/**
 * Runtime errors become first-class citizens (v2.39.0, David's ask:
 * the best tool for doing, learning, and experimenting with web
 * development — the loop that teaches is the loop that closes). A JS
 * error in the in-app Browser used to die in the DevTools console;
 * now the bridge's {@code err()} capture — {@code "message
 * (http://127.0.0.1:8080/main.js:12)"} — resolves back to the SOURCE
 * FILE through the same serving-registry machinery as Open Source
 * (v1.357.0), and lands on the {@link DiagnosticsBus} as the
 * {@code [browser]} tool: a squiggle at the line and an Action Items
 * row with click-to-navigate, exactly like an eslint finding. Break
 * your page and the mistake appears in your editor, where you made
 * it.
 *
 * <p>The laws carried in: errors belong to the PAGE LOAD that
 * produced them — a reload or navigation clears the previous page's
 * batch (the v1.172.0 result-ownership law; the bus's v1.49.0
 * replace-per-run semantics make an empty publish the clear).
 * Off-project URLs (a CDN script, a remote page) resolve to nothing
 * and are simply not published — the console still shows them; the
 * editor only ever carries YOUR files. Pure parsing so every rule is
 * a unit test.
 */
public final class RuntimeErrors {

    static final String TOOL = "browser";

    /** "message (url:line)" — the DevScripts window.onerror shape. */
    private static final Pattern LOCATED =
            Pattern.compile("^(.*) \\((\\S+):(\\d+)\\)$", Pattern.DOTALL);

    private final List<DiagnosticsBus.Problem> current = new ArrayList<>();

    /** One parsed error location, or null when the text carries none. */
    record Located(String message, String url, int line) {
    }

    /**
     * Parses the bridge's error text. Unhandled rejections and
     * errors without a usable location (empty filename, line 0 —
     * WebKit's shape for cross-origin or eval frames) return null:
     * a guessed location is worse than none.
     */
    static Located parse(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = LOCATED.matcher(text.strip());
        if (!m.matches() || m.group(2).isEmpty()) {
            return null;
        }
        int line = Integer.parseInt(m.group(3));
        if (line <= 0) {
            return null;
        }
        return new Located(m.group(1).strip(), m.group(2), line);
    }

    /**
     * An error arrived from the page: resolve and, if it lands in a
     * served project file, publish the accumulated batch. EDT (the
     * bridge marshals); the resolve is a string/path computation over
     * an in-memory servings snapshot — no disk walk.
     */
    public void onError(String text) {
        Located loc = parse(text);
        if (loc == null) {
            return;
        }
        LiveServings servings = LiveServings.find();
        List<LiveServings.Serving> snapshot =
                servings == null ? List.of() : servings.snapshot();
        PageSourceResolver.Resolved r = PageSourceResolver.resolve(loc.url(), snapshot);
        if (r == null || r.file() == null) {
            return; // not ours — the console still shows it
        }
        add(r.file(), loc.line(), loc.message());
    }

    /** The batch step, split out so the bus contract is testable. */
    void add(File file, int line, String message) {
        current.add(new DiagnosticsBus.Problem(file, line, message, true));
        DiagnosticsBus.publish(TOOL, List.copyOf(current));
    }

    /**
     * A new document is loading: the previous page's errors are that
     * page's, not this one's. The empty publish clears every file the
     * old batch touched (the bus's all-clear rule).
     */
    public void onPageLoad() {
        if (!current.isEmpty()) {
            current.clear();
            DiagnosticsBus.publish(TOOL, List.of());
        }
    }

    /** The live batch — tests, and the Console pane's Explain error…
     *  flow (v2.39.2), which wants the LAST located error. */
    public List<DiagnosticsBus.Problem> current() {
        return List.copyOf(current);
    }

    /** What Explain error… should explain (v2.39.2): the LAST located
     *  error when one exists, else the last console-level error text
     *  (message-only), else null. Pure so the choice is a unit test. */
    public record ExplainTarget(String message, File file, int line) {
    }

    public static ExplainTarget pickExplainTarget(
            List<DiagnosticsBus.Problem> located, List<String> consoleErrors) {
        if (located != null && !located.isEmpty()) {
            var last = located.get(located.size() - 1);
            return new ExplainTarget(last.message(), last.file(), last.line());
        }
        if (consoleErrors != null && !consoleErrors.isEmpty()) {
            return new ExplainTarget(
                    consoleErrors.get(consoleErrors.size() - 1), null, 0);
        }
        return null;
    }

    /** Resolves a page URL to a served file, or null. Test seam. */
    static File resolveForTest(String url, List<LiveServings.Serving> servings) {
        PageSourceResolver.Resolved r = PageSourceResolver.resolve(url, servings);
        return r == null ? null : r.file();
    }
}
