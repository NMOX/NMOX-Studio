package org.nmox.studio.editor.debug;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.nmox.studio.core.process.ToolLocator;
import org.openide.util.BaseUtilities;

/**
 * Finds a debuggable browser for "Debug in Chrome (breakpoints)".
 *
 * js-debug drives the browser over the Chrome DevTools Protocol, so only
 * Chromium-family browsers qualify — Chrome, Microsoft Edge, Chromium —
 * never Safari or Firefox. The probe is the ToolLocator idiom: a fixed
 * list of the standard install locations per OS, first hit wins, and a
 * miss returns null so the caller can name what to install instead of
 * spawning something that cannot exist.
 */
public final class BrowserLocator {

    private BrowserLocator() {
    }

    /**
     * The first installed Chromium-family browser, or null when none is
     * found (callers show an install hint and never spawn).
     */
    public static File find() {
        return firstPresent(candidates(System.getenv(),
                BaseUtilities.isMac(), BaseUtilities.isWindows()));
    }

    /**
     * Candidate launcher paths for the given OS, most common first: Chrome
     * before Edge before Chromium, because Chrome is what the action's
     * label promises. Parameters instead of direct env/OS reads so tests
     * can probe every OS's list from any machine.
     */
    static List<File> candidates(Map<String, String> env, boolean mac, boolean windows) {
        List<File> found = new ArrayList<>();
        if (mac) {
            // a data list of probe paths, the ToolLocator shape — these ARE
            // the fixed install locations, there is nothing to derive them from
            for (String path : List.of(
                    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                    "/Applications/Chromium.app/Contents/MacOS/Chromium")) {
                found.add(new File(path));
            }
        } else if (windows) {
            // Chrome installs per-machine under either Program Files, or
            // per-user under LOCALAPPDATA; Edge ships with Windows itself
            for (String base : new String[] {env.get("PROGRAMFILES"),
                    env.get("PROGRAMFILES(X86)"), env.get("LOCALAPPDATA")}) {
                if (base != null) {
                    found.add(new File(base, "Google/Chrome/Application/chrome.exe"));
                }
            }
            for (String base : new String[] {env.get("PROGRAMFILES"),
                    env.get("PROGRAMFILES(X86)")}) {
                if (base != null) {
                    found.add(new File(base, "Microsoft/Edge/Application/msedge.exe"));
                }
            }
        } else {
            // Linux: distro packaging scatters the binary name, not the
            // location — ride ToolLocator's augmented PATH scan, which
            // returns the name unchanged when nothing was found
            for (String name : List.of("google-chrome", "google-chrome-stable",
                    "chromium", "chromium-browser", "microsoft-edge")) {
                String resolved = ToolLocator.resolve(name);
                if (!resolved.equals(name)) {
                    found.add(new File(resolved));
                }
            }
        }
        return found;
    }

    /** The pure half: first candidate that exists and can run, else null. */
    static File firstPresent(List<File> candidates) {
        for (File candidate : candidates) {
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
        }
        return null;
    }
}
