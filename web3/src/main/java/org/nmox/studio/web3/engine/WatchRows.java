package org.nmox.studio.web3.engine;

import java.util.List;
import java.util.StringJoiner;

/**
 * Turns {@link WatchFeed} rows into the three display cells of the
 * Watch table — pure, so the wording ("3 txs · gas 42%") is pinned by
 * tests and the table model in the UI stays a dumb list holder.
 */
@org.openide.util.NbBundle.Messages({
    "WatchRows_columnBlock=Block",
    "WatchRows_columnWhat=What",
    "WatchRows_columnDetails=Details",
    "WatchRows_block=block",
    "WatchRows_txCountOne={0} tx",
    "WatchRows_txCountMany={0} txs",
    "WatchRows_gasUnknown=gas \u2014",
    "WatchRows_gasPercent=gas {0}%"
})
public final class WatchRows {

    /** The Watch table's three cells for one feed row. */
    public record Cells(String block, String what, String details) {
    }

    private WatchRows() {
    }

    /** The Watch table's column headers, in order. */
    public static List<String> columns() {
        return List.of(Bundle.WatchRows_columnBlock(),
                Bundle.WatchRows_columnWhat(),
                Bundle.WatchRows_columnDetails());
    }

    /** The cells for one feed row — blocks and decoded events alike. */
    public static Cells cells(WatchFeed.Row row) {
        if (row instanceof WatchFeed.BlockRow block) {
            return new Cells("#" + block.number(), Bundle.WatchRows_block(),
                    txPart(block.txCount()) + " · " + gasPart(block));
        }
        WatchFeed.EventRow event = (WatchFeed.EventRow) row;
        StringJoiner joiner = new StringJoiner(", ", event.eventName() + "(", ")");
        event.decoded().forEach((name, value) -> joiner.add(name + ": " + value));
        return new Cells("#" + event.blockNumber(), event.contractName(),
                joiner.toString());
    }

    private static String txPart(int txCount) {
        return txCount == 1
                ? Bundle.WatchRows_txCountOne(String.valueOf(txCount))
                : Bundle.WatchRows_txCountMany(String.valueOf(txCount));
    }

    /** Gas fill as a whole percent; a zero gas limit (odd node) shows as "gas —". */
    private static String gasPart(WatchFeed.BlockRow block) {
        if (block.gasLimit() <= 0) {
            return Bundle.WatchRows_gasUnknown();
        }
        long pct = Math.round(block.gasUsed() * 100.0 / block.gasLimit());
        return Bundle.WatchRows_gasPercent(String.valueOf(pct));
    }
}
