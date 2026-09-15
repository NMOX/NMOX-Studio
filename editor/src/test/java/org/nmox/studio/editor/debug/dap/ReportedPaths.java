package org.nmox.studio.editor.debug.dap;

import java.io.IOException;
import java.nio.file.Path;

import org.json.JSONObject;

/**
 * A stack frame's source path as the filesystem spells it. js-debug reports
 * a Windows path for a {@code pwa-node} target with a lowercase drive letter
 * ({@code c:\…}) where {@code Path.toString()} says {@code C:\…} — the
 * windows lane caught it on the v2.156.0 Node E2E's first run — so the two
 * integration tests compare canonical forms. A path that cannot be resolved
 * is returned as reported, so a wrong path still fails by its own spelling.
 */
final class ReportedPaths {

    private ReportedPaths() {
    }

    static String of(JSONObject frame) {
        String path = frame.getJSONObject("source").getString("path");
        try {
            return Path.of(path).toRealPath().toString();
        } catch (IOException | RuntimeException notResolvable) {
            return path;
        }
    }
}
