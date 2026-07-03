package org.nmox.studio.editor.completion;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.completion.JavaScriptCompletionProvider.JavaScriptContext;
import org.nmox.studio.editor.completion.JavaScriptCompletionProvider.JavaScriptMethod;
import org.nmox.studio.editor.completion.JavaScriptCompletionProvider.JavaScriptSnippet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JavaScript completion decides between member access (after a dot) and
 * general completion (keywords, globals, snippets), and filters each
 * dictionary by the word under the caret. These pin that classifier and
 * the matchers, which is where the useful logic lives.
 */
class JavaScriptCompletionProviderTest {

    // ---- context classification --------------------------------------------

    @Test
    @DisplayName("A bare word being typed is not dot-access; the prefix is the word")
    void bareWordContext() {
        JavaScriptContext c = JavaScriptCompletionProvider.analyzeContext("const fo");
        assertThat(c.isDotAccess).isFalse();
        assertThat(c.prefix).isEqualTo("fo");
        assertThat(c.objectName).isNull();
    }

    @Test
    @DisplayName("After 'object.' the caret is member access; object and (empty) prefix recovered")
    void dotAccessContext() {
        JavaScriptContext c = JavaScriptCompletionProvider.analyzeContext("console.");
        assertThat(c.isDotAccess).isTrue();
        assertThat(c.objectName).isEqualTo("console");
        assertThat(c.prefix).isEmpty();

        JavaScriptContext partial = JavaScriptCompletionProvider.analyzeContext("Math.ab");
        assertThat(partial.isDotAccess).isTrue();
        assertThat(partial.objectName).isEqualTo("Math");
        assertThat(partial.prefix).isEqualTo("ab");
    }

    @Test
    @DisplayName("A lone dot with no identifier before it recovers no object name")
    void danglingDot() {
        JavaScriptContext c = JavaScriptCompletionProvider.analyzeContext(" .");
        assertThat(c.isDotAccess).isTrue();
        // nothing walked back before the dot, so objectName stays null
        assertThat(c.objectName).isNull();
    }

    // ---- keyword matching --------------------------------------------------

    @Test
    @DisplayName("Keyword matching is prefix-based, case-insensitive, and sorted")
    void matchingKeywords() {
        List<String> co = JavaScriptCompletionProvider.matchingKeywords("co");
        assertThat(co).contains("const", "continue");
        assertThat(co).isSorted();
        assertThat(co).allMatch(k -> k.startsWith("co"));
        // 'ca' picks up case/catch, 'cl' picks up class — prefixes are exact
        assertThat(JavaScriptCompletionProvider.matchingKeywords("ca")).contains("case", "catch");
        assertThat(JavaScriptCompletionProvider.matchingKeywords("cl")).containsExactly("class");
        assertThat(JavaScriptCompletionProvider.matchingKeywords("CONST")).contains("const");
        assertThat(JavaScriptCompletionProvider.matchingKeywords("zzz")).isEmpty();
    }

    // ---- global object matching --------------------------------------------

    @Test
    @DisplayName("Global-object matching surfaces the built-in namespaces")
    void matchingGlobalObjects() {
        assertThat(JavaScriptCompletionProvider.matchingGlobalObjects(""))
                .contains("console", "document", "Array", "String", "Object", "Math", "JSON");
        assertThat(JavaScriptCompletionProvider.matchingGlobalObjects("cons")).containsExactly("console");
        // case-insensitive: 'ma' matches Math
        assertThat(JavaScriptCompletionProvider.matchingGlobalObjects("ma")).containsExactly("Math");
    }

    // ---- method matching ---------------------------------------------------

    @Test
    @DisplayName("Method matching is scoped to the named object and prefix-filtered")
    void matchingMethods() {
        List<JavaScriptMethod> consoleAll = JavaScriptCompletionProvider.matchingMethods("console", "");
        assertThat(consoleAll).extracting(m -> m.name).contains("log", "error", "warn", "info");

        List<JavaScriptMethod> consoleL = JavaScriptCompletionProvider.matchingMethods("console", "lo");
        assertThat(consoleL).extracting(m -> m.name).containsExactly("log");

        // an object we don't know contributes no methods
        assertThat(JavaScriptCompletionProvider.matchingMethods("myVar", "")).isEmpty();
        // null object name is safe
        assertThat(JavaScriptCompletionProvider.matchingMethods(null, "")).isEmpty();
    }

    // ---- snippet matching --------------------------------------------------

    @Test
    @DisplayName("Snippet matching is prefix-based on the trigger")
    void matchingSnippets() {
        List<JavaScriptSnippet> f = JavaScriptCompletionProvider.matchingSnippets("f");
        assertThat(f).extracting(s -> s.trigger).contains("func", "for", "forin", "forof", "fetch");
        assertThat(JavaScriptCompletionProvider.matchingSnippets("class"))
                .extracting(s -> s.trigger).containsExactly("class");
        assertThat(JavaScriptCompletionProvider.matchingSnippets("zzz")).isEmpty();
    }

    // ---- auto-query trigger ------------------------------------------------

    @Test
    @DisplayName("Single-char typing triggers on a letter or a dot only")
    void autoQueryTypes() {
        JavaScriptCompletionProvider p = new JavaScriptCompletionProvider();
        assertThat(p.getAutoQueryTypes(null, "a")).isNotZero();
        assertThat(p.getAutoQueryTypes(null, ".")).isNotZero();
        assertThat(p.getAutoQueryTypes(null, "1")).isZero();
        assertThat(p.getAutoQueryTypes(null, ";")).isZero();
        assertThat(p.getAutoQueryTypes(null, "ab")).isZero();
    }
}
