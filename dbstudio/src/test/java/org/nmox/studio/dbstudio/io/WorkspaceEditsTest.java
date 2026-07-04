package org.nmox.studio.dbstudio.io;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO.HistoryEntry;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO.SavedQuery;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure list edits behind persistent history (shell semantics:
 * newest first, dedupe-to-front, capped), the saved-query shelf
 * (replace-by-name in place), the save-prompt default name, and the
 * .env-suggestion duplicate check.
 */
class WorkspaceEditsTest {

    // ---- withRun ----

    @Test
    @DisplayName("Runs prepend newest-first and never mutate the input list")
    void runsPrependNewestFirst() {
        List<HistoryEntry> initial = List.of(new HistoryEntry("SELECT 1;", "SQLite", 1));

        List<HistoryEntry> updated = WorkspaceEdits.withRun(initial,
                new HistoryEntry("SELECT 2;", "SQLite", 2));

        assertThat(updated).extracting(HistoryEntry::text)
                .containsExactly("SELECT 2;", "SELECT 1;");
        assertThat(initial).hasSize(1); // input untouched (it was immutable anyway)
    }

    @Test
    @DisplayName("Re-running a text moves it to the front with the new timestamp")
    void rerunMovesToFront() {
        List<HistoryEntry> history = List.of(
                new HistoryEntry("SELECT 2;", "SQLite", 2),
                new HistoryEntry("SELECT 1;", "SQLite", 1));

        List<HistoryEntry> updated = WorkspaceEdits.withRun(history,
                new HistoryEntry("SELECT 1;", "SQLite", 3));

        assertThat(updated).extracting(HistoryEntry::text)
                .containsExactly("SELECT 1;", "SELECT 2;");
        assertThat(updated.get(0).at()).isEqualTo(3);
    }

    @Test
    @DisplayName("The same text on a different engine is a separate entry")
    void engineDistinguishesRuns() {
        List<HistoryEntry> updated = WorkspaceEdits.withRun(
                List.of(new HistoryEntry("SELECT 1;", "MySQL", 1)),
                new HistoryEntry("SELECT 1;", "PostgreSQL", 2));

        assertThat(updated).hasSize(2);
    }

    @Test
    @DisplayName("History caps at HISTORY_CAP, dropping the oldest")
    void historyCaps() {
        List<HistoryEntry> history = new ArrayList<>();
        for (int i = 1; i <= DbWorkspaceIO.HISTORY_CAP + 5; i++) {
            history = WorkspaceEdits.withRun(history,
                    new HistoryEntry("SELECT " + i + ";", "SQLite", i));
        }

        assertThat(history).hasSize(DbWorkspaceIO.HISTORY_CAP);
        assertThat(history.get(0).text()).isEqualTo("SELECT 55;");
        assertThat(history.get(history.size() - 1).text()).isEqualTo("SELECT 6;");
    }

    @Test
    @DisplayName("Blank and null runs remember nothing")
    void blankRunsIgnored() {
        List<HistoryEntry> initial = List.of(new HistoryEntry("SELECT 1;", "SQLite", 1));

        assertThat(WorkspaceEdits.withRun(initial, null)).hasSize(1);
        assertThat(WorkspaceEdits.withRun(initial,
                new HistoryEntry("   \n", "SQLite", 2))).hasSize(1);
        assertThat(WorkspaceEdits.withRun(initial,
                new HistoryEntry(null, "SQLite", 2))).hasSize(1);
    }

    // ---- withSaved ----

    @Test
    @DisplayName("A new name appends; an existing name replaces in place")
    void savedReplaceByName() {
        List<SavedQuery> saved = WorkspaceEdits.withSaved(List.of(),
                new SavedQuery("top users", "SELECT 1;", "SQLite"));
        saved = WorkspaceEdits.withSaved(saved,
                new SavedQuery("slow queries", "SELECT 2;", "SQLite"));
        saved = WorkspaceEdits.withSaved(saved,
                new SavedQuery("top users", "SELECT 99;", "PostgreSQL"));

        assertThat(saved).extracting(SavedQuery::name)
                .containsExactly("top users", "slow queries"); // position kept
        assertThat(saved.get(0).text()).isEqualTo("SELECT 99;");
        assertThat(saved.get(0).engine()).isEqualTo("PostgreSQL");
    }

    @Test
    @DisplayName("A blank or null name has nothing to file under")
    void savedBlankNameIgnored() {
        List<SavedQuery> initial = List.of(new SavedQuery("keep", "SELECT 1;", ""));

        assertThat(WorkspaceEdits.withSaved(initial, null)).hasSize(1);
        assertThat(WorkspaceEdits.withSaved(initial,
                new SavedQuery("  ", "SELECT 2;", ""))).hasSize(1);
        assertThat(WorkspaceEdits.withSaved(initial,
                new SavedQuery(null, "SELECT 2;", ""))).hasSize(1);
    }

