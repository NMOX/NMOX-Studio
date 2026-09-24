package org.nmox.studio.rack.mcp;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.DiagnosticsBus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Agent Port's diagnostics never name, count or quote a secret-bearing
 * file (3.1.0). Language servers now feed the bus, and a JSON server
 * reports on a malformed secrets file like any other.
 */
class McpDiagnosticsSecretsTest {

    private static DiagnosticsBus.Problem p(String name, String message) {
        return new DiagnosticsBus.Problem(new File("/proj/" + name), 3, message, true);
    }

    @Test
    @DisplayName("a server's problems in .env and credentials files are neither counted nor listed")
    void secretFilesAreInvisible() {
        JSONObject out = McpTools.diagnostics(Map.of("lsp:vscode-json",
                List.of(p("credentials.json", "Expected comma near \"token\": \"sk-live-123\""),
                        p(".env.local", "bad line"),
                        p("package.json", "Trailing comma"))), null);
        assertThat(out.getInt("totalFindings")).isEqualTo(1);
        assertThat(out.toString()).doesNotContain("credentials.json").doesNotContain("sk-live")
                .doesNotContain(".env").contains("package.json");
    }
}
