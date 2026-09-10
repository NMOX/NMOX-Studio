package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every place the product decides where a word ends says whose word it is
 * (v2.115.0) — the spawn-ledger idiom applied to word boundaries.
 *
 * <p>{@code Character.isLetter} says a combining mark is not a letter. In
 * Latin that is harmless, because an accent has already been folded away by
 * the time anything splits. In Devanagari, Thai, Hebrew and their
 * neighbours a mark is a vowel: टास्क is one word, and a boundary rule that
 * does not know it cut that word into ट, स and क — three one-letter tokens
 * that match almost anything. Search did exactly that from v1.215.0 until
 * v2.114.0, in a language the product has spoken since v2.97.0.
 *
 * <p>The lesson is not "always keep the marks". It is that the right answer
 * depends on WHOSE text is being split, and the two answers are opposite:
 *
 * <ul>
 * <li><b>Human text</b> — what a person typed in their own language. A mark
 * belongs to the word in front of it, or the reader cannot find what they
 * wrote.</li>
 * <li><b>Code syntax</b> — an identifier, a selector, an abbreviation. The
 * LANGUAGE decides what a name may contain, not the reader's script, and a
 * boundary that drifted from the spec would break the editor for everyone.
 * These are correct as they are.</li>
 * </ul>
 *
 * <p>So each site names which it is, and a new one fails the build until
 * someone decides. Enumeration beats recollection.
 */
class WordBoundaryLedgerTest {

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    /** Splits text a PERSON wrote, so a combining mark is part of the word. */
    private static final Map<String, String> HUMAN_TEXT = Map.of(
            "SearchTerms.java",
            "the one term matcher behind every search surface: a mark joins the word in "
            + "front of it (v2.114.0), and a mark with no word in front of it separates",
            "CodeSpellTokenListProvider.java",
            "scans comments and strings for words to spell-check — and DELIBERATELY keeps "
            + "the letters-only rule: its two-letter minimum means a Devanagari word "
            + "produces no token at all, so nothing is checked and nothing is flagged. "
            + "Teaching it the marks would hand every Hindi word to a dictionary that "
            + "does not have it and squiggle the lot. The same rule that broke search "
            + "protects this consumer, because what tokenizing COSTS differs by consumer");

    /** Splits text a LANGUAGE defines, where the spec is the authority. */
    private static final String CODE_SYNTAX =
            "a code-syntax boundary: the language spec says what an identifier, selector, "
            + "property or abbreviation may contain, and the reader's script does not "
            + "change it. Widening these would break the editor for everyone";

    private static final List<String> CODE_SYNTAX_FILES = List.of(
            "ClassicApiMatcher.java", "CssClassCompletionItem.java", "CssClasses.java",
            "CssColors.java", "CssCompletionProvider.java", "CssFutures.java", "CssTokens.java",
            "Emmet.java", "EnvKeys.java", "ExplainQueries.java", "Highlights.java",
            "IrcClient.java", "JavaScriptCompletionProvider.java", "JavaScriptLexer.java",
            "JsOccurrencesHighlighter.java", "Keyframes.java", "NgSelectorHyperlink.java",
            "NgTemplateCompletion.java", "NgTemplateHyperlinkEnabler.java", "PairLogic.java",
            "PolyglotCompletionProvider.java", "Routes.java", "SimpleSelectParser.java",
            "TaskfileParser.java", "UpdateBuilder.java");

    @Test
    @DisplayName("every word boundary in the product says whose word it is")
    void everyBoundaryIsClassified() throws IOException {
        List<String> unclassified = new ArrayList<>();
        int found = 0;
        for (Path p : sources()) {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            if (!body.contains("isLetterOrDigit") && !body.contains("isLetter(")) {
                continue;
            }
            found++;
            String file = p.getFileName().toString();
            if (!HUMAN_TEXT.containsKey(file) && !CODE_SYNTAX_FILES.contains(file)) {
                unclassified.add(file);
            }
        }
        assertThat(found).as("the census should find the product's word boundaries").isGreaterThan(20);
        assertThat(unclassified)
                .as("say whose word this splits: a person's (a mark joins it) or a language's "
                        + "(the spec decides). " + CODE_SYNTAX)
                .isEmpty();
    }

    @Test
    @DisplayName("the human-text sites really handle a mark, each in its own way")
    void humanTextSitesAnswerForMarks() throws IOException {
        List<String> silent = new ArrayList<>();
        for (Path p : sources()) {
            String file = p.getFileName().toString();
            if (!HUMAN_TEXT.containsKey(file)) {
                continue;
            }
            String body = Files.readString(p, StandardCharsets.UTF_8);
            // either it names the mark types, or it explains in the file why
            // letters-only is the right answer for its consumer
            boolean answers = body.contains("NON_SPACING_MARK")
                    || body.contains("COMBINING_SPACING_MARK")
                    || body.toLowerCase(java.util.Locale.ROOT).contains("combining mark");
            if (!answers) {
                silent.add(file + " — " + HUMAN_TEXT.get(file));
            }
        }
        assertThat(silent)
                .as("a site that splits a person's own language must have decided about marks "
                        + "in writing, where the next reader will find it")
                .isEmpty();
    }

    @Test
    @DisplayName("no ghosts: a classified file that has left the product is a stale blessing")
    void theListsHoldNoGhosts() throws IOException {
        List<String> names = sources().stream().map(p -> p.getFileName().toString()).toList();
        List<String> ghosts = new ArrayList<>();
        for (String f : HUMAN_TEXT.keySet()) {
            if (!names.contains(f)) {
                ghosts.add(f);
            }
        }
        for (String f : CODE_SYNTAX_FILES) {
            if (!names.contains(f)) {
                ghosts.add(f);
            }
        }
        assertThat(ghosts).as("named in the ledger but no longer in the product").isEmpty();
    }

    private static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                out.addAll(files.filter(f -> f.toString().endsWith(".java")).toList());
            }
        }
        return out;
    }
}
