package org.nmox.studio.dbstudio.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The console run history: newest first, capped at 50, shell-history
 * dedupe (same text + engine moves to the front), blanks never
 * recorded.
 */
class ConsoleHistoryTest {

    @Test
    @DisplayName("entries come back newest first")
    void newestFirst() {
        ConsoleHistory history = new ConsoleHistory();
        history.add("SELECT 1;", "PostgreSQL", 1_000);
        history.add("SELECT 2;", "PostgreSQL", 2_000);
        history.add("SELECT 3;", "PostgreSQL", 3_000);

        assertThat(history.entries())
                .extracting(ConsoleHistory.Entry::text)
                .containsExactly("SELECT 3;", "SELECT 2;", "SELECT 1;");
    }

    @Test
    @DisplayName("caps at 50 entries, dropping the oldest")
    void capsAtFifty() {
        ConsoleHistory history = new ConsoleHistory();
        for (int i = 1; i <= 60; i++) {
            history.add("SELECT " + i + ";", "SQLite", i);
        }

        assertThat(history.entries()).hasSize(ConsoleHistory.CAPACITY);
        assertThat(history.entries().get(0).text()).isEqualTo("SELECT 60;");
        assertThat(history.entries().get(49).text()).isEqualTo("SELECT 11;");
    }

    @Test
    @DisplayName("re-running the same text moves it to the front instead of duplicating")
    void dedupeMovesToFront() {
        ConsoleHistory history = new ConsoleHistory();
        history.add("SELECT 1;", "MySQL", 1_000);
        history.add("SELECT 2;", "MySQL", 2_000);
        history.add("SELECT 1;", "MySQL", 3_000);

        assertThat(history.entries())
                .extracting(ConsoleHistory.Entry::text)
                .containsExactly("SELECT 1;", "SELECT 2;");
        assertThat(history.entries().get(0).timestamp()).isEqualTo(3_000);
    }

    @Test
    @DisplayName("the same text on a different engine is a separate entry")
    void engineDistinguishes() {
        ConsoleHistory history = new ConsoleHistory();
        history.add("SELECT 1;", "MySQL", 1_000);
        history.add("SELECT 1;", "PostgreSQL", 2_000);

        assertThat(history.entries()).hasSize(2);
    }

    @Test
    @DisplayName("blank and null texts are never recorded")
    void blanksIgnored() {
        ConsoleHistory history = new ConsoleHistory();
        history.add(null, "MySQL", 1_000);
        history.add("", "MySQL", 2_000);
        history.add("   \n", "MySQL", 3_000);

        assertThat(history.entries()).isEmpty();
    }

    @Test
    @DisplayName("a null engine is normalized, not an NPE")
    void nullEngineNormalized() {
        ConsoleHistory history = new ConsoleHistory();
        history.add("SELECT 1;", null, 1_000);

        assertThat(history.entries()).hasSize(1);
        assertThat(history.entries().get(0).engine()).isEmpty();
    }
}
