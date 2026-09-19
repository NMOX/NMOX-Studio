package org.nmox.studio.rack.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What a rack file says about itself: the card a reader meets before the
 * wiring. Until v2.179.0 a rack's only name was its filename, which is fine
 * for a file beside a project and useless for one passed between people — a
 * rack worth sharing has to be able to say what it is for.
 *
 * <p>The card rides the {@code shared} header of a rack file (see
 * {@link RackShare}), beside the product version:
 *
 * <pre>{@code
 * "shared": {
 *   "product": "2.179.0",
 *   "name": "Rust watch loop",
 *   "description": "REFLEX watches src/, VERITAS runs cargo test on every save.",
 *   "author": "",
 *   "kinds": ["RUST"],
 *   "requires": ["cargo"]
 * }
 * }</pre>
 *
 * <p>Every field is optional and every field is a STRANGER'S TEXT: read
 * tolerantly (a wrong type is an absent field, never an exception), stripped
 * of control characters (a newline in a name could forge a line of the import
 * manifest), and clipped by code points. {@code author} is empty unless the
 * sender typed one — a name is never inferred from the machine. {@code kinds}
 * are {@code ProjectKind} names and only steer ranking; an unknown one is
 * kept and matches nothing. {@code requires} are BARE tool names the gallery
 * looks up on the PATH and never runs — anything with a path separator or
 * whitespace is dropped (the doctor.d rule).
 */
