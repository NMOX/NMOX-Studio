package org.nmox.studio.apiclient.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Assertion;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Tests grid model: two columns of assertion kind and target, with
 * the trailing blank row that adds an Assertion when filled - exercised
 * directly, no JTable required.
 */
class TestsTableModelTest {

    /** A model over a fresh list, counting how often onChange fired. */
    private static final class Fixture {
        final List<Assertion> tests = new ArrayList<>();
        int changes = 0;
        final TestsTableModel model = new TestsTableModel(tests, () -> changes++);
    }

    // ---- shape ----

    @Test
    @DisplayName("Two columns named Assertion/Target")
    void columns() {
        TestsTableModel m = new Fixture().model;
        assertThat(m.getColumnCount()).isEqualTo(2);
        assertThat(m.getColumnName(0)).isEqualTo("Assertion");
        assertThat(m.getColumnName(1)).isEqualTo("Target");
    }

    @Test
    @DisplayName("Row count is the list size plus one trailing blank row")
    void rowCountHasTrailingBlank() {
        Fixture f = new Fixture();
        assertThat(f.model.getRowCount()).isEqualTo(1);
        f.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "200"));
        assertThat(f.model.getRowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Every cell is editable")
    void allCellsEditable() {
        Fixture f = new Fixture();
        f.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "200"));
        for (int row = 0; row < f.model.getRowCount(); row++) {
            assertThat(f.model.isCellEditable(row, 0)).isTrue();
            assertThat(f.model.isCellEditable(row, 1)).isTrue();
        }
    }

    // ---- reads ----

    @Test
    @DisplayName("getValueAt returns the assertion kind and target for a real row")
    void readsRealRow() {
        Fixture f = new Fixture();
        f.tests.add(new Assertion(Assertion.Kind.BODY_CONTAINS, "hello"));
        assertThat(f.model.getValueAt(0, 0)).isEqualTo(Assertion.Kind.BODY_CONTAINS);
        assertThat(f.model.getValueAt(0, 1)).isEqualTo("hello");
    }

    @Test
    @DisplayName("The trailing blank row reads as STATUS_IS with an empty target")
    void readsTrailingBlank() {
        TestsTableModel m = new Fixture().model;
        assertThat(m.getValueAt(0, 0)).isEqualTo(Assertion.Kind.STATUS_IS);
        assertThat(m.getValueAt(0, 1)).isEqualTo("");
    }

    // ---- edits: creating a row from the trailing blank ----

    @Test
    @DisplayName("Setting a target on the blank row adds a new Assertion and fires onChange")
    void settingTargetCreatesRow() {
        Fixture f = new Fixture();
        f.model.setValueAt("201", 0, 1);

        assertThat(f.tests).hasSize(1);
        assertThat(f.tests.get(0).kind).as("new assertion defaults STATUS_IS")
                .isEqualTo(Assertion.Kind.STATUS_IS);
        assertThat(f.tests.get(0).target).isEqualTo("201");
        assertThat(f.changes).isEqualTo(1);
    }

    @Test
    @DisplayName("Choosing a kind on the blank row adds a new Assertion")
    void settingKindCreatesRow() {
        Fixture f = new Fixture();
        f.model.setValueAt(Assertion.Kind.HEADER_PRESENT, 0, 0);

        assertThat(f.tests).hasSize(1);
        assertThat(f.tests.get(0).kind).isEqualTo(Assertion.Kind.HEADER_PRESENT);
    }

    @Test
    @DisplayName("An empty or null edit on the blank row creates nothing")
    void emptyEditOnBlankRowIsNoOp() {
        Fixture f = new Fixture();
        f.model.setValueAt("", 0, 1);
        f.model.setValueAt(null, 0, 1);
        assertThat(f.tests).isEmpty();
        assertThat(f.changes).isZero();
    }

    // ---- edits: mutating an existing row ----

    @Test
    @DisplayName("The kind column accepts a Kind enum directly")
    void kindColumnAcceptsEnum() {
        Fixture f = new Fixture();
        f.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "200"));
        f.model.setValueAt(Assertion.Kind.TIME_UNDER_MS, 0, 0);
        assertThat(f.tests.get(0).kind).isEqualTo(Assertion.Kind.TIME_UNDER_MS);
    }

    @Test
    @DisplayName("The kind column parses a String name into a Kind")
    void kindColumnParsesString() {
        Fixture f = new Fixture();
        f.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "200"));
        f.model.setValueAt("JSON_HAS_PATH", 0, 0);
        assertThat(f.tests.get(0).kind).isEqualTo(Assertion.Kind.JSON_HAS_PATH);
    }

    @Test
    @DisplayName("Editing the target writes through to the backing Assertion")
    void editTarget() {
        Fixture f = new Fixture();
        f.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "200"));
        f.model.setValueAt("404", 0, 1);
        assertThat(f.tests.get(0).target).isEqualTo("404");
        assertThat(f.changes).isEqualTo(1);
    }
}