    // ---- defaultName ----

    @Test
    @DisplayName("The default save name is the first line, stripped, capped at 30 chars")
    void defaultNameRules() {
        assertThat(WorkspaceEdits.defaultName("SELECT 1;")).isEqualTo("SELECT 1;");
        assertThat(WorkspaceEdits.defaultName("  SELECT 1;  \nFROM t;"))
                .isEqualTo("SELECT 1;");
        assertThat(WorkspaceEdits.defaultName(
                "SELECT id, name, age, email FROM users WHERE age > 30;"))
                .isEqualTo("SELECT id, name, age, email FR")
                .hasSize(30);
        assertThat(WorkspaceEdits.defaultName("")).isEqualTo("query");
        assertThat(WorkspaceEdits.defaultName(null)).isEqualTo("query");
        assertThat(WorkspaceEdits.defaultName("   \n\n")).isEqualTo("query");
    }

    // ---- alreadyConfigured ----

    private static EnvConnections.Suggestion mysql() {
        return new EnvConnections.Suggestion(DbEngine.MYSQL, "localhost", 3306,
                "shop", "root", null);
    }

    @Test
    @DisplayName("A matching server connection is recognized — host case-insensitive")
    void serverSuggestionMatches() {
        List<ConnectionSpec> specs = List.of(new ConnectionSpec("id", "shop db",
                DbEngine.MYSQL, "LOCALHOST", 3306, "shop", "root", ""));

        assertThat(WorkspaceEdits.alreadyConfigured(mysql(), specs)).isTrue();
    }

    @Test
    @DisplayName("A spec without an explicit port matches on the engine default")
    void defaultPortMatches() {
        List<ConnectionSpec> specs = List.of(new ConnectionSpec("id", "shop db",
                DbEngine.MYSQL, "localhost", -1, "shop", "root", ""));

        assertThat(WorkspaceEdits.alreadyConfigured(mysql(), specs)).isTrue();
    }

    @Test
    @DisplayName("Engine, database, host or port differences mean no match")
    void differencesDoNotMatch() {
        assertThat(WorkspaceEdits.alreadyConfigured(mysql(), List.of(new ConnectionSpec(
                "id", "n", DbEngine.MARIADB, "localhost", 3306, "shop", "", ""))))
                .as("different engine").isFalse();
        assertThat(WorkspaceEdits.alreadyConfigured(mysql(), List.of(new ConnectionSpec(
                "id", "n", DbEngine.MYSQL, "localhost", 3306, "warehouse", "", ""))))
                .as("different database").isFalse();
        assertThat(WorkspaceEdits.alreadyConfigured(mysql(), List.of(new ConnectionSpec(
                "id", "n", DbEngine.MYSQL, "db.example.com", 3306, "shop", "", ""))))
                .as("different host").isFalse();
        assertThat(WorkspaceEdits.alreadyConfigured(mysql(), List.of(new ConnectionSpec(
                "id", "n", DbEngine.MYSQL, "localhost", 3307, "shop", "", ""))))
                .as("different port").isFalse();
        assertThat(WorkspaceEdits.alreadyConfigured(mysql(), List.of())).isFalse();
        assertThat(WorkspaceEdits.alreadyConfigured(null, List.of())).isFalse();
    }

    @Test
    @DisplayName("SQLite matches on the file path the suggestion carries as its database")
    void sqliteMatchesOnFilePath() {
        EnvConnections.Suggestion sqlite = new EnvConnections.Suggestion(
                DbEngine.SQLITE, "", -1, "/tmp/app.db", "", null);
        List<ConnectionSpec> matching = List.of(new ConnectionSpec("id", "cache",
                DbEngine.SQLITE, "", -1, "", "", "/tmp/app.db"));
        List<ConnectionSpec> other = List.of(new ConnectionSpec("id", "cache",
                DbEngine.SQLITE, "", -1, "", "", "/tmp/other.db"));

        assertThat(WorkspaceEdits.alreadyConfigured(sqlite, matching)).isTrue();
        assertThat(WorkspaceEdits.alreadyConfigured(sqlite, other)).isFalse();
    }

    @Test
    @DisplayName("Specs without a modeled engine never match (Services entries)")
    void nullEngineSpecsSkipped() {
        List<ConnectionSpec> specs = List.of(new ConnectionSpec("nb:x", "services",
                null, "localhost", 3306, "shop", "root", null));

        assertThat(WorkspaceEdits.alreadyConfigured(mysql(), specs)).isFalse();
    }
}
