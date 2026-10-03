package org.nmox.studio.ui.keymap;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.ui.keymap.KeymapProfiles.Outcome;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 * <i>Preferences: Use the VS Code Keymap</i> — one step from Quick Search to
 * the sixth keymap profile, for hands that know VS Code's chords.
 *
 * <p>The profile itself is in the Options dialog like the other five
 * (Keymap ▸ Profile ▸ VS Code); this action is the same switch without the
 * dialog ({@link KeymapProfiles}). It is never run for the user: no first-run
 * prompt offers it and nothing calls it but the person who asks for it, because a
 * keymap is theirs. Every outcome is said on the status line, and the one
 * that changed something names the way back.
 *
 * <p>The switch sets an attribute of the configuration filesystem, which the
 * platform writes to the user directory, so it runs off the event thread.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.ui.keymap.UseVsCodeKeymapAction")
@ActionRegistration(displayName = "#CTL_UseVsCodeKeymapAction", lazy = true)
@Messages({
    "CTL_UseVsCodeKeymapAction=Use the VS Code Keymap",
    "UseVsCodeKeymap_switched=Keymap profile: VS Code. To go back, choose another under Tools ▸ Options ▸ Keymap ▸ Profile.",
    "UseVsCodeKeymap_switchedMac=Keymap profile: VS Code. To go back, choose another under NMOX Studio ▸ Settings… ▸ Keymap ▸ Profile.",
    "UseVsCodeKeymap_already=The keymap profile is already VS Code.",
    "UseVsCodeKeymap_noProfile=The VS Code keymap profile is not installed, so the keymap was not changed.",
    "UseVsCodeKeymap_failed=The keymap was not changed: the keymap settings could not be reached. Choose VS Code under Tools ▸ Options ▸ Keymap ▸ Profile.",
    "UseVsCodeKeymap_failedMac=The keymap was not changed: the keymap settings could not be reached. Choose VS Code under NMOX Studio ▸ Settings… ▸ Keymap ▸ Profile."
})
public final class UseVsCodeKeymapAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("nmox-keymap-profile", 1);

    @Override
    public void actionPerformed(ActionEvent e) {
        RP.post(() -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(
                message(KeymapProfiles.use(KeymapProfiles.VSCODE, KeymapProfiles.PLATFORM), Utilities.isMac()))));
    }

    /** What the status line says for an outcome; macOS keeps its settings under the application menu. */
    static String message(Outcome outcome, boolean mac) {
        return switch (outcome) {
            case SWITCHED -> mac ? Bundle.UseVsCodeKeymap_switchedMac() : Bundle.UseVsCodeKeymap_switched();
            case ALREADY -> Bundle.UseVsCodeKeymap_already();
            case NO_PROFILE -> Bundle.UseVsCodeKeymap_noProfile();
            case FAILED -> mac ? Bundle.UseVsCodeKeymap_failedMac() : Bundle.UseVsCodeKeymap_failed();
        };
    }
}
