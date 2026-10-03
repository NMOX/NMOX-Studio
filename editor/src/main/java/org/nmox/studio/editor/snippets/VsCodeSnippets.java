package org.nmox.studio.editor.snippets;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONParserConfiguration;
import org.json.JSONTokener;
import org.nmox.studio.core.util.Jsonc;
import org.nmox.studio.editor.snippets.SnippetBody.Refused;

/**
 * The text of a {@code .code-snippets} file, read as VS Code reads it:
 * JSON with comments, one entry per snippet,
 *
 * <pre>
 * {
 *   "Log to console": {
 *     "prefix": "log",                    // or ["log", "cl"]
 *     "body": ["console.log('$1');", "$0"],  // or one string
 *     "description": "Log output to console",
 *     "scope": "javascript,typescript"    // absent: every language
 *   }
 * }
 * </pre>
 *
 * and, as VS Code allows, one level of grouping: an entry with no
 * {@code body} of its own is a group of snippets.
 *
 * <p>The file is somebody else's. So the reading is bounded and nothing
 * is taken half: at most {@link #MAX_SNIPPETS} snippets a file, a body
 * of at most {@link SnippetBody#MAX_CHARS} characters, and a snippet
 * that cannot be honoured whole (no body, a transform whose pattern is
 * unsafe, placeholders nested without end) is left out BY NAME in
 * {@link Parsed#notes}, while every other snippet of the file still
 * loads. A snippet with no {@code prefix} is left out too: in VS Code it
 * is reachable only through the Insert Snippet command, and here there
 * is nothing to type that would offer it.
 *
 * <p>Entries are read in the order of their names, so which 500 of 600
 * are kept is the same on every machine. A name written twice keeps the
 * later entry, as in VS Code.
 *
 * <p>Pure: text in, snippets out.
 */
public final class VsCodeSnippets {

    /** A file's snippets past this many are not read. */
    public static final int MAX_SNIPPETS = 500;

    /** A prefix longer than this is not a prefix anybody types. */
    static final int MAX_PREFIX_CHARS = 100;

    /** How many "left out" notes a file may add before the rest are counted, not listed. */
    static final int MAX_NOTES = 12;

