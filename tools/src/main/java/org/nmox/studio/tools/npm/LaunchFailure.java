package org.nmox.studio.tools.npm;

/**
 * What the status line says when an IDE run could not START (v2.73.0):
 * CommandExecutor answers a failed spawn with exit −1 and a friendly reason
 * in the Output tab, but the ▶ said nothing where the eye rests — the
 * progress bar vanished and that was all. Pure, so the sentence is a test.
 */
final class LaunchFailure {

    private LaunchFailure() {
    }

    static String status(String label) {
        return label + " didn't start — see Output ▸ Rack: " + label
                + ", or Tools ▸ Environment Doctor";
    }
}
