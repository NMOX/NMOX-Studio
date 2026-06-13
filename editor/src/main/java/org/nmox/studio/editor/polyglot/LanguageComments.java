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
            Map.entry("text/x-properties", "#"),
            Map.entry("text/x-erlang", "%"),
            Map.entry("text/x-elixir", "#"),
            Map.entry("text/x-clojure", ";;"),
            Map.entry("text/x-lisp", ";;"),
            Map.entry("text/x-lua", "--"),
            Map.entry("text/x-swift", "//"),
            Map.entry("text/x-kotlin", "//"),
            Map.entry("text/x-csharp", "//"),
            Map.entry("text/x-fsharp", "//"),
            Map.entry("text/x-groovy", "//"),
            Map.entry("text/x-perl", "#"),
            Map.entry("text/x-r", "#"),
            Map.entry("text/x-julia", "#"),
            Map.entry("text/x-dart", "//"),
            Map.entry("text/x-scala", "//"),
            Map.entry("text/x-haskell", "--"),
            Map.entry("text/x-zig", "//"),
            Map.entry("text/x-crystal", "#"),
            // CSS proper has only block comments; its preprocessors add //
            Map.entry("text/x-scss", "//"),
            Map.entry("text/x-less", "//"),
            // the config layer: .editorconfig, ignore files, infra configs
            Map.entry("text/x-ini", "#"),
            Map.entry("text/x-ignore", "#"),
            Map.entry("text/x-graphql", "#"),
            Map.entry("text/x-pug", "//"),
            Map.entry("text/x-nginx-conf", "#"),
            Map.entry("text/x-makefile", "#"),
            Map.entry("text/x-protobuf", "//"),
            Map.entry("text/x-prisma", "//"),
            Map.entry("text/x-dockerfile", "#"),
            Map.entry("text/x-sql", "--"));

    private LanguageComments() {
    }

    /** The line-comment prefix for a mime, or null when unknown. */
    public static String lineCommentFor(String mimeType) {
        return mimeType == null ? null : LINE_COMMENT.get(mimeType);
    }
}
