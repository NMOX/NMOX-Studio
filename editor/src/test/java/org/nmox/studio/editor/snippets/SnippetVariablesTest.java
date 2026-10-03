package org.nmox.studio.editor.snippets;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** VS Code's snippet variables, each against a stated editor. */
class SnippetVariablesTest {

    /** Friday 2 October 2026, 09:05:07, two hours east of Greenwich. */
    private static final ZonedDateTime FRIDAY =
            ZonedDateTime.of(2026, 10, 2, 9, 5, 7, 0, ZoneOffset.ofHours(2));

    private static SnippetContext ctx() {
        return SnippetContext.at(FRIDAY, new Random(42))
                .withFile("/work/shop/src/app/user.service.ts", "/work/shop")
                .withLine("    const total = sum(items);", 41, "total", null);
    }

    private static String value(String name) {
        return SnippetVariables.resolve(name, ctx());
    }

    @Test
    @DisplayName("the file: name, base, folder, path, path from the project")
    void fileVariables() {
        assertThat(value("TM_FILENAME")).isEqualTo("user.service.ts");
        assertThat(value("TM_FILENAME_BASE")).isEqualTo("user.service");
        assertThat(value("TM_DIRECTORY")).isEqualTo("/work/shop/src/app");
        assertThat(value("TM_DIRECTORY_BASE")).isEqualTo("app");
        assertThat(value("TM_FILEPATH")).isEqualTo("/work/shop/src/app/user.service.ts");
        assertThat(value("RELATIVE_FILEPATH")).isEqualTo("src/app/user.service.ts");
        assertThat(value("WORKSPACE_NAME")).isEqualTo("shop");
        assertThat(value("WORKSPACE_FOLDER")).isEqualTo("/work/shop");
    }

    @Test
    @DisplayName("a dotfile keeps its whole name as its base, and Windows paths are paths")
    void pathEdges() {
        assertThat(SnippetVariables.withoutExtension(".eslintrc")).isEqualTo(".eslintrc");
        assertThat(SnippetVariables.withoutExtension("README")).isEqualTo("README");
        assertThat(SnippetVariables.baseName("C:\\work\\shop\\a.ts")).isEqualTo("a.ts");
        assertThat(SnippetVariables.dirName("C:\\work\\shop\\a.ts")).isEqualTo("C:\\work\\shop");
        assertThat(SnippetVariables.relative("C:\\work\\shop\\src\\a.ts", "C:\\work\\shop\\")).isEqualTo("src\\a.ts");
        assertThat(SnippetVariables.relative("/work/shopping/a.ts", "/work/shop"))
                .as("a sibling folder with the same beginning is not inside").isEqualTo("/work/shopping/a.ts");
        assertThat(SnippetVariables.relative("/elsewhere/a.ts", null)).isEqualTo("/elsewhere/a.ts");
    }

    @Test
    @DisplayName("the caret's line: text, word, index from zero, number from one")
    void lineVariables() {
        assertThat(value("TM_CURRENT_LINE")).isEqualTo("    const total = sum(items);");
        assertThat(value("TM_CURRENT_WORD")).isEqualTo("total");
        assertThat(value("TM_LINE_INDEX")).isEqualTo("41");
        assertThat(value("TM_LINE_NUMBER")).isEqualTo("42");
    }

    @Test
    @DisplayName("the clock, zero-padded, with English names")
    void clockVariables() {
        assertThat(value("CURRENT_YEAR")).isEqualTo("2026");
        assertThat(value("CURRENT_YEAR_SHORT")).isEqualTo("26");
        assertThat(value("CURRENT_MONTH")).isEqualTo("10");
        assertThat(value("CURRENT_MONTH_NAME")).isEqualTo("October");
        assertThat(value("CURRENT_MONTH_NAME_SHORT")).isEqualTo("Oct");
        assertThat(value("CURRENT_DATE")).isEqualTo("02");
        assertThat(value("CURRENT_DAY_NAME")).isEqualTo("Friday");
        assertThat(value("CURRENT_DAY_NAME_SHORT")).isEqualTo("Fri");
        assertThat(value("CURRENT_HOUR")).isEqualTo("09");
        assertThat(value("CURRENT_MINUTE")).isEqualTo("05");
        assertThat(value("CURRENT_SECOND")).isEqualTo("07");
        assertThat(value("CURRENT_SECONDS_UNIX")).isEqualTo(Long.toString(FRIDAY.toEpochSecond()));
        assertThat(value("CURRENT_TIMEZONE_OFFSET")).isEqualTo("+02:00");
    }

