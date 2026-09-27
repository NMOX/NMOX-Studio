package org.nmox.studio.dbstudio.io;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a second developer's merge does to {@code .nmoxdb.json} (3.4,
 * question 1), on the shapes a real {@code git merge} leaves.
 */
public class TeamDbWorkspaceTest {

    /** Git's markers, built so no tracked line of this file starts with one. */
    public static String conflicted() {
        return "{\n  \"version\": 1,\n  \"connections\": [\n"
                + "<".repeat(7) + " HEAD\n"
                + "    {\"id\": \"a\", \"name\": \"shop\", \"engine\": \"SQLITE\", \"filePath\": \"data/shop.db\"}\n"
                + "=".repeat(7) + "\n"
                + "    {\"id\": \"b\", \"name\": \"orders\", \"engine\": \"POSTGRES\", \"host\": \"db\"}\n"
                + ">".repeat(7) + " feature/bob\n"
                + "  ],\n  \"saved\": []\n}\n";
    }

    @Test
    @DisplayName("a file holding git's merge conflict is left as it is and bound read-only")
    void conflictedFileIsLeftAlone(@TempDir File dir) throws Exception {
        File f = new File(dir, DbWorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), conflicted(), StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(f.toPath());

        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir);

        assertThat(outcome.conflicted()).isTrue();
        assertThat(outcome.readOnly()).isTrue();
        assertThat(outcome.backup()).as("not corrupt, so nothing is moved aside").isNull();
        assertThat(dir.list()).containsExactly(DbWorkspaceIO.FILENAME);
        assertThatThrownBy(() -> DbWorkspaceIO.save(dir, List.of()))
                .as("the connections-only writer refuses too");
        assertThat(Files.readAllBytes(f.toPath())).isEqualTo(before);
    }

    @Test
    @DisplayName("an engine a newer NMOX Studio wrote binds read-only instead of being dropped")
    void newerEngineIsReadOnly(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, DbWorkspaceIO.FILENAME).toPath(), """
            {"version": 1, "connections": [
              {"id": "a", "name": "shop", "engine": "SQLITE", "filePath": "x.db"},
              {"id": "b", "name": "graph", "engine": "NEO4J", "host": "g"}]}
            """, StandardCharsets.UTF_8);

        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir);

        assertThat(outcome.newerFormat()).isTrue();
        assertThat(outcome.readOnly())
                .as("the next save used to drop the NEO4J connection for everyone").isTrue();
        assertThat(outcome.workspace().connections()).extracting(ConnectionSpec::name)
                .containsExactly("shop");
    }

    @Test
    @DisplayName("two saved queries sharing a name after a merge are both kept, the later renamed")
    void keepBothKeepsBoth(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, DbWorkspaceIO.FILENAME).toPath(), """
            {"version": 1, "connections": [], "saved": [
              {"name": "report", "text": "SELECT 1;", "engine": "MySQL"},
              {"name": "report (2)", "text": "SELECT 2;", "engine": "MySQL"},
              {"name": "report", "text": "SELECT 3;", "engine": "MySQL"},
              {"name": "report", "text": "SELECT 1;", "engine": "MySQL"}]}
            """, StandardCharsets.UTF_8);

        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir);

        assertThat(outcome.workspace().saved()).containsExactly(
                new DbWorkspaceIO.SavedQuery("report", "SELECT 1;", "MySQL"),
                new DbWorkspaceIO.SavedQuery("report (2)", "SELECT 2;", "MySQL"),
                new DbWorkspaceIO.SavedQuery("report (3)", "SELECT 3;", "MySQL"));
        assertThat(outcome.renamedSaved())
                .as("the rename is said, not only logged — and an exact copy just collapses")
                .containsExactly("report → report (3)");
        assertThat(outcome.readOnly()).as("a heal is not a refusal").isFalse();
    }

    @Test
    @DisplayName("a second corrupt file never overwrites the first rescue")
    void secondRescueKeepsTheFirst(@TempDir File dir) throws Exception {
        File f = new File(dir, DbWorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), "{ first", StandardCharsets.UTF_8);
        File first = DbWorkspaceIO.loadWorkspaceGuarded(dir).backup();
        Files.writeString(f.toPath(), "{ second", StandardCharsets.UTF_8);
        File second = DbWorkspaceIO.loadWorkspaceGuarded(dir).backup();

        assertThat(Files.readString(first.toPath())).isEqualTo("{ first");
        assertThat(Files.readString(second.toPath())).isEqualTo("{ second");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("the query history leaves the shared file; a pre-3.4 file's rows are read once")
    void historyIsPersonal(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, DbWorkspaceIO.FILENAME).toPath(), """
            {"version": 1, "connections": [],
             "history": [{"text": "SELECT secret FROM payroll;", "engine": "PostgreSQL", "at": 3}]}
            """, StandardCharsets.UTF_8);
        DbWorkspaceIO.Workspace legacy = DbWorkspaceIO.loadWorkspaceGuarded(dir).workspace();

        assertThat(legacy.history()).extracting(DbWorkspaceIO.HistoryEntry::text)
                .containsExactly("SELECT secret FROM payroll;");
        assertThat(DbWorkspaceIO.toJson(legacy))
                .as("a teammate's clone no longer receives what this person ran")
                .doesNotContain("payroll");
        assertThat(DbWorkspaceIO.personalHistory(DbWorkspaceIO.personalJson(legacy.history())))
                .isEqualTo(legacy.history());
        assertThat(DbWorkspaceIO.personalHistory(null)).as("no document yet").isNull();
    }

    @Test
    @DisplayName("an ordinary file is writable")
    void ordinaryIsWritable(@TempDir File dir) throws Exception {
        DbWorkspaceIO.save(dir, new DbWorkspaceIO.Workspace(List.of(new ConnectionSpec(
                "a", "shop", DbEngine.SQLITE, "", -1, "", "", "x.db")), List.of(), List.of()));
        assertThat(DbWorkspaceIO.loadWorkspaceGuarded(dir).readOnly()).isFalse();
    }
}
