package org.nmox.studio.editor.polyglot;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Drives the polyglot provider's query end to end against real
 * documents. The provider owns the mimes JS/HTML don't: it must offer
 * that language's keywords plus the buffer's own identifiers, and stay
 * silent for mimes the primary providers already serve.
 */
class PolyglotQueryTest {

    private static List<CompletionItem> query(String mime, String text, int caret)
            throws BadLocationException {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, text, null);
        doc.putProperty("mimeType", mime);
        List<CompletionItem> items = new ArrayList<>();
        CompletionResultSet rs = Mockito.mock(CompletionResultSet.class);
        Mockito.when(rs.addItem(any())).thenAnswer(inv -> {
            items.add(inv.getArgument(0));
            return true;
        });
        new PolyglotCompletionProvider.Query().query(rs, doc, caret);
        Mockito.verify(rs).finish();
        return items;
    }

    private static List<String> names(List<CompletionItem> items) {
        return items.stream().map(i -> i.getSortText().toString()).toList();
    }

    @Test
    @DisplayName("Lua buffer offers Lua keywords and the buffer's own identifiers for the prefix")
    void luaKeywordsAndIdentifiers() throws BadLocationException {
        String src = "local wiring = 1\nlocal wired = 2\nwi";
        List<CompletionItem> items = query("text/x-lua", src, src.length());
        assertThat(names(items)).contains("wiring", "wired"); // buffer identifiers
        // the fragment being typed is not offered back
        assertThat(names(items)).doesNotContain("wi");

        // keywords ride the same query: 'fu' surfaces Lua's function
        String kw = "fu";
        assertThat(names(query("text/x-lua", kw, kw.length()))).contains("function");
    }

    @Test
    @DisplayName("Rust buffer offers fn/for for 'f' — the keyword table is per-mime")
    void perMimeKeywordTables() throws BadLocationException {
        String src = "f";
        // no earlier closing token: prefixAt walks from the caret only
        List<String> rust = names(query("text/x-rust", "let x = 1;\nf", 12));
        assertThat(rust).contains("fn", "for");
        assertThat(rust).doesNotContain("function"); // that's Lua/JS vocabulary
    }

    @Test
    @DisplayName("Mimes owned by the primary providers stay silent here")
    void ownedMimesStaySilent() throws BadLocationException {
        assertThat(query("text/javascript", "co", 2)).isEmpty();
        assertThat(query("text/x-unknown-mime", "co", 2)).isEmpty();
    }

    @Test
    @DisplayName("Explicit invocation only: no auto-popup, and only completion queries make a task")
    void gates() {
        PolyglotCompletionProvider p = new PolyglotCompletionProvider();
        assertThat(p.getAutoQueryTypes(null, "a")).isZero();
        assertThat(p.createTask(CompletionProvider.DOCUMENTATION_QUERY_TYPE, null)).isNull();
        assertThat(p.createTask(CompletionProvider.COMPLETION_QUERY_TYPE, null)).isNotNull();
    }
}
