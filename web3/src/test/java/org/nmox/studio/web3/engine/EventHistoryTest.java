package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The history query's laws (v2.47.0): bounded ranges refused past the
 * cap (never clamped), decoded rows with honest unknowns, and CSV that
 * a spreadsheet can never execute.
 */
class EventHistoryTest {

    @Test
    @DisplayName("ranges: defaults anchor to latest; inversion and over-cap REFUSE, never clamp")
    void ranges() {
        EventHistory.Range r = EventHistory.range("", "", 5000);
        assertThat(r.to()).isEqualTo(5000);
        assertThat(r.from()).isEqualTo(4001); // last 1000 by default
        assertThat(EventHistory.range("10", "20", 5000))
                .isEqualTo(new EventHistory.Range(10, 20));
        assertThat(EventHistory.range("0", "latest", 300).to()).isEqualTo(300);
        assertThatThrownBy(() -> EventHistory.range("30", "20", 5000))
                .hasMessageContaining("inverted");
        assertThatThrownBy(() -> EventHistory.range("0", "5000", 5000))
                .hasMessageContaining("cap");
        assertThatThrownBy(() -> EventHistory.range("abc", "20", 5000))
                .hasMessageContaining("Not a block number");
    }

    @Test
    @DisplayName("rows decode canonical Transfer; unknown topics stay honestly raw")
    void rows() {
        String transferTopic = "0x" + Hex.toHex(Keccak256.hash(
                "Transfer(address,address,uint256)".getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII)));
        JsonRpcClient.LogEntry known = new JsonRpcClient.LogEntry("0xC0",
                List.of(transferTopic,
                        "0x0000000000000000000000000000000000000000000000000000000000000001",
                        "0x0000000000000000000000000000000000000000000000000000000000000002"),
                "0x0000000000000000000000000000000000000000000000000000000000000007",
                42, "0xAA");
        JsonRpcClient.LogEntry unknown = new JsonRpcClient.LogEntry("0xC0",
                List.of("0x" + "ab".repeat(32)), "0x", 43, "0xBB");
        List<EventHistory.Row> rows =
                EventHistory.rows(List.of(known, unknown), List.of());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).event()).isEqualTo("Transfer");
        assertThat(rows.get(0).details()).contains("value=7");
        assertThat(rows.get(1).event()).isEqualTo("(unknown)");
        assertThat(rows.get(1).details()).startsWith("0xabab");
    }

    @Test
    @DisplayName("CSV quotes and neutralizes formula-leading cells")
    void csv() {
        String csv = EventHistory.toCsv(List.of(
                new EventHistory.Row(1, "0xAA", "Transfer", "value=7"),
                new EventHistory.Row(2, "=SUM(A1:A9)", "Evil\"Quote", "@cmd")));
        assertThat(csv).startsWith("block,txHash,event,details\n");
        assertThat(csv).contains("\"'=SUM(A1:A9)\"");
        assertThat(csv).contains("\"Evil\"\"Quote\"");
        assertThat(csv).contains("\"'@cmd\"");
    }
}
