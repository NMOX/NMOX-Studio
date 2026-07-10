package org.nmox.studio.editor.debug;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The browser probe is a fixed candidate list per OS plus a pure
 * first-existing scan — both halves testable from any machine.
 */
class BrowserLocatorTest {

    @Test
    @DisplayName("macOS candidates: Chrome first, then Edge, then Chromium, all under /Applications")
    void shouldListMacCandidates() {
        List<File> candidates = BrowserLocator.candidates(Map.of(), true, false);

        // These are macOS *data* paths; File.getPath() renders them with the
        // HOST separator, so on a Windows runner get(0) comes back as
        // "\Applications\...". Normalize to '/' and assert the INTENT
        // (order + "/Applications" location), never the host's rendering.
        List<String> paths = candidates.stream()
                .map(f -> f.getPath().replace(File.separatorChar, '/'))
                .toList();

        assertThat(paths).isNotEmpty();
        assertThat(paths.get(0))
                .isEqualTo("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
        assertThat(paths).allMatch(p -> p.startsWith("/Applications/"));
        // Chrome first, then Edge before Chromium.
        assertThat(firstIndexContaining(paths, "Microsoft Edge"))
                .isPositive()
                .isLessThan(firstIndexContaining(paths, "Chromium"));
    }

    /** Index of the first path containing {@code needle}, or -1. */
    private static int firstIndexContaining(List<String> paths, String needle) {
        for (int i = 0; i < paths.size(); i++) {
            if (paths.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("Windows candidates come from the env's install roots — Chrome before Edge")
    void shouldListWindowsCandidatesFromEnv() {
        Map<String, String> env = Map.of(
                "PROGRAMFILES", "C:/Program Files",
                "PROGRAMFILES(X86)", "C:/Program Files (x86)",
                "LOCALAPPDATA", "C:/Users/dev/AppData/Local");

        List<File> candidates = BrowserLocator.candidates(env, false, true);

        assertThat(candidates.get(0).getPath())
                .contains("Program Files").contains("chrome.exe");
        assertThat(candidates).extracting(File::getPath)
                .anyMatch(p -> p.contains("AppData") && p.contains("chrome.exe"))
                .anyMatch(p -> p.contains("msedge.exe"));
        // Chrome candidates all precede Edge candidates
        int lastChrome = -1;
        int firstEdge = Integer.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).getPath().contains("chrome.exe")) {
                lastChrome = i;
            } else if (firstEdge == Integer.MAX_VALUE) {
                firstEdge = i;
            }
        }
        assertThat(lastChrome).isLessThan(firstEdge);
    }

    @Test
    @DisplayName("Windows candidates skip install roots the env does not define")
    void shouldSkipMissingWindowsRoots() {
        List<File> candidates = BrowserLocator.candidates(
                Map.of("LOCALAPPDATA", "C:/Users/dev/AppData/Local"), false, true);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getPath()).contains("AppData");
    }

    @Test
    @DisplayName("firstPresent picks the first candidate that exists and can run")
    void shouldPickFirstExecutable(@TempDir Path dir) throws Exception {
        File missing = dir.resolve("missing/chrome").toFile();
        Path present = Files.createFile(dir.resolve("chromium"));
        assertThat(present.toFile().setExecutable(true)).isTrue();
        File alsoPresent = Files.createFile(dir.resolve("edge")).toFile();
        assertThat(alsoPresent.setExecutable(true)).isTrue();

        File found = BrowserLocator.firstPresent(
                List.of(missing, present.toFile(), alsoPresent));

        assertThat(found).isEqualTo(present.toFile());
    }

    @Test
    @DisplayName("a directory named like the browser is not a browser")
    void shouldRejectDirectories(@TempDir Path dir) throws Exception {
        File impostor = Files.createDirectories(dir.resolve("chrome")).toFile();

        assertThat(BrowserLocator.firstPresent(List.of(impostor))).isNull();
    }

    @Test
    @DisplayName("no candidates present: null, so the caller can hint instead of spawning")
    void shouldReturnNullWhenNothingFound(@TempDir Path dir) {
        assertThat(BrowserLocator.firstPresent(
                List.of(dir.resolve("nope").toFile()))).isNull();
    }
}