    /** An editor with no file, no selection and an empty line, at the epoch: where a body is tried once when it is read. */
    private static final SnippetContext NOWHERE = SnippetContext.at(
            java.time.ZonedDateTime.ofInstant(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
            new java.security.SecureRandom());

    private VsCodeSnippets() {
    }

    /**
     * One snippet.
     *
     * @param name the entry's name in its file
     * @param prefixes what is typed to find it, never empty
     * @param description its description, or the empty string
     * @param scopes the VS Code language ids it applies to; empty means every language
     * @param body the parsed body
     * @param firstLine the first line of the body as written, for the completion list
     * @param source the file's name, for the completion list
     */
    public record Snippet(String name, List<String> prefixes, String description, Set<String> scopes,
            SnippetBody body, String firstLine, String source) {

        /**
         * Whether the snippet is offered in a file of VS Code language
         * {@code languageId}. No scope is every language; a language this
         * product has no id for (null) gets only the unscoped ones.
         */
        public boolean appliesTo(String languageId) {
            return scopes.isEmpty() || (languageId != null && scopes.contains(languageId));
        }
    }

    /**
     * What a file held.
     *
     * @param snippets the snippets that can be honoured
     * @param notes one line for each thing left out, and why
     */
    public record Parsed(List<Snippet> snippets, List<String> notes) {
    }

    /** The file is not a snippet file at all; the message says why. */
    public static final class Malformed extends Exception {

        private static final long serialVersionUID = 1L;

        Malformed(String why) {
            super(why);
        }
    }

    /**
     * Reads a snippet file's text.
     *
     * @param text the file's content
     * @param source the file's name, carried on every snippet
     * @throws Malformed when the text is not a JSON object
     */
    public static Parsed parse(String text, String source) throws Malformed {
        return parse(text, source, TRIAL_NANOS);
    }

    /**
     * How long one file's snippets may take, between them, to be tried
     * once when the file is read (see {@code Reader.read}), in
     * nanoseconds. A file can hold five hundred bodies whose transforms
     * each stop just short of their own clock; past this the rest are
     * kept without the trial, and that is said. What a trial would have
     * refused is then refused when the snippet is accepted.
     */
    static final long TRIAL_NANOS = 1_000_000_000L;

    /** {@link #parse(String, String)} with the file's trial time stated (the tests' seam). */
    static Parsed parse(String text, String source, long trialNanos) throws Malformed {
        JSONObject root;
        try {
            root = new JSONObject(new JSONTokener(Jsonc.strip(text)),
                    new JSONParserConfiguration().withOverwriteDuplicateKey(true));
        } catch (JSONException | StackOverflowError ex) {
            String why = ex.getMessage();
            throw new Malformed(why == null ? "it is not a JSON object" : why);
        }
        Reader reader = new Reader(source, trialNanos);
        for (String name : new TreeSet<>(root.keySet())) {
            Object entry = root.opt(name);
            if (!(entry instanceof JSONObject o)) {
                reader.note("\"" + clip(name) + "\" is not an object");
                continue;
            }
            if (o.has("body")) {
                reader.read(name, o);
                continue;
            }
            // a group: one level, as VS Code reads it
            boolean any = false;
            for (String inner : new TreeSet<>(o.keySet())) {
                if (o.opt(inner) instanceof JSONObject io && io.has("body")) {
                    reader.read(inner, io);
                    any = true;
                }
            }
            if (!any) {
                reader.note("\"" + clip(name) + "\" has no body");
            }
        }
        return reader.done();
    }

    private static String clip(String s) {
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 60 ? oneLine
                : oneLine.substring(0, oneLine.offsetByCodePoints(0, oneLine.codePointCount(0, 57))) + "…";
    }

    private static final class Reader {

        private final String source;
        private final List<Snippet> snippets = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private int unlisted;
        private int overCap;
        private int noPrefix;
        private int untried;
        private final long trialNanos;
        private long trialLeft;

        Reader(String source, long trialNanos) {
            this.source = source;
            this.trialNanos = trialNanos;
            this.trialLeft = trialNanos;
        }

        void note(String note) {
            if (notes.size() < MAX_NOTES) {
                notes.add(note);
            } else {
                unlisted++;
            }
        }

        void read(String name, JSONObject o) {
            if (snippets.size() >= MAX_SNIPPETS) {
                overCap++;
                return;
            }
            String text = lines(o.opt("body"));
            if (text == null) {
                note("\"" + clip(name) + "\" has a body that is neither a string nor a list of strings");
                return;
            }
            List<String> prefixes = prefixes(o.opt("prefix"));
            if (prefixes.isEmpty()) {
                noPrefix++;
                return;
            }
            SnippetBody body;
            try {
                body = SnippetBody.parse(text);
                // a trial in an editor with nothing set: what can never be
                // inserted whole (a stop that is only ever transformed) is
                // left out here, by name, instead of refusing at every accept
                if (trialLeft > 0) {
                    long began = System.nanoTime();
                    try {
                        SnippetTemplates.toCodeTemplate(body, NOWHERE,
                                Math.min(trialLeft, SnippetTemplates.BUDGET_NANOS));
                    } finally {
                        trialLeft -= System.nanoTime() - began;
                    }
                } else {
                    untried++;
                }
            } catch (Refused refused) {
                note("\"" + clip(name) + "\" is left out because " + refused.getMessage());
                return;
            }
            String description = lines(o.opt("description"));
            int nl = text.indexOf('\n');
            snippets.add(new Snippet(name, prefixes, description == null ? "" : description,
                    scopes(o.opt("scope")), body, nl < 0 ? text : text.substring(0, nl), source));
        }

        Parsed done() {
            if (noPrefix > 0) {
                notes.add(noPrefix + (noPrefix == 1 ? " snippet has" : " snippets have")
                        + " no prefix, so nothing typed would offer " + (noPrefix == 1 ? "it" : "them"));
            }
            if (overCap > 0) {
                notes.add(overCap + " more past the first " + MAX_SNIPPETS + " are not read");
            }
            if (untried > 0) {
                notes.add(untried + (untried == 1 ? " snippet was" : " snippets were")
                        + " kept without being tried: the file used its " + (trialNanos / 1_000_000L)
                        + " ms for that on the ones before");
            }
            if (unlisted > 0) {
                notes.add("and " + unlisted + " more left out");
            }
            return new Parsed(List.copyOf(snippets), List.copyOf(notes));
        }

        /** A string, or a list of strings as lines; null for anything else. Line ends are {@code \n}. */
        private static String lines(Object value) {
            if (value instanceof String s) {
                return s.replace("\r\n", "\n").replace('\r', '\n');
            }
            if (value instanceof JSONArray a) {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < a.length(); i++) {
                    if (!(a.opt(i) instanceof String s)) {
                        return null;
                    }
                    if (b.length() > SnippetBody.MAX_CHARS) {
                        // already past the bound the parser refuses at: it will
                        // refuse this text by its length, so stop building it
                        break;
                    }
                    if (i > 0) {
                        b.append('\n');
                    }
                    b.append(s.replace("\r\n", "\n").replace('\r', '\n'));
                }
                return b.toString();
            }
            return null;
        }

        private static List<String> prefixes(Object value) {
            List<String> out = new ArrayList<>();
            if (value instanceof String s) {
                add(out, s);
            } else if (value instanceof JSONArray a) {
                for (int i = 0; i < a.length() && out.size() < 16; i++) {
                    if (a.opt(i) instanceof String s) {
                        add(out, s);
                    }
                }
            }
            return List.copyOf(out);
        }

        private static void add(List<String> out, String prefix) {
            if (!prefix.isBlank() && prefix.length() <= MAX_PREFIX_CHARS
                    && prefix.indexOf('\n') < 0 && !out.contains(prefix)) {
                out.add(prefix);
            }
        }

        private static Set<String> scopes(Object value) {
            Set<String> out = new LinkedHashSet<>();
            if (value instanceof String s) {
                for (String id : s.split(",")) {
                    if (!id.isBlank()) {
                        out.add(id.strip());
                    }
                }
            }
            return Set.copyOf(out);
        }
    }
}
