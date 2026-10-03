package org.nmox.studio.tools.vscode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.Containment;
import org.nmox.studio.core.util.Jsonc;

/**
 * A project's {@code .vscode/extensions.json}, read: the pure half
 * behind the VS Code extensions sheet. It answers "which extensions
 * does this repository recommend" and nothing else; what each one means
 * here is {@link ExtensionEquivalents}' question.
 *
 * <p><b>What is read.</b> The file's {@code recommendations} array, each
 * string entry an extension id ({@code publisher.name}). VS Code treats
 * ids without regard to case, so each is folded to lower case and a
 * repeat is dropped. {@code unwantedRecommendations} is not read: it
 * tells VS Code which of ITS OWN suggestions to keep quiet about in
 * this workspace, which is not a recommendation by the repository and
 * means nothing in a product that suggests none. A multi-root
 * {@code .code-workspace} file's {@code extensions} block is not read
 * either.
 *
 * <p><b>The ids are somebody else's text.</b> A clone brings the file,
 * so the read is bounded ({@link #MAX_BYTES} through
 * {@link BoundedReads}), at most {@link #MAX_IDS} ids are kept (the
 * rest are counted, and the sheet says how many it left out), and an
 * entry that is not shaped like {@code publisher.name} is kept as it
 * was written — control and formatting characters folded to spaces, cut at
 * {@link #MAX_ID_LENGTH} code points — and marked, so it can only ever
 * be shown as text and reported as not known: it is never looked up.
 * Every sink that paints an id guards it ({@code PlainTables}).
 *
 * <p>The file is JSONC ({@link Jsonc}). One that cannot be read or does
 * not parse answers {@link Recommendations#unreadable()} and logs why at
 * INFO; the notice then says nothing about extensions and the sheet says
 * the file could not be read.
 */
public final class VsCodeExtensions {

    private static final Logger LOG = Logger.getLogger(VsCodeExtensions.class.getName());

    /** An honest extensions.json is a few hundred bytes; a megabyte is a mistake or malice. */
    static final long MAX_BYTES = 1024L * 1024;

    /** The most ids the sheet lists. The largest real files recommend a few dozen. */
    static final int MAX_IDS = 200;

    /** The longest a malformed entry is shown, in code points. */
    static final int MAX_ID_LENGTH = 120;

    /** Where VS Code keeps a folder's recommendations, relative to the folder. */
    static final String RELATIVE_PATH = ".vscode/extensions.json";

    /**
     * {@code publisher.name} as the Marketplace allows it, after folding
     * to lower case: letters, digits and hyphens on both sides, with dots
     * and underscores allowed in the name. Anything else is not an id.
     */
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}\\.[a-z0-9][a-z0-9._-]{0,63}");

    private VsCodeExtensions() {
    }

    /**
     * One recommendation.
     *
     * @param id         the id, lower case; for a malformed entry, the text
     *                   as written, made safe to show
     * @param wellFormed whether it is shaped like an extension id; a
     *                   malformed entry is never looked up
     */
    record Entry(String id, boolean wellFormed) {
    }

    /**
     * What the file recommends.
     *
     * @param entries    the recommendations, in file order, at most {@link #MAX_IDS}
     * @param notShown   how many more distinct recommendations the file holds
     * @param unreadable the file is there and could not be read as JSON
     */
    record Recommendations(List<Entry> entries, int notShown, boolean unreadable) {

        static final Recommendations NONE = new Recommendations(List.of(), 0, false);
        static final Recommendations UNREADABLE = new Recommendations(List.of(), 0, true);

        /** Every distinct recommendation in the file, listed or not. */
        int total() {
            return entries.size() + notShown;
        }
    }

    /** What {@code dir}'s {@code .vscode/extensions.json} recommends; none when there is no such file. */
    static Recommendations read(File dir) {
        if (dir == null) {
            return Recommendations.NONE;
        }
        File written = new File(new File(dir, ".vscode"), "extensions.json");
        if (!written.isFile()) {
            return Recommendations.NONE;
        }
        // a .vscode folder or extensions.json that is a link out of the
        // project is somebody else's file: not read, and said so
        File file = Containment.resolve(dir, RELATIVE_PATH);
        if (file == null || !file.isFile()) {
            LOG.log(Level.INFO, "{0} leads out of the project; it was not read", written);
            return Recommendations.UNREADABLE;
        }
        try {
            return parse(BoundedReads.read(file, MAX_BYTES));
        } catch (IOException unreadable) {
            LOG.log(Level.INFO, "{0} was not read: {1}", new Object[] {file, unreadable.getMessage()});
            return Recommendations.UNREADABLE;
        }
    }

    /** The recommendations in an extensions.json's text. */
    static Recommendations parse(String text) {
        JSONArray array;
        try {
            array = new JSONObject(Jsonc.strip(text)).optJSONArray("recommendations");
        } catch (JSONException | StackOverflowError malformed) {
            // org.json recurses per nesting level: a file of ten thousand
            // opening brackets is a stack overflow, not an exception
            LOG.log(Level.INFO, "{0} does not parse: {1}", new Object[] {RELATIVE_PATH, malformed.toString()});
            return Recommendations.UNREADABLE;
        }
        if (array == null) {
            return Recommendations.NONE;
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Entry> entries = new ArrayList<>();
        int notShown = 0;
        for (int i = 0; i < array.length(); i++) {
            if (!(array.opt(i) instanceof String written) || written.isBlank()) {
                continue;
            }
            String id = written.strip().toLowerCase(Locale.ROOT);
            boolean wellFormed = ID.matcher(id).matches();
            if (!wellFormed) {
                id = shown(written);
            }
            if (!seen.add(id)) {
                continue;
            }
            if (entries.size() < MAX_IDS) {
                entries.add(new Entry(id, wellFormed));
            } else {
                notShown++;
            }
        }
        return new Recommendations(List.copyOf(entries), notShown, false);
    }

    /**
     * A malformed entry made safe to show: control characters, line
     * breaks and formatting characters become spaces, so it stays on its
     * row and reads in the order it was written, and it is cut at
     * {@link #MAX_ID_LENGTH} code points with an ellipsis, on a code-point
     * boundary.
     */
    static String shown(String written) {
        StringBuilder folded = new StringBuilder(written.length());
        written.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            // FORMAT covers the bidi overrides (U+202E), which would paint
            // the rest of the row backwards
            boolean breaks = Character.isISOControl(cp) || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR || type == Character.FORMAT;
            folded.appendCodePoint(breaks ? ' ' : cp);
        });
        String text = folded.toString().strip();
        if (text.codePointCount(0, text.length()) <= MAX_ID_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_ID_LENGTH)) + '\u2026';
    }
}
