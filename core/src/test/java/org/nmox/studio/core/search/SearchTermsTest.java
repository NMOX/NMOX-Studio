package org.nmox.studio.core.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three failures that motivated {@link SearchTerms}, each pinned by
 * a test named after the real symptom, plus the precision rules that
 * stop the fix from becoming a new problem.
 */
class SearchTermsTest {

    @Nested
    @DisplayName("the bugs this class exists to fix")
    class RegressionsFromTheOldContainsMatcher {

        @Test
        @DisplayName("a phrase matches out of order and across a hyphen")
        void phrasesWork() {
            // PRISM's own description. Old matcher: "bundle size" was not
            // a substring of "Bundle-Size Gate", so the device could not
            // be found by its own name.
            String prism = "Bundle-Size Gate — weigh the build, hold the line";
            assertThat(SearchTerms.matches("bundle size", prism)).isTrue();
            assertThat(SearchTerms.matches("size bundle", prism)).isTrue();
            assertThat(SearchTerms.matches("gate bundle", prism)).isTrue();
        }

        @Test
        @DisplayName("a plural query finds singular text")
        void pluralsWork() {
            // TAIL: "Log Follower". "logs" is not a substring of it.
            assertThat(SearchTerms.matches("logs", "Log Follower — tail -f any file")).isTrue();
            // and the other direction
            assertThat(SearchTerms.matches("container", "containers volumes images")).isTrue();
        }

        @Test
        @DisplayName("a short term does not match inside a longer word")
        void shortTermsNeedAWordBoundary() {
            // The old matcher returned TAIL and ANVIL ("chain") for "ai".
            assertThat(SearchTerms.matches("ai", "Log Follower — tail -f any file")).isFalse();
            assertThat(SearchTerms.matches("ai", "Local EVM chain — anvil devnet")).isFalse();
            // but the device that really is about AI still answers
            assertThat(SearchTerms.matches("ai", "Error Explainer", "ai claude llm")).isTrue();
        }
    }

    @Nested
    @DisplayName("precision rules")
    class Precision {

        @Test
        @DisplayName("every term must match — one miss rejects the whole query")
        void allTermsRequired() {
            String veritas = "Test Harness — jest/vitest/mocha";
            assertThat(SearchTerms.matches("test harness", veritas)).isTrue();
            assertThat(SearchTerms.matches("test kangaroo", veritas)).isFalse();
        }

        @Test
        @DisplayName("a blank query matches nothing, so an empty box shows nothing")
        void blankMatchesNothing() {
            assertThat(SearchTerms.matches("", "anything")).isFalse();
            assertThat(SearchTerms.matches("   ", "anything")).isFalse();
            assertThat(SearchTerms.matches(null, "anything")).isFalse();
        }

        @Test
        @DisplayName("null haystack entries are skipped, not NPEs")
        void nullHaystacksTolerated() {
            assertThat(SearchTerms.matches("test", null, "Test Harness", null)).isTrue();
            assertThat(SearchTerms.matches("test", (String) null)).isFalse();
        }

        @Test
        @DisplayName("terms of three or more may land mid-word — pasted fragments still work")
        void longTermsMatchMidWord() {
            // The habit this protects: pasting part of a contract address
            // or a table name. Word-boundary-only matching would break it.
            String address = "Counter @ 0x5FbDB2315678afecb367f032d93F642f64180aa3";
            assertThat(SearchTerms.matches("5678afecb", address)).isTrue();
            assertThat(SearchTerms.matches("sql", "PostgreSQL")).isTrue();
        }

        @Test
        @DisplayName("separators split, so a path or URL is searchable by its parts")
        void separatorsSplit() {
            assertThat(SearchTerms.matches("nmox studio",
                    "/Users/david/vcs/git/github/nmox/NMOX-Studio")).isTrue();
            assertThat(SearchTerms.matches("5173", "http://localhost:5173")).isTrue();
            assertThat(SearchTerms.matches("localhost", "http://localhost:5173")).isTrue();
        }

        @Test
        @DisplayName("camelCase splits, but an all-caps word stays whole")
        void camelCaseSplits() {
            assertThat(SearchTerms.words("getUserById")).containsExactly("get", "user", "by", "id");
            // NPM must not become n/p/m
            assertThat(SearchTerms.words("NPM-9000")).containsExactly("npm", "9000");
        }

        @Test
        @DisplayName("short CJK queries match — each ideograph carries word-level meaning")
        void cjkQueriesMatch() {
            // CJK has no separators, so a name tokenizes as one word and
            // a 2-char query is neither a prefix nor long enough for the
            // Latin fallback. The old contains matcher handled these;
            // v1.216.0 restores them (the arc review's regression catch).
            assertThat(SearchTerms.matches("项目", "前端项目")).isTrue();
            assertThat(SearchTerms.matches("项", "前端项目")).isTrue();
            assertThat(SearchTerms.matches("前端", "前端项目")).isTrue();
            assertThat(SearchTerms.matches("数据", "前端项目")).isFalse();
        }

        @Test
        @DisplayName("a double-s word is not stemmed")
        void doubleSSurvives() {
            // "css" -> "cs" would be wrong; guard it
            assertThat(SearchTerms.matches("css", "CSS formatter")).isTrue();
            assertThat(SearchTerms.matches("class", "classes of things")).isTrue();
        }
    }

