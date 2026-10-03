package org.nmox.studio.editor.editing;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

/**
 * Toggle Word Wrap, as a rule over the platform's own setting.
 *
 * <p><b>What the platform gives.</b> Soft wrap is the editor setting
 * {@code text-line-wrap} ({@code none}, {@code words} or {@code chars}),
 * kept per LANGUAGE: a document reads it from the preferences of its mime
 * type, which inherit from the all-languages preferences
 * ({@code NbEditorDocument} puts a lazy {@code text-line-wrap} property
 * on every document that asks {@code CodeStylePreferences}, and the view
 * reads that property; RELEASE310 bytecode). It is what Options ▸ Editor ▸
 * Formatting ▸ Line Wrap edits. So the toggle is honest about its reach:
 * it switches wrap for every editor of the current file's language, and
 * the choice is saved as any editor setting is.
 *
 * <p><b>One home.</b> The toggle writes that same preference rather than
 * keeping a wrap state of its own, so the Options panel and the View menu
 * can never disagree. A value equal to what the language would inherit
 * anyway is REMOVED instead of written, which is how the Options panel
 * leaves a language that no longer overrides the global setting.
 *
 * <p><b>The view does not listen to the preference.</b> It re-reads the
 * setting when the document's property is poked, which is what the
 * Options dialog does after Apply ({@code FormattingPanelController}:
 * {@code putProperty("text-line-wrap", "")} on every open editor's
 * document). The caller passes that poke in as {@code refresh}.
 *
 * <p><b>A project can own the setting.</b> A project with its own
 * formatting settings answers from those, not from the language's; the
 * toggle then changes nothing the editor reads, and {@link Result#took}
 * says so instead of claiming a wrap that did not happen.
 */
public final class WordWrap {

    /** The platform's setting name ({@code SimpleValueNames.TEXT_LINE_WRAP}). */
    public static final String KEY = "text-line-wrap";

    /** No wrap. */
    static final String OFF = "none";

    /** Wrap after words: what the toggle switches on. */
    static final String ON = "words";

    /** What one press did. {@code took} is false when the editor still reads the old value. */
    public record Result(String mime, boolean on, boolean took) {
    }

    private WordWrap() {
    }

    /** Whether a setting value wraps: after words, or anywhere. */
    public static boolean isOn(Object value) {
        return value instanceof String s && !s.isEmpty() && !OFF.equals(s);
    }

    /**
     * Switches wrap for {@code mime}.
     *
     * @param effective what an editor of that language reads right now
     * @param prefsFor the preferences of a mime type; the empty mime is all languages
     * @param refresh tells the open editors to read the setting again
     */
    public static Result toggle(String mime, Supplier<Object> effective,
            Function<String, Preferences> prefsFor, Runnable refresh) {
        String next = isOn(effective.get()) ? OFF : ON;
        Preferences own = prefsFor.apply(mime);
        String inherited = prefsFor.apply("").get(KEY, OFF);
        if (next.equals(inherited) && !mime.isEmpty()) {
            own.remove(KEY);
        } else {
            own.put(KEY, next);
        }
        refresh.run();
        return new Result(mime, ON.equals(next), isOn(effective.get()) == ON.equals(next));
    }
}
