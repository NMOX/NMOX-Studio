package org.nmox.studio.editor.symbols;

import org.netbeans.spi.jumpto.support.NameMatcher;

/**
 * The pure matching rules between a typed query and an outline symbol
 * name — split from {@link NmoxSymbolProvider} so the rules stay on
 * the tested surface while the provider stays a thin platform-SPI
 * adapter (excluded from the coverage surface at the root pom, the
 * house law: floors answer to testable code, never the other way).
 */
final class SymbolMatch {

    private SymbolMatch() {
    }

    /**
     * A symbol whose outline name carries a stylesheet sigil
     * ({@code .hero-banner}, {@code #masthead}, {@code @media}) must be
     * findable by the name a person would TYPE — the v1.215.0
     * findability law: a punctuation prefix must not empty the search.
     * The display keeps the honest full selector; only the match
     * consults the sigil-free form too.
     */
    static boolean matches(NameMatcher matcher, String name) {
        if (matcher.accept(name)) {
            return true;
        }
        String bare = sigilFree(name);
        if (!bare.equals(name) && matcher.accept(bare)) {
            return true;
        }
        // the platform's quick-search bridge strips every character that
        // is not a Java identifier part from the QUERY before consulting
        // providers (decompiled: GoToSymbolProvider.removeNonJavaChars),
        // so ⌘I "hero-banner" reaches us as "herobanner" — a Java-era
        // assumption CSS/HTML names break. Fold the candidate the same
        // way so the hyphenated name is still found; the dialog path
        // never folds its query, and lanes one and two own it.
        String folded = identifierFold(bare);
        return !folded.equals(bare) && matcher.accept(folded);
    }

    /** Strips a leading run of stylesheet sigils; never empties a name. */
    static String sigilFree(String name) {
        int i = 0;
        while (i < name.length() && ".#@".indexOf(name.charAt(i)) >= 0) {
            i++;
        }
        return i == 0 || i >= name.length() ? name : name.substring(i);
    }

    /** The bridge's own query folding, applied to the candidate. */
    static String identifierFold(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isJavaIdentifierPart(c)) {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? name : sb.toString();
    }
}
