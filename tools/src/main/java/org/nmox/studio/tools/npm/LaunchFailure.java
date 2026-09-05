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

    /** The balloon's title and detail (v2.73.0): a wall deserves a door, not only a status line. */
    static String title(String label) {
        return label + " didn't start";
    }

    static String detail(String label) {
        return "The tool it needs is not on your PATH. The reason is in Output ▸ Rack: " + label
                + " — click to open the Environment Doctor.";
    }

    /** The Doctor by its action id — tools carries no dependency on the ui module that owns it. */
    static final String DOCTOR_CATEGORY = "Tools";
    static final String DOCTOR_ID = "org.nmox.studio.ui.actions.EnvironmentDoctorAction";

    static void notify(String label) {
        Balloons.replace("launch:" + label,
                org.openide.awt.NotificationDisplayer.getDefault().notify(title(label),
                javax.swing.UIManager.getIcon("OptionPane.warningIcon"), detail(label), e -> {
                    javax.swing.Action doctor = org.openide.awt.Actions.forID(DOCTOR_CATEGORY, DOCTOR_ID);
                    if (doctor != null) {
                        doctor.actionPerformed(e);
                    }
                }));
    }
}