public record RackCard(String name, String description, String author,
        List<String> kinds, List<String> requires) {

    /** What the sender called the rack. */
    public static final String NAME = "name";
    /** What it does, in a sentence or two. */
    public static final String DESCRIPTION = "description";
    /** Who wrote it, empty unless they typed it. */
    public static final String AUTHOR = "author";
    /** The {@code ProjectKind} names it says it fits. */
    public static final String KINDS = "kinds";
    /** The bare tool names it needs on the PATH. */
    public static final String REQUIRES = "requires";

    /**
     * Every field of a card, declared where it is WRITTEN and READ: exactly
     * what {@link #writeTo} writes and {@link #of} looks for. The community-rack
     * gate ({@code gallery.RackJudge}) permits a header key only when this set
     * or {@link #isLanguageSibling} knows it, and keeps no list of its own — it
     * did until v2.179.2, so a field added here would have made the gate refuse
     * every rack that used it as an "unknown header key" (the second-home
     * defect v2.179.1 removed one authority over).
     */
    public static final Set<String> FIELDS = Set.of(NAME, DESCRIPTION, AUTHOR, KINDS, REQUIRES);

    /** The fields a rack may translate — the ones {@link #localized} reads a {@code .<lang>} sibling of. */
    public static final Set<String> TRANSLATED = Set.of(NAME, DESCRIPTION);

    public static final int MAX_NAME = 60;
    public static final int MAX_DESCRIPTION = 400;
    public static final int MAX_AUTHOR = 60;
    public static final int MAX_LIST = 12;
    public static final int MAX_ITEM = 40;

    /** A file that says nothing about itself: every plain Save Patch file. */
    public static final RackCard EMPTY = new RackCard("", "", "", List.of(), List.of());

    public RackCard {
        name = clean(name, MAX_NAME);
        description = clean(description, MAX_DESCRIPTION);
        author = clean(author, MAX_AUTHOR);
        kinds = cleanList(kinds, false);
        requires = cleanList(requires, true);
    }

    /** The card of a rack document in the reader's language; {@link #EMPTY} when it carries none. */
    public static RackCard of(JSONObject doc) {
        return of(doc, Locale.getDefault().getLanguage());
    }

    /**
     * The card as a reader of {@code language} meets it. A rack may carry
     * {@code name.<lang>} / {@code description.<lang>} siblings (the learning
     * catalogue's mechanism, v2.133.0): the racks that ship with the product
     * speak the reader's language, a stranger's file says what its author
     * wrote, and an absent or blank sibling falls back to the base field —
     * FIELD BY FIELD, so translating only the description is a complete thing
     * to do. The siblings are read, never written: Share builds a fresh header
     * from what the sender typed.
     */
    public static RackCard of(JSONObject doc, String language) {
        JSONObject header = doc == null ? null : doc.optJSONObject(RackShare.SHARED);
        if (header == null) {
            return EMPTY;
        }
        return new RackCard(localized(header, NAME, language), localized(header, DESCRIPTION, language),
                text(header, AUTHOR),
                strings(header.optJSONArray(KINDS)), strings(header.optJSONArray(REQUIRES)));
    }

    /**
     * {@code name.de}, {@code description.pt-br}: a translation sibling of a
     * {@link #TRANSLATED} field. The rule lives beside the reading it serves
     * ({@link #localized}), so a reader of a rack file — the gate included —
     * asks rather than spelling these prefixes a second time.
     */
    public static boolean isLanguageSibling(String key) {
        if (key == null) {
            return false;
        }
        for (String field : TRANSLATED) {
            if (key.startsWith(field + ".") && isLanguageTag(key, field.length() + 1)) {
                return true;
            }
        }
        return false;
    }

    /** {@code de}, {@code pt-br}, {@code zh_hans}: two to eight lower-case letters, {@code -} or {@code _}. */
    private static boolean isLanguageTag(String key, int from) {
        int length = key.length() - from;
        if (length < 2 || length > 8) {
            return false;
        }
        for (int i = from; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!((c >= 'a' && c <= 'z') || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static String localized(JSONObject header, String key, String language) {
        if (language != null && !language.isBlank()) {
            String theirs = text(header, key + "." + language.toLowerCase(Locale.ROOT));
            if (!theirs.isBlank()) {
                return theirs;
            }
        }
        return text(header, key);
    }

    /** Writes the fields that say something into a {@code shared} header; blank ones are left out. */
    public void writeTo(JSONObject header) {
        putIfSaid(header, NAME, name);
        putIfSaid(header, DESCRIPTION, description);
        putIfSaid(header, AUTHOR, author);
        if (!kinds.isEmpty()) {
            header.put(KINDS, new JSONArray(kinds));
        }
        if (!requires.isEmpty()) {
            header.put(REQUIRES, new JSONArray(requires));
        }
    }

    public boolean isBlank() {
        return name.isEmpty() && description.isEmpty() && author.isEmpty()
                && kinds.isEmpty() && requires.isEmpty();
    }

    /** True when this rack says it fits {@code kind} (a {@code ProjectKind} name), case-insensitively. */
    public boolean fits(String kind) {
        if (kind == null) {
            return false;
        }
        String k = kind.toUpperCase(Locale.ROOT);
        return kinds.contains(k);
    }

    private static void putIfSaid(JSONObject header, String key, String value) {
        if (!value.isEmpty()) {
            header.put(key, value);
        }
    }

    /** Only a JSON string is text: a number or an object in a name's place is an absent name. */
    private static String text(JSONObject header, String key) {
        return header.opt(key) instanceof String s ? s : "";
    }

    private static List<String> strings(JSONArray array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            // the read is bounded too: a hostile million-entry array costs MAX_LIST looks
            for (int i = 0; i < array.length() && out.size() < MAX_LIST; i++) {
                if (array.opt(i) instanceof String s) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Control characters become spaces, runs of whitespace one space, the rest clipped by code points. */
    static String clean(String raw, int max) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), max * 2));
        boolean space = false;
        int kept = 0;
        for (int i = 0; i < raw.length() && kept < max;) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            // a lone surrogate is malformed text, not a character to keep
            boolean lone = cp <= 0xFFFF && Character.isSurrogate((char) cp);
            if (Character.isISOControl(cp) || Character.isWhitespace(cp) || lone
                    || cp == 0x2028 || cp == 0x2029) {
                space = sb.length() > 0;
                continue;
            }
            if (space) {
                sb.append(' ');
                kept++;
                space = false;
                if (kept >= max) {
                    break;
                }
            }
            sb.appendCodePoint(cp);
            kept++;
        }
        // a clip that lands on the joining space must not leave it dangling
        return sb.toString().stripTrailing();
    }

    private static List<String> cleanList(List<String> raw, boolean bareToolNames) {
        if (raw == null) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String item : raw) {
            if (out.size() >= MAX_LIST) {
                break;
            }
            String v = clean(item, MAX_ITEM);
            if (v.isEmpty()) {
                continue;
            }
            if (bareToolNames) {
                if (!isBareTool(v)) {
                    continue;
                }
            } else {
                v = v.toUpperCase(Locale.ROOT);
            }
            out.add(v);
        }
        return List.copyOf(out);
    }

    /** {@code cargo}, {@code docker-compose}, {@code python3.12}: a name to look up, never a path or a command line. */
    static boolean isBareTool(String v) {
        if (v.isEmpty() || v.startsWith("-") || v.startsWith(".")) {
            return false;
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '+';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
