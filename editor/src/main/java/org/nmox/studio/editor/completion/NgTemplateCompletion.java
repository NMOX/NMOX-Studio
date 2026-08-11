package org.nmox.studio.editor.completion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pure half of Angular template completion (v1.217.0): given the
 * line up to the caret, the Angular constructs that belong there. UI-
 * and platform-free so every rule is a plain unit test — the provider
 * wrapping this is a thin adapter.
 *
 * <p>Two trigger shapes, matching how Angular templates are written:
 * <ul>
 *   <li>{@code @} at markup level starts the Angular 17+ control-flow
 *       blocks ({@code @if}, {@code @for}, {@code @switch}, the
 *       {@code @defer} family, {@code @let}).</li>
 *   <li>{@code *} inside a tag starts the classic structural
 *       directives ({@code *ngIf}, {@code *ngFor}, …) — still
 *       everywhere in real codebases, so both generations complete.</li>
 * </ul>
 */
public final class NgTemplateCompletion {

    /** A completion item: the text to insert and a short type label. */
    public record Item(String insert, String label) {
    }

    /** The Angular 17+ block keywords, in the order they read in docs. */
    static final String[] BLOCKS = {
        "if", "else", "else if", "for", "empty", "switch", "case",
        "default", "defer", "placeholder", "loading", "error", "let",
    };

    /** The classic structural directives (the {@code *} generation). */
    static final String[] STRUCTURAL = {
        "ngIf", "ngFor", "ngSwitch", "ngSwitchCase", "ngSwitchDefault",
        "ngTemplateOutlet", "ngComponentOutlet",
    };

    private NgTemplateCompletion() {
    }

    /**
     * Items for the caret position described by {@code lineToCaret}, or
     * an empty list when the position is not an Angular trigger. Never
     * null.
     */
    public static List<Item> items(String lineToCaret) {
        List<Item> out = new ArrayList<>();
        if (lineToCaret == null) {
            return out;
        }
        int at = lineToCaret.lastIndexOf('@');
        int star = lineToCaret.lastIndexOf('*');
        if (at >= 0 && at >= star) {
            String typed = lineToCaret.substring(at + 1);
            // only at markup level: "@" inside a word (an email in text,
            // a decorator in a code sample) is not a block start
            if (at > 0 && Character.isLetterOrDigit(lineToCaret.charAt(at - 1))) {
                return out;
            }
            if (typed.chars().allMatch(c -> Character.isLetter(c) || c == ' ')) {
                String lower = typed.toLowerCase(Locale.ROOT);
                for (String block : BLOCKS) {
                    if (block.startsWith(lower)) {
                        out.add(new Item("@" + block, "block"));
                    }
                }
            }
            return out;
        }
        if (star >= 0 && insideTag(lineToCaret, star)) {
            String typed = lineToCaret.substring(star + 1);
            if (typed.chars().allMatch(Character::isLetter)) {
                String lower = typed.toLowerCase(Locale.ROOT);
                for (String directive : STRUCTURAL) {
                    if (directive.toLowerCase(Locale.ROOT).startsWith(lower)) {
                        out.add(new Item("*" + directive, "directive"));
                    }
                }
            }
        }
        return out;
    }

    /**
     * The partial ELEMENT name being typed after an open {@code <}, or
     * null when the caret is not at a tag-name position (Angular-top
     * arc: this is where the project's own component selectors
     * complete). Empty string means the {@code <} was just typed —
     * every selector is a candidate. Closing tags and positions past
     * the tag name (attributes) return null; the HTML provider owns
     * those.
     */
    public static String tagPrefix(String lineToCaret) {
        if (lineToCaret == null) {
            return null;
        }
        int open = lineToCaret.lastIndexOf('<');
        int close = lineToCaret.lastIndexOf('>');
        if (open < 0 || open < close) {
            return null;
        }
        String rest = lineToCaret.substring(open + 1);
        // closing tags (</app-) fall out here too: '/' is not a name
        // character — a separate startsWith("/") guard was proven
        // equivalent by mutation and deleted (the v1.333.0 rule)
        boolean nameShaped = rest.chars()
                .allMatch(c -> Character.isLetterOrDigit(c) || c == '-');
        return nameShaped ? rest : null;
    }

    /**
     * The element selectors matching {@code prefix}, from raw selector
     * strings as declared (comma-lists split, attribute forms like
     * {@code [appThing]} excluded — they are not tags), deduped and
     * sorted. Only DASHED names offer: a component selector without a
     * dash is nonstandard and would collide with real HTML tags.
     */
    public static List<String> selectorMatches(List<String> declaredSelectors,
            String prefix) {
        return declaredSelectors.stream()
                .flatMap(s -> java.util.Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("[")
                        && s.indexOf('-') > 0 && s.startsWith(prefix))
                .distinct()
                .sorted()
                .toList();
    }

    /** The insert offset: where the trigger character sits on the line. */
    public static int triggerOffset(String lineToCaret) {
        int at = lineToCaret.lastIndexOf('@');
        int star = lineToCaret.lastIndexOf('*');
        return Math.max(at, star);
    }

    /**
     * True inside an open tag: a {@code <} after the last {@code >}.
     * The same raw-prefix classification the HTML provider uses — pure,
     * no DOM parse.
     */
    private static boolean insideTag(String lineToCaret, int pos) {
        int open = lineToCaret.lastIndexOf('<', pos);
        int close = lineToCaret.lastIndexOf('>', pos);
        return open >= 0 && open > close;
    }

    /**
     * True when {@code file} lives in an Angular workspace: some parent
     * (bounded walk) carries {@code angular.json}. Completion must stay
     * silent in plain HTML — an {@code @}-trigger popping up in a
     * hand-written page would be noise claiming knowledge we don't have.
     */
    public static boolean inAngularWorkspace(File file) {
        return workspaceRoot(file) != null;
    }

    /** The directory carrying {@code angular.json} above {@code file}, or null. */
    public static File workspaceRoot(File file) {
        File dir = file == null ? null : file.getParentFile();
        for (int i = 0; dir != null && i < 12; i++, dir = dir.getParentFile()) {
            if (new File(dir, "angular.json").isFile()) {
                return dir;
            }
        }
        return null;
    }
}
