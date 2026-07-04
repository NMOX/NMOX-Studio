package org.nmox.studio.dbstudio.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.engine.EditSession;
import org.nmox.studio.dbstudio.engine.QueryResult;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editable grid's table model: cells editable exactly where the
 * session allows, committed edits land in the session and ping the
 * dirty callback, and revert restores the originals. Pure model,
 * headless.
 */
class EditableResultsModelTest {

    private static final TableInfo USERS = new TableInfo("", "", "users", "TABLE");
    private static final List<ColumnInfo> COLUMNS = List.of(
            new ColumnInfo("id", "INTEGER", 10, false, true),
            new ColumnInfo("name", "TEXT", 0, true, false));

    private static EditSession session() {
        QueryResult result = new QueryResult(List.of("id", "name"),
                List.of(List.of("1", "ada"), List.of("2", "grace")),
                2, -1, false, 1, null, "SELECT * FROM users;");
        return new EditSession(result, USERS, COLUMNS);
    }

    @Test
    @DisplayName("Shape and values mirror the session")
    void shapeAndValues() {
        EditableResultsModel model = new EditableResultsModel(session(), () -> {
        });

        assertThat(model.getRowCount()).isEqualTo(2);
        assertThat(model.getColumnCount()).isEqualTo(2);
        assertThat(model.getColumnName(0)).isEqualTo("id");
        assertThat(model.getColumnName(1)).isEqualTo("name");
        assertThat(model.getColumnClass(1)).isEqualTo(String.class);
        assertThat(model.getValueAt(0, 1)).isEqualTo("ada");
    }

    @Test
    @DisplayName("Primary-key cells are not editable; ordinary columns are")
    void editabilityFollowsSession() {
        EditableResultsModel model = new EditableResultsModel(session(), () -> {
        });

        assertThat(model.isCellEditable(0, 0)).isFalse(); // id is the key
        assertThat(model.isCellEditable(0, 1)).isTrue();
        assertThat(model.isCellEditable(1, 1)).isTrue();
    }

    @Test
    @DisplayName("A committed edit lands in the session, shows in the grid, pings the callback")
    void setValueEditsAndNotifies() {
        AtomicInteger pings = new AtomicInteger();
        EditSession session = session();
        EditableResultsModel model = new EditableResultsModel(session, pings::incrementAndGet);

        model.setValueAt("lovelace", 0, 1);

        assertThat(model.getValueAt(0, 1)).isEqualTo("lovelace");
        assertThat(model.isDirty(0, 1)).isTrue();
        assertThat(session.dirtyCount()).isEqualTo(1);
        assertThat(pings.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Editing a cell back to its original text un-dirties it")
    void editBackCleans() {
        AtomicInteger pings = new AtomicInteger();
        EditableResultsModel model = new EditableResultsModel(session(), pings::incrementAndGet);

        model.setValueAt("lovelace", 0, 1);
        model.setValueAt("ada", 0, 1);

        assertThat(model.isDirty(0, 1)).isFalse();
        assertThat(model.session().dirtyCount()).isZero();
        assertThat(pings.get()).isEqualTo(2); // the chip refreshed both times
    }

    @Test
    @DisplayName("revertAll restores originals and pings the callback")
    void revertAllRestores() {
        AtomicInteger pings = new AtomicInteger();
        EditableResultsModel model = new EditableResultsModel(session(), pings::incrementAndGet);
        model.setValueAt("x", 0, 1);
        model.setValueAt("y", 1, 1);

        model.revertAll();

        assertThat(model.getValueAt(0, 1)).isEqualTo("ada");
        assertThat(model.getValueAt(1, 1)).isEqualTo("grace");
        assertThat(model.session().dirtyCount()).isZero();
        assertThat(pings.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("A null committed value follows the grid's NULL convention")
    void nullValueBecomesNullText() {
        EditableResultsModel model = new EditableResultsModel(session(), () -> {
        });

        model.setValueAt(null, 0, 1);

        assertThat(model.getValueAt(0, 1)).isEqualTo("NULL");
        assertThat(model.isDirty(0, 1)).isTrue();
    }
}
