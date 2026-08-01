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
}