    @Test
    @DisplayName("the names and the digits do not follow the reader's language")
    void clockIsNotTheReaders() {
        Locale before = Locale.getDefault();
        try {
            for (String tag : new String[] {"ar", "hi-IN-u-nu-deva", "de-DE", "ja-JP"}) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                assertThat(value("CURRENT_DAY_NAME")).as(tag).isEqualTo("Friday");
                assertThat(value("CURRENT_MONTH_NAME")).as(tag).isEqualTo("October");
                assertThat(value("CURRENT_YEAR") + "-" + value("CURRENT_MONTH") + "-" + value("CURRENT_DATE"))
                        .as(tag).isEqualTo("2026-10-02");
                assertThat(value("TM_LINE_NUMBER")).as(tag).isEqualTo("42");
            }
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("random: six digits, six hex digits, a version-4 UUID")
    void randomVariables() {
        assertThat(value("RANDOM")).matches("[0-9]{6}");
        assertThat(value("RANDOM_HEX")).matches("[0-9a-f]{6}");
        assertThat(value("UUID")).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        SnippetContext small = SnippetContext.at(FRIDAY, new Random() {
            @Override
            public int nextInt(int bound) {
                return 7;
            }
        });
        assertThat(SnippetVariables.resolve("RANDOM", small)).as("padded, never shortened").isEqualTo("000007");
        assertThat(SnippetVariables.resolve("RANDOM_HEX", small)).isEqualTo("000007");
    }

    @Test
    @DisplayName("comment marks come from the context; a language without one leaves the variable unset")
    void commentVariables() {
        SnippetContext ts = ctx().withComments("//", "/*", "*/");
        assertThat(SnippetVariables.resolve("LINE_COMMENT", ts)).isEqualTo("//");
        assertThat(SnippetVariables.resolve("BLOCK_COMMENT_START", ts)).isEqualTo("/*");
        assertThat(SnippetVariables.resolve("BLOCK_COMMENT_END", ts)).isEqualTo("*/");
        assertThat(value("LINE_COMMENT")).isNull();
        assertThat(SnippetVariables.known("LINE_COMMENT")).isTrue();
    }

    @Test
    @DisplayName("the clipboard: its text; unset when empty, over the bound, or when reading it throws")
    void clipboard() {
        assertThat(SnippetVariables.resolve("CLIPBOARD", ctx().withClipboard(() -> "pasted"))).isEqualTo("pasted");
        assertThat(SnippetVariables.resolve("CLIPBOARD", ctx().withClipboard(() -> null))).isNull();
        assertThat(SnippetVariables.resolve("CLIPBOARD", ctx().withClipboard(() -> ""))).isNull();
        assertThat(SnippetVariables.resolve("CLIPBOARD", ctx().withClipboard(
                () -> "x".repeat(SnippetVariables.MAX_CLIPBOARD_CHARS + 1)))).isNull();
        assertThat(SnippetVariables.resolve("CLIPBOARD", ctx().withClipboard(() -> {
            throw new IllegalStateException("clipboard is busy");
        }))).isNull();
    }

    @Test
    @DisplayName("nothing selected, no file: the variables are unset, not empty strings or the word null")
    void unset() {
        SnippetContext bare = SnippetContext.at(FRIDAY, new Random(1));
        for (String name : new String[] {"TM_SELECTED_TEXT", "SELECTION", "TM_CURRENT_WORD", "TM_FILENAME",
            "TM_FILENAME_BASE", "TM_DIRECTORY", "TM_FILEPATH", "RELATIVE_FILEPATH", "WORKSPACE_NAME",
            "WORKSPACE_FOLDER", "CLIPBOARD"}) {
            assertThat(SnippetVariables.resolve(name, bare)).as(name).isNull();
            assertThat(SnippetVariables.known(name)).as(name).isTrue();
        }
        assertThat(SnippetVariables.resolve("TM_SELECTED_TEXT",
                bare.withLine("x", 0, null, "picked"))).isEqualTo("picked");
    }

    @Test
    @DisplayName("a name VS Code does not have is not known, and has no value")
    void unknown() {
        assertThat(SnippetVariables.known("cursor")).isFalse();
        assertThat(SnippetVariables.known("TM_NOPE")).isFalse();
        assertThat(value("TM_NOPE")).isNull();
    }
}
