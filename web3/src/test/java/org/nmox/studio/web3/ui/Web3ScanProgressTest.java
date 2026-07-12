package org.nmox.studio.web3.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 34, last sliver (v1.48.0): the Contract Studio artifact walk runs
 * under a real, finally-guarded ProgressHandle instead of status text only.
 * Source-gated the way the boot laws are — the walk itself is package-deep
 * inside a pure-Swing window, so the gate pins the load-bearing shape:
 * every ArtifactScanner.scan call in the window routes through
 * {@code scanWithProgress}, the handle starts before the walk, and
 * {@code finish()} sits in a {@code finally} so a walk that throws can
 * never leave a zombie progress bar in the status line.
 */
class Web3ScanProgressTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/web3/ui/Web3StudioTopComponent.java"),
                StandardCharsets.UTF_8);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as(signature + " exists").isGreaterThan(0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("every artifact walk in the window rides scanWithProgress — no bare scan calls")
    void allScansRouteThroughProgress() throws Exception {
        String source = source();
        Matcher bareScan = Pattern.compile("ArtifactScanner\\.scan\\(").matcher(source);
        int calls = 0;
        while (bareScan.find()) {
            calls++;
        }
        assertThat(calls)
                .as("exactly one ArtifactScanner.scan call site — the one inside scanWithProgress")
                .isEqualTo(1);
        assertThat(method(source, "private static List<ContractArtifact> scanWithProgress"))
                .contains("ArtifactScanner.scan(");
        assertThat(method(source, "private void rescan()")).contains("scanWithProgress(");
        assertThat(method(source, "private void autoRescan()")).contains("scanWithProgress(");
    }

    @Test
    @DisplayName("the handle starts before the walk and finishes in a finally block")
    void handleIsFinallyGuarded() throws Exception {
        String helper = method(source(), "private static List<ContractArtifact> scanWithProgress");

        int start = helper.indexOf("progress.start()");
        int walk = helper.indexOf("ArtifactScanner.scan(");
        int fin = helper.indexOf("finally");
        int finish = helper.indexOf("progress.finish()");

        assertThat(start).as("handle starts").isGreaterThan(0);
        assertThat(walk).as("walk runs after start").isGreaterThan(start);
        assertThat(fin).as("finally exists").isGreaterThan(walk);
        assertThat(finish).as("finish() lives in the finally, after the walk").isGreaterThan(fin);
        // no cancel wiring on purpose: Files.walk has no interrupt seam here,
        // and the comment in the helper says so (the v1.44.0 idiom)
        assertThat(helper).doesNotContain("Cancellable");
    }
}
