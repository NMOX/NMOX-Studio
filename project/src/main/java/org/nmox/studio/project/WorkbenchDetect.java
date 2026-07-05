package org.nmox.studio.project;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.openide.util.RequestProcessor;

/**
 * The Workbench's off-EDT toolchain detection, extracted from the pure-Swing
 * {@link ProjectExplorerTopComponent} so the load-bearing behavior — that the
 * directory-walking detection never runs on the caller's (EDT) thread — is
 * unit-testable.
 *
 * <p>Detecting a project's toolchains means {@code File.list} on every manifest
 * lane; on a fresh $HOME aim that would touch the macOS TCC-protected folders
 * on the EDT and stack permission prompts during window restore. Running the
 * walk on a background {@link RequestProcessor} and applying the result on the
 * EDT is the fix.
 */
final class WorkbenchDetect {

    private WorkbenchDetect() {
    }

    /**
     * Posts {@code detect.apply(dir)} to {@code rp} (off the caller's thread)
     * and, when it completes, hands the result to {@code apply} on the EDT.
     *
     * @param rp     the background executor the walk runs on
     * @param dir    the project directory to detect
     * @param detect the (directory-walking) detection, run on {@code rp}
     * @param apply  receives the detected names; invoked on the EDT
     */
    static void detectAsync(RequestProcessor rp, File dir,
            Function<File, List<String>> detect, Consumer<List<String>> apply) {
        rp.post(() -> {
            List<String> names = detect.apply(dir);
            javax.swing.SwingUtilities.invokeLater(() -> apply.accept(names));
        });
    }
}
