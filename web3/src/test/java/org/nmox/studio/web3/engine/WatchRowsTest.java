package org.nmox.studio.web3.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Watch table's cell wording — pinned here so the table model in
 * the UI stays a dumb list holder.
 */
class WatchRowsTest {

    @Test
    @DisplayName("the columns are Block / What / Details")
    void columns() {
        assertThat(WatchRows.columns()).containsExactly("Block", "What", "Details");
    }

    @Test
    @DisplayName("a block row reads '#12 · block · 3 txs · gas 42%'")
    void blockRow() {
        WatchRows.Cells cells = WatchRows.cells(
                new WatchFeed.BlockRow(12, 3, 4_200_000, 10_000_000, "0xhash"));

        assertThat(cells.block()).isEqualTo("#12");
        assertThat(cells.what()).isEqualTo("block");
        assertThat(cells.details()).isEqualTo("3 txs · gas 42%");
    }

    @Test
    @DisplayName("one transaction is singular")
    void singularTx() {
        WatchRows.Cells cells = WatchRows.cells(
                new WatchFeed.BlockRow(1, 1, 1, 3, "0xh"));

        assertThat(cells.details()).isEqualTo("1 tx · gas 33%");
    }

    @Test
    @DisplayName("a zero gas limit shows 'gas —' instead of dividing by zero")
    void zeroGasLimit() {
        WatchRows.Cells cells = WatchRows.cells(
                new WatchFeed.BlockRow(2, 0, 0, 0, "0xh"));

        assertThat(cells.details()).isEqualTo("0 txs · gas —");
    }

    @Test
    @DisplayName("an event row carries the contract and the decoded param line")
    void eventRow() {
        Map<String, String> decoded = new LinkedHashMap<>();
        decoded.put("from", "0x709979…79c8");
        decoded.put("value", "1.5 ETH");

        WatchRows.Cells cells = WatchRows.cells(
                new WatchFeed.EventRow(15, "Token", "Transfer", decoded));

        assertThat(cells.block()).isEqualTo("#15");
        assertThat(cells.what()).isEqualTo("Token");
        assertThat(cells.details())
                .isEqualTo("Transfer(from: 0x709979…79c8, value: 1.5 ETH)");
    }

    @Test
    @DisplayName("an event with no params renders empty parens")
    void eventWithoutParams() {
        WatchRows.Cells cells = WatchRows.cells(
                new WatchFeed.EventRow(3, "Counter", "Ping", Map.of()));

        assertThat(cells.details()).isEqualTo("Ping()");
    }
}
