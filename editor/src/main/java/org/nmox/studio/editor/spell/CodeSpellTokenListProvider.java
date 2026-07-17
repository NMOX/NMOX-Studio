package org.nmox.studio.editor.spell;

import java.util.function.Predicate;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.spellchecker.spi.language.TokenList;
import org.netbeans.modules.spellchecker.spi.language.TokenListProvider;

/**
 * Spellcheck for code files the way IDEs are supposed to do it: only
 * the words inside comments are checked. Without this, the platform's
 * fallback treats the whole file as prose and flags every identifier -
 * printf, useState, impl - as a typo.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/typescript", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-java", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-c", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-cpp", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-python", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-ruby", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-rust", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-php5", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/sh", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-go", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-json", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-erlang", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-elixir", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-clojure", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-lisp", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-lua", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-swift", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-kotlin", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-csharp", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-fsharp", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-groovy", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-perl", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-r", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-julia", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-dart", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-scala", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-haskell", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-zig", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-gleam", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-nim", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-d", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-racket", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-elm", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-rescript", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-purescript", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-vlang", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-fortran", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-smalltalk", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-prolog", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-tcl", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-scheme", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-ocaml", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-crystal", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-solidity", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/coffeescript", service = TokenListProvider.class),
        @MimeRegistration(mimeType = "text/css", service = TokenListProvider.class),
        @MimeRegistration(mimeType = "text/x-scss", service = TokenListProvider.class),
        @MimeRegistration(mimeType = "text/x-less", service = TokenListProvider.class),
    // the config layer: values are not prose, only comments get spellcheck
    @MimeRegistration(mimeType = "text/x-ini", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-ignore", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-graphql", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-vue", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-svelte", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-astro", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-pug", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-handlebars", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-liquid", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-nginx-conf", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-apache-conf", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-makefile", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-protobuf", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-prisma", service = TokenListProvider.class),
    // platform-owned config mimes that ship without a comments-only binding
    @MimeRegistration(mimeType = "text/x-yaml", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-toml", service = TokenListProvider.class),
    @MimeRegistration(mimeType = "text/x-dockerfile", service = TokenListProvider.class)
})
public class CodeSpellTokenListProvider implements TokenListProvider {

    @Override
    public TokenList findTokenList(Document doc) {
        return new FilteredWordsTokenList(doc, CodeSpellTokenListProvider::isComment);
    }

    /**
     * Walks the document's token stream and yields only words that live
     * inside tokens the filter accepts — comments for code files, prose
     * for markdown. The word scan itself is shared; only the notion of
     * "spellcheckable token" differs per provider.
     */
    static final class FilteredWordsTokenList implements TokenList {

        private final Document doc;
        private final Predicate<Token<?>> spellcheckable;
        private int offset;
        private int wordStart;
        private CharSequence wordText;

        FilteredWordsTokenList(Document doc, Predicate<Token<?>> spellcheckable) {
            this.doc = doc;
            this.spellcheckable = spellcheckable;
        }

        @Override
        public void setStartOffset(int offset) {
            this.offset = offset;
        }

        @Override
        public boolean nextWord() {
            final boolean[] found = {false};
            doc.render(() -> {
                try {
                    found[0] = advance();
                } catch (BadLocationException ex) {
                    found[0] = false;
                }
            });
            return found[0];
        }

        private boolean advance() throws BadLocationException {
            TokenHierarchy<?> hierarchy = TokenHierarchy.get(doc);
            TokenSequence<?> ts = hierarchy == null ? null : hierarchy.tokenSequence();
            if (ts == null) {
                return false;
            }
            ts.move(offset);
            while (ts.moveNext()) {
                Token<?> token = ts.token();
                if (!spellcheckable.test(token)) {
                    continue;
                }
                CharSequence text = token.text();
                int base = ts.offset();
                int from = Math.max(0, offset - base);
                for (int i = from; i < text.length(); i++) {
                    if (Character.isLetter(text.charAt(i))) {
                        int start = i;
                        while (i < text.length() && Character.isLetter(text.charAt(i))) {
                            i++;
                        }
                        if (i - start >= 2) {
                            wordStart = base + start;
                            wordText = text.subSequence(start, i);
                            offset = base + i;
                            return true;
                        }
                    }
                }
                offset = base + text.length();
            }
            return false;
        }

        @Override
        public int getCurrentWordStartOffset() {
            return wordStart;
        }

        @Override
        public CharSequence getCurrentWordText() {
            return wordText;
        }

        @Override
        public void addChangeListener(ChangeListener l) {
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
        }
    }

    /**
     * TextMate tokens carry a single id (TEXTMATE); the real scope stack
     * lives in the "categories" property. The custom JS/TS lexer instead
     * names the category "comment" on the id itself.
     */
    private static boolean isComment(Token<?> token) {
        return isCommentScope(token.id().primaryCategory(),
                token.getProperty("categories"));
    }

    /**
     * Whether a token sits inside a comment, across both token shapes we
     * lex: the custom lexers name the id category "comment"; TextMate
     * exposes the scope stack as a "categories" property (a List whose
     * entries look like "comment.line.number-sign.ini"). Reading the
     * wrong property key here is what let prose spellcheck leak onto
     * config-file keys and values.
     */
    static boolean isCommentScope(String primaryCategory, Object categoriesProperty) {
        if (primaryCategory != null && primaryCategory.contains("comment")) {
            return true;
        }
        return categoriesProperty != null
                && categoriesProperty.toString().contains("comment");
    }
}