    @Test
    @DisplayName("score(): a whole-word hit outranks a prefix hit — 'compose' finds docker compose before composer (ledger 67)")
    void scoreRanksExactAboveLoose() {
        assertThat(SearchTerms.score("compose", "HARBOR", "Docker Engine", "docker compose containers"))
                .isEqualTo(SearchTerms.EXACT);
        assertThat(SearchTerms.score("compose", "ARTISAN", "Laravel console", "composer artisan php"))
                .isEqualTo(SearchTerms.LOOSE);
        assertThat(SearchTerms.score("compose", "VITALS", "Lighthouse floor", "lighthouse a11y"))
                .isEqualTo(SearchTerms.NO_MATCH);
        // plural/singular still counts as exact both ways; a two-term query is exact only when both are
        assertThat(SearchTerms.score("logs", "TAIL", "Log Follower", "log tail")).isEqualTo(SearchTerms.EXACT);
        assertThat(SearchTerms.score("docker compose", "HARBOR", "Docker Engine", "docker compose")).isEqualTo(SearchTerms.EXACT);
        assertThat(SearchTerms.score("docker composer", "HARBOR", "Docker Engine", "docker compose")).isEqualTo(SearchTerms.NO_MATCH);
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("accents are not a barrier to finding a thing")
    class AccentFolding {

        @Test
        @DisplayName("a name typed without its accents still finds it")
        void typedWithoutAccents() {
            assertThat(SearchTerms.matches("ubersetzung", "Übersetzung starten")).isTrue();
            assertThat(SearchTerms.matches("Uber", "Übersetzung starten")).isTrue();
            // Polish ł carries no Unicode decomposition — it needs naming
            assertThat(SearchTerms.matches("lacze", "Łącze do projektu")).isTrue();
            assertThat(SearchTerms.matches("cwiczenie", "Ćwiczenie w Studio")).isTrue();
            // German ß folds to ss, the way people type it
            assertThat(SearchTerms.matches("strasse", "Straße")).isTrue();
        }

        @Test
        @DisplayName("Vietnamese without tone marks — how Vietnamese is normally typed")
        void vietnameseWithoutTones() {
            assertThat(SearchTerms.matches("gia tac vu", "Giá tác vụ")).isTrue();
            assertThat(SearchTerms.matches("du an", "Studio dự án")).isTrue();
            // đ carries no decomposition either, and Vietnamese leans on it
            assertThat(SearchTerms.matches("dong", "Đóng cửa sổ")).isTrue();
        }

        @Test
        @DisplayName("folding widens what is found; it never narrows it")
        void theExactSpellingStillWorks() {
            assertThat(SearchTerms.matches("Übersetzung", "Übersetzung starten")).isTrue();
            assertThat(SearchTerms.matches("Ćwiczenie", "Ćwiczenie w Studio")).isTrue();
            assertThat(SearchTerms.matches("Giá", "Giá tác vụ")).isTrue();
            assertThat(SearchTerms.matches("проєкту", "Студія проєкту")).isTrue();
        }

        @Test
        @DisplayName("folding does not invent matches out of unrelated words")
        void noFalseHits() {
            assertThat(SearchTerms.matches("ubersetzung", "Projekt starten")).isFalse();
            assertThat(SearchTerms.matches("lacze", "Studio kontraktów")).isFalse();
            // the v1.215.0 junk rule survives folding: a short term must
            // still start a word, so "ai" cannot reach inside "TAIL"
            assertThat(SearchTerms.matches("ai", "Log Follower TAIL")).isFalse();
        }

        @Test
        @DisplayName("scripts with no combining marks are untouched")
        void otherScriptsAreLeftAlone() {
            assertThat(SearchTerms.fold("任务机架")).isEqualTo("任务机架");
            assertThat(SearchTerms.fold("टास्क रैक")).isEqualTo("टास्क रैक");
            assertThat(SearchTerms.fold("plain ascii")).isEqualTo("plain ascii");
        }
    }

    @Nested
    @DisplayName("the same name spelled two ways is one name")
    class Normalization {

        /**
         * macOS can hand back a filename DECOMPOSED — {@code U} plus a
         * combining diaeresis — while the user types it COMPOSED from their
         * keyboard, and the two are different strings. Accent folding
         * (v2.106.0) already normalizes as a side effect of how it works,
         * which means search has been normalization-insensitive since then
         * and nothing said so. Measured on a real APFS directory: a folder
         * created decomposed reads its name back decomposed, so both forms
         * genuinely reach this code.
         */
        @Test
        @DisplayName("a composed and a decomposed spelling fold to the same word")
        void bothNormalizationFormsFoldTheSame() {
            String composed = "\u00dcbersetzung";
            String decomposed = java.text.Normalizer.normalize(
                    composed, java.text.Normalizer.Form.NFD);
            assertThat(decomposed).as("the two really are different strings")
                    .isNotEqualTo(composed).hasSize(composed.length() + 1);
            assertThat(SearchTerms.score(composed, new String[] {"ubersetzung"}))
                    .as("typed without the umlaut, composed on disk").isPositive();
            assertThat(SearchTerms.score(decomposed, new String[] {"ubersetzung"}))
                    .as("typed without the umlaut, decomposed on disk").isPositive();
            assertThat(SearchTerms.score(decomposed, SearchTerms.words(composed).toArray(String[]::new)))
                    .as("one spelling must find the other, or a project found by "
                            + "its own name depends on which tool created the folder")
                    .isPositive();
        }
    }
}
