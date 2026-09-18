package org.nmox.studio.rack.sharing;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A rack as pasted text — the clipboard door. A rack file is small enough to
 * paste into a chat, an issue or an IRC channel, which is how most
 * configuration actually travels between developers; this reads one back.
 * Whatever is on a clipboard is a stranger's text: capped before it is parsed,
 * and refused by name rather than by org.json's own exception.
 */
public final class RackText {

    /** The same ceiling a rack file has ({@code RackIO.MAX_PATCH_BYTES}), in characters. */
    public static final int MAX_CHARS = 8 * 1024 * 1024;

    private RackText() {
    }

    /** Why pasted text is not a rack; the message is for the status line. */
    public static final class NotARackException extends Exception {
        public enum Reason { EMPTY, TOO_LARGE, NOT_JSON, NO_DEVICES }

        private final Reason reason;

        NotARackException(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    /**
     * Parses pasted text as a rack document. Tolerates what chat clients add:
     * surrounding whitespace, a byte-order mark, and a Markdown code fence
     * (with or without a language tag) around the JSON.
     */
    public static JSONObject parse(String pasted) throws NotARackException {
        if (pasted == null || pasted.isBlank()) {
            throw new NotARackException(NotARackException.Reason.EMPTY);
        }
        if (pasted.length() > MAX_CHARS) {
            throw new NotARackException(NotARackException.Reason.TOO_LARGE);
        }
        String text = unfence(pasted.strip());
        JSONObject doc;
        try {
            doc = new JSONObject(text);
        } catch (JSONException | StackOverflowError notJson) {
            // a deeply nested paste overflows org.json's recursive parser: still "not a rack"
            throw new NotARackException(NotARackException.Reason.NOT_JSON);
        }
        JSONArray devices = doc.optJSONArray("devices");
        if (devices == null) {
            throw new NotARackException(NotARackException.Reason.NO_DEVICES);
        }
        return doc;
    }

    /** {@code ```json … ```} → the inside; anything else unchanged. */
    static String unfence(String text) {
        String t = text;
        if (!t.isEmpty() && t.charAt(0) == 0xFEFF) {
            t = t.substring(1).strip();
        }
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNewline = t.indexOf('\n');
        int close = t.lastIndexOf("```");
        if (firstNewline < 0 || close <= firstNewline) {
            return t;
        }
        return t.substring(firstNewline + 1, close).strip();
    }

    /** A rack as text worth pasting: the JSON alone, two-space indented, newline-terminated. */
    public static String render(JSONObject shared) {
        return shared.toString(2) + "\n";
    }
}
