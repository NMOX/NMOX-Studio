package org.nmox.studio.editor.spell;

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
    @MimeRegistration(mimeType = "text/x-kotlin", service = TokenListProvider.class)
})
public class CodeSpellTokenListProvider implements TokenListProvider {

    @Override
    public TokenList findTokenList(Document doc) {
        return new CommentWordsTokenList(doc);
    }

    /**
     * Walks the document's token stream and yields only words that live
     * inside comment tokens. Detection is lexer-agnostic: a token counts
     * as comment when its id's primary category mentions "comment" (our
     * JS/TS lexer) or any of its TextMate scopes do.
     */
    static final class CommentWordsTokenList implements TokenList {

        private final Document doc;
        private int offset;
        private int wordStart;
        private CharSequence wordText;

        CommentWordsTokenList(Document doc) {
            this.doc = doc;
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
                if (!isComment(token)) {
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

        private static boolean isComment(Token<?> token) {
            String category = token.id().primaryCategory();
            if (category != null && category.contains("comment")) {
                return true;
            }
            // TextMate tokens: the real category lives in the scope list property
            Object scopes = token.getProperty("scopes");
            return scopes != null && scopes.toString().contains("comment");
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
}
