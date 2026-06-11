package org.nmox.studio.editor.polyglot;

import java.util.Map;

/**
 * Line-comment syntax per MIME type - the one piece of language
 * knowledge the typing tools need. Mimes not listed here don't get
 * comment toggling.
 */
public final class LanguageComments {

    private static final Map<String, String> LINE_COMMENT = Map.ofEntries(
            Map.entry("text/javascript", "//"),
            Map.entry("text/typescript", "//"),
            Map.entry("text/x-java", "//"),
            Map.entry("text/x-c", "//"),
            Map.entry("text/x-cpp", "//"),
            Map.entry("text/x-rust", "//"),
            Map.entry("text/x-php5", "//"),
            Map.entry("text/x-go", "//"),
            Map.entry("text/x-python", "#"),
            Map.entry("text/x-ruby", "#"),
            Map.entry("text/sh", "#"),
            Map.entry("text/x-toml", "#"),
            Map.entry("text/x-yaml", "#"),
            Map.entry("text/x-properties", "#"));

    private LanguageComments() {
    }

    /** The line-comment prefix for a mime, or null when unknown. */
    public static String lineCommentFor(String mimeType) {
        return mimeType == null ? null : LINE_COMMENT.get(mimeType);
    }
}
