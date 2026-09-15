package org.nmox.studio.editor.polyglot;

import java.util.List;
import java.util.Set;

/**
 * Test-scope bridge to the provider's package-private pure pieces, for
 * language vertical tests that live beside their language config rather
 * than in this package.
 */
public final class PolyglotCompletionAccess {

    private PolyglotCompletionAccess() {
    }

    public static Set<String> keywords(String mime) {
        return PolyglotCompletionProvider.KEYWORDS.get(mime);
    }

    public static String prefixAt(String text, int offset, String mime) {
        return PolyglotCompletionProvider.prefixAt(text, offset, mime);
    }

    public static List<String> matching(String mime, String prefix) {
        return PolyglotCompletionProvider.matchingKeywords(keywords(mime), prefix);
    }
}
