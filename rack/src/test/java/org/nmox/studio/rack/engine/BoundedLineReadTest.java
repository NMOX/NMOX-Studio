package org.nmox.studio.rack.engine;

import java.io.BufferedReader;
import java.io.StringReader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 60 closed: the pump's line reads are bounded. readLineBounded
 * matches readLine's terminator handling exactly on honest input, and a
 * pathological no-newline flood is truncated (marked) with the tail
 * drained — memory stays capped, the stream keeps flowing, and the
 * lines after the flood arrive intact.
 */
class BoundedLineReadTest {

    private static BufferedReader reader(String s) {
        return new BufferedReader(new StringReader(s));
    }

    @Test
    @DisplayName("Honest input: \\n, \\r\\n, and lone \\r all split exactly like readLine")
    void terminatorParity() throws Exception {
        BufferedReader r = reader("alpha\nbravo\r\ncharlie\rdelta");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEqualTo("alpha");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEqualTo("bravo");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEqualTo("charlie");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEqualTo("delta");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isNull();
    }

    @Test
    @DisplayName("Empty lines and EOF behave like readLine")
    void emptyLinesAndEof() throws Exception {
        BufferedReader r = reader("\n\nx\n");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEmpty();
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEmpty();
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEqualTo("x");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isNull();
        assertThat(CommandExecutor.readLineBounded(reader(""), 100)).isNull();
    }

    @Test
    @DisplayName("A no-newline flood is truncated with the honest marker, not grown until OOM")
    void floodIsTruncated() throws Exception {
        String flood = "y".repeat(50_000);
        BufferedReader r = reader(flood);
        String line = CommandExecutor.readLineBounded(r, 1_000);
        assertThat(line).hasSize(1_000 + " …[line truncated]".length())
                .startsWith("yyy").endsWith(" …[line truncated]");
        assertThat(CommandExecutor.readLineBounded(r, 1_000))
                .as("the flood's tail was drained, not re-served").isNull();
    }

    @Test
    @DisplayName("Lines AFTER a flood arrive intact — the stream keeps flowing")
    void streamContinuesAfterFlood() throws Exception {
        BufferedReader r = reader("z".repeat(5_000) + "\nnext line\r\nlast");
        assertThat(CommandExecutor.readLineBounded(r, 100)).endsWith("…[line truncated]");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEqualTo("next line");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEqualTo("last");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isNull();
    }

    @Test
    @DisplayName("A flood ending in \\r\\n does not swallow the following line")
    void floodCrLfBoundary() throws Exception {
        BufferedReader r = reader("q".repeat(500) + "\r\nafter");
        assertThat(CommandExecutor.readLineBounded(r, 100)).endsWith("…[line truncated]");
        assertThat(CommandExecutor.readLineBounded(r, 100)).isEqualTo("after");
    }

    @Test
    @DisplayName("The production ceiling is sane: big enough for honest lines, bounded for floods")
    void productionCeiling() {
        assertThat(CommandExecutor.MAX_LINE_CHARS).isBetween(50_000, 1_000_000);
    }
}
