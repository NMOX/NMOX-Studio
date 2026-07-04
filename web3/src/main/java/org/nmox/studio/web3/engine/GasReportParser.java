package org.nmox.studio.web3.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code forge test --gas-report} tables — both the box-drawing
 * variant (╭│╰) and the plain-pipe variant newer forge versions print —
 * and {@code .gas-snapshot} files ({@code test_X() (gas: 12345)}).
 *
 * <p>Hand-rolled line scanning, no regexes (the v1.32.0 ReDoS-by-idiom
 * precedent), and a when-unsure-skip-the-line bias: garbage never
 * throws, it just doesn't parse.
 */
public final class GasReportParser {

    /** One function's row of a gas report table. */
    public record FunctionGas(String contract, String function,
            long min, long avg, long median, long max, long calls) {
    }

    /** One {@code .gas-snapshot} line. */
    public record SnapshotEntry(String test, long gas) {
    }

    private GasReportParser() {
    }

    /**
     * Extracts the function rows of every contract table in the output.
     * A row needs a current contract (from a {@code …/X.sol:Name} header
     * line), a non-numeric function name, and five numeric cells —
     * anything else is skipped.
     */
    public static List<FunctionGas> parseGasReport(String output) {
        List<FunctionGas> rows = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return rows;
        }
        String contract = null;
        for (String line : output.split("\n", -1)) {
            List<String> cells = cells(line);
            if (cells.isEmpty()) {
                continue;
            }
            String header = contractName(cells.get(0));
            if (header != null) {
                contract = header;
                continue;
            }
            if (contract == null || cells.size() < 6) {
                continue;
            }
            String function = cells.get(0);
            if (function.isEmpty() || isNumeric(function)) {
                continue; // deployment-cost value rows, separators
            }
            Long min = parseGasNumber(cells.get(1));
            Long avg = parseGasNumber(cells.get(2));
            Long median = parseGasNumber(cells.get(3));
            Long max = parseGasNumber(cells.get(4));
            Long calls = parseGasNumber(cells.get(5));
            if (min == null || avg == null || median == null || max == null || calls == null) {
                continue; // the "Function Name | min | avg …" header row lands here
            }
            rows.add(new FunctionGas(contract, function, min, avg, median, max, calls));
        }
        return rows;
    }

    /**
     * Parses {@code .gas-snapshot} lines like
     * {@code CounterTest:test_Increment() (gas: 31303)}. Lines that
     * don't match are skipped.
     */
    public static List<SnapshotEntry> parseSnapshot(String content) {
        List<SnapshotEntry> entries = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return entries;
        }
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            int marker = trimmed.lastIndexOf("(gas:");
            if (marker <= 0 || !trimmed.endsWith(")")) {
                continue;
            }
            String name = trimmed.substring(0, marker).trim();
            if (name.isEmpty()) {
                continue;
            }
            String number = trimmed.substring(marker + "(gas:".length(),
                    trimmed.length() - 1).trim();
            Long gas = parseGasNumber(number);
            if (gas == null) {
                continue;
            }
            entries.add(new SnapshotEntry(name, gas));
        }
        return entries;
    }

    // ---- internals -------------------------------------------------------

    /** Splits a table line on both pipe glyphs; trims; drops empty cells at the edges only. */
    private static List<String> cells(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean sawPipe = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '|' || c == '│') { // ASCII pipe or box-drawing │
                sawPipe = true;
                out.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString().trim());
        if (!sawPipe) {
            return List.of(); // separator lines (╭──╮, ├──┤) and prose
        }
        // the text between the leading and trailing pipes is what matters
        while (!out.isEmpty() && out.get(0).isEmpty()) {
            out.remove(0);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /**
     * {@code src/Counter.sol:Counter contract} (or {@code … Contract})
     * → {@code Counter}; null when the cell is no contract header.
     */
    private static String contractName(String cell) {
        int solMarker = cell.indexOf(".sol:");
        if (solMarker < 0) {
            return null;
        }
        String after = cell.substring(solMarker + ".sol:".length()).trim();
        if (after.endsWith(" contract") || after.endsWith(" Contract")) {
            after = after.substring(0, after.length() - " contract".length()).trim();
        }
        if (after.isEmpty() || after.contains(" ")) {
            return null;
        }
        return after;
    }

    /** Digits with optional {@code ,}/{@code _} grouping → long; anything else → null. */
    private static Long parseGasNumber(String cell) {
        if (cell == null || cell.isEmpty()) {
            return null;
        }
        long value = 0;
        boolean sawDigit = false;
        for (int i = 0; i < cell.length(); i++) {
            char c = cell.charAt(i);
            if (c >= '0' && c <= '9') {
                if (value > (Long.MAX_VALUE - (c - '0')) / 10) {
                    return null; // would overflow: not a gas number
                }
                value = value * 10 + (c - '0');
                sawDigit = true;
            } else if (c == ',' || c == '_') {
                continue; // grouping
            } else {
                return null;
            }
        }
        return sawDigit ? value : null;
    }

    private static boolean isNumeric(String cell) {
        return parseGasNumber(cell) != null;
    }
}
