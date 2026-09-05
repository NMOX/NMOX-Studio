package org.nmox.studio.rack.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arc's load-bearing law, structural: the Agent Port is READ-ONLY
 * BY CONSTRUCTION. No class in org.nmox.studio.rack.mcp may name a
 * spawn or write primitive — CommandExecutor, ProcessSupport,
 * ProcessBuilder, Runtime.exec, Files.write/newOutputStream, or the
 * trust gate (a caller that is not the user cannot be asked to grant
 * trust). A v2 execution surface will introduce these DELIBERATELY
 * with its own consent design; until then this gate fails the build
 * on the first one, so the inbound port can never quietly grow teeth.
 */
class McpReadOnlyLedgerTest {

    private static final List<String> FORBIDDEN = List.of(
            "CommandExecutor", "ProcessSupport", "ProcessBuilder",
            "Runtime.getRuntime", ".exec(", "Files.write", "Files.newOutputStream",
            "Files.newBufferedWriter", "WorkspaceTrust", "requestTrust",
            // the run registry's write half (v2.77.0): live_runs LISTS, the
            // \u25a0 stops — an agent that may stop the user's server is a v2
            // execution surface with its own consent design
            "LiveRuns.stop", "LiveRuns.remove(",
            // every other way to change a file (v2.80.0): the outline and
            // search tools READ the project; the read-only law must ban the
            // mutators by name, not trust that nobody reaches for them
            "Files.delete", "Files.move", "Files.copy", "Files.createFile",
            "Files.createDirector", "Files.setLastModifiedTime", "Files.setPosixFilePermissions",
            "FileOutputStream", "FileWriter", "RandomAccessFile", "AtomicFiles");

    private static Path mcpDir() {
        return Path.of("src/main/java/org/nmox/studio/rack/mcp");
    }

    @Test
    @DisplayName("No MCP class names any spawn or write primitive")
    void readOnlyByConstruction() throws IOException {
        assertThat(Files.isDirectory(mcpDir()))
                .as("the mcp package exists").isTrue();
        try (Stream<Path> files = Files.walk(mcpDir())) {
            List<String> offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(McpReadOnlyLedgerTest::offendingLines)
                    .toList();
            assertThat(offenders)
                    .as("read-only ledger — a v2 execution surface adds these "
                            + "deliberately with its own consent design")
                    .isEmpty();
        }
    }

    private static Stream<String> offendingLines(Path file) {
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Stream.of(file + ": unreadable");
        }
        String name = file.getFileName().toString();
        // the AgentPort DOES bind an httpserver (that is the transport,
        // not a spawn); the forbidden list is spawn/write/trust only, so
        // no exemption is needed — but the OutputStream it writes the
        // HTTP RESPONSE to is exchange.getResponseBody(), never a file:
        // Files.newOutputStream is the banned token, not OutputStream.
        return source.lines()
                .filter(line -> {
                    String code = line.strip();
                    if (code.startsWith("//") || code.startsWith("*")) {
                        return false; // a comment naming the ban is not the ban
                    }
                    return FORBIDDEN.stream().anyMatch(code::contains);
                })
                .map(line -> name + ": " + line.strip());
    }
}
