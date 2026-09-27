package org.nmox.studio.ui.irc;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.KeyboardAccess;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The /list channel browser joins on Enter as it does on a double-click
 * (3.4, question 3) — the table's own Enter would only move down a row.
 */
class ChannelListEnterTest {

    @Test
    @DisplayName("Enter joins the selected channel, through the sort")
    void enterJoins() {
        List<String> joined = new ArrayList<>();
        JTable table = ChannelListDialog.channelTable(List.of(
                new ChannelListCollector.Row("#alpha", 3, "first"),
                new ChannelListCollector.Row("#beta", 40, "second")), joined::add);
        assertThat(KeyboardAccess.perform(table, KeyboardAccess.ENTER)).isTrue();
        assertThat(joined).as("nothing selected, nothing joined").isEmpty();

        table.getRowSorter().toggleSortOrder(1);   // users ascending
        table.getRowSorter().toggleSortOrder(1);   // users descending: #beta first
        table.setRowSelectionInterval(0, 0);
        KeyboardAccess.perform(table, KeyboardAccess.ENTER);
        assertThat(joined).containsExactly("#beta");
    }
}
