package org.nmox.studio.apiclient.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Params/Headers grid model: shape, cell reads, and the editing
 * rules that make the trailing blank row materialize a Pair when typed
 * and drop a Pair when emptied - all without touching a live JTable.
 */
class PairTableModelTest {

    /** A model over a fresh list, counting how often onChange fired. */
    private static final class Fixture {
        final List<Pair> pairs = new ArrayList<>();
        int changes = 0;
        final PairTableModel model = new PairTableModel(pairs, () -> changes++);
    }

    // ---- shape ----

    @Test
    @DisplayName("Three columns named On/Name/Value, with a Boolean first column")
    void columns() {
        PairTableModel m = new Fixture().model;
        assertThat(m.getColumnCount()).isEqualTo(3);
        assertThat(m.getColumnName(0)).isEqualTo("On");
        assertThat(m.getColumnName(1)).isEqualTo("Name");
        assertThat(m.getColumnName(2)).isEqualTo("Value");
        assertThat(m.getColumnClass(0)).isEqualTo(Boolean.class);
        assertThat(m.getColumnClass(1)).isEqualTo(String.class);
        assertThat(m.getColumnClass(2)).isEqualTo(String.class);
    }

    @Test
    @DisplayName("Row count is the list size plus one trailing blank row")
    void rowCountHasTrailingBlank() {
        Fixture f = new Fixture();
        assertThat(f.model.getRowCount()).isEqualTo(1);
        f.pairs.add(new Pair("A", "1"));
        f.pairs.add(new Pair("B", "2"));
        assertThat(f.model.getRowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Every cell is editable, including the trailing blank row")
    void allCellsEditable() {
        Fixture f = new Fixture();
        f.pairs.add(new Pair("A", "1"));
        for (int row = 0; row < f.model.getRowCount(); row++) {
            for (int col = 0; col < 3; col++) {
                assertThat(f.model.isCellEditable(row, col))
                        .as("cell %d,%d editable", row, col).isTrue();
            }
        }
    }

    // ---- reads ----

    @Test
    @DisplayName("getValueAt returns enabled flag, name and value for a real row")
    void readsRealRow() {
        Fixture f = new Fixture();
        Pair p = new Pair("Accept", "application/json");
        p.enabled = false;
        f.pairs.add(p);
        assertThat(f.model.getValueAt(0, 0)).isEqualTo(false);
        assertThat(f.model.getValueAt(0, 1)).isEqualTo("Accept");
        assertThat(f.model.getValueAt(0, 2)).isEqualTo("application/json");
    }

    @Test
    @DisplayName("A row with null name and value reads back as empty strings")
    void readsNullsAsEmpty() {
        Fixture f = new Fixture();
        f.pairs.add(new Pair(null, null));
        assertThat(f.model.getValueAt(0, 1)).isEqualTo("");
        assertThat(f.model.getValueAt(0, 2)).isEqualTo("");
    }

    @Test
    @DisplayName("The trailing blank row reads as enabled=true and empty text")
    void readsTrailingBlank() {
        PairTableModel m = new Fixture().model;
        assertThat(m.getValueAt(0, 0)).isEqualTo(Boolean.TRUE);
        assertThat(m.getValueAt(0, 1)).isEqualTo("");
        assertThat(m.getValueAt(0, 2)).isEqualTo("");
    }

    // ---- edits: creating a row from the trailing blank ----

    @Test
    @DisplayName("Typing a name into the blank row materializes a new Pair and fires onChange")
    void typingNameCreatesRow() {
        Fixture f = new Fixture();
        f.model.setValueAt("Authorization", 0, 1);

        assertThat(f.pairs).hasSize(1);
        assertThat(f.pairs.get(0).name).isEqualTo("Authorization");
        assertThat(f.pairs.get(0).value).isEmpty();
        assertThat(f.pairs.get(0).enabled).as("new pair defaults enabled").isTrue();
        assertThat(f.changes).isEqualTo(1);
    }

    @Test
    @DisplayName("Typing only a value into the blank row also materializes a Pair")
    void typingValueCreatesRow() {
        Fixture f = new Fixture();
        f.model.setValueAt("bearer-xyz", 0, 2);

        assertThat(f.pairs).hasSize(1);
        assertThat(f.pairs.get(0).name).isEmpty();
        assertThat(f.pairs.get(0).value).isEqualTo("bearer-xyz");
    }

    @Test
    @DisplayName("Toggling the checkbox on the blank row creates nothing")
    void checkboxOnBlankRowIsNoOp() {
        Fixture f = new Fixture();
        f.model.setValueAt(Boolean.TRUE, 0, 0);
        assertThat(f.pairs).isEmpty();
        assertThat(f.changes).as("no change event for a no-op edit").isZero();
    }

    @Test
    @DisplayName("An empty text edit on the blank row creates nothing")
    void emptyEditOnBlankRowIsNoOp() {
        Fixture f = new Fixture();
        f.model.setValueAt("", 0, 1);
        f.model.setValueAt(null, 0, 2);
        assertThat(f.pairs).isEmpty();
        assertThat(f.changes).isZero();
    }

    // ---- edits: mutating an existing row ----

    @Test
    @DisplayName("Editing the checkbox flips a Pair's enabled flag")
    void checkboxFlipsEnabled() {
        Fixture f = new Fixture();
        f.pairs.add(new Pair("X", "1"));
        f.model.setValueAt(Boolean.FALSE, 0, 0);
        assertThat(f.pairs.get(0).enabled).isFalse();
        f.model.setValueAt(Boolean.TRUE, 0, 0);
        assertThat(f.pairs.get(0).enabled).isTrue();
        assertThat(f.changes).isEqualTo(2);
    }

    @Test
    @DisplayName("Editing name and value writes through to the backing Pair")
    void editNameAndValue() {
        Fixture f = new Fixture();
        f.pairs.add(new Pair("old", "v"));
        f.model.setValueAt("new", 0, 1);
        f.model.setValueAt("changed", 0, 2);
        assertThat(f.pairs.get(0).name).isEqualTo("new");
        assertThat(f.pairs.get(0).value).isEqualTo("changed");
    }

    // ---- edits: emptying a row drops it ----

    @Test
    @DisplayName("Clearing both name and value of a row removes that Pair")
    void clearingBothDropsRow() {
        Fixture f = new Fixture();
        f.pairs.add(new Pair("name", ""));
        f.model.setValueAt("", 0, 1);
        assertThat(f.pairs).as("row with empty name and empty value is dropped").isEmpty();
    }

    @Test
    @DisplayName("Clearing only the name keeps a row that still has a value")
    void clearingNameKeepsRowWithValue() {
        Fixture f = new Fixture();
        f.pairs.add(new Pair("name", "keepme"));
        f.model.setValueAt("", 0, 1);
        assertThat(f.pairs).hasSize(1);
        assertThat(f.pairs.get(0).value).isEqualTo("keepme");
    }
}
