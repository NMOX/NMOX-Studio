package org.nmox.studio.ui.options;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.netbeans.spi.options.OptionsPanelController;
import org.nmox.studio.core.util.AtomicFiles;
import org.nmox.studio.core.util.UiLocale;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;

/**
 * Options ▸ NMOX Studio ▸ General: the daily update-check toggle (the
 * {@code nmox/ui} preference node the {@link org.nmox.studio.ui.UpdateCheck}
 * startup task gates on) and, since v2.97.0, the <b>Language</b> the IDE's
 * own chrome speaks. The language is not a preference: it is a launcher
 * argument, so Apply writes {@link UiLocale}'s shell-safe block into the
 * per-user {@code etc/nmoxstudio.conf} (off the EDT, atomically) and says
 * plainly that the switch lands on the next start.
 */
@OptionsPanelController.SubRegistration(
        location = "NmoxStudio",
        displayName = "#GeneralOptions_DisplayName",
        keywords = "#GeneralOptions_Keywords",
        keywordsCategory = "NmoxStudio/General",
        position = 10
)
@org.openide.util.NbBundle.Messages({
    "GeneralOptions_DisplayName=General",
    "GeneralOptions_Keywords=update check startup language locale translation",
    "GeneralOptions_UpdateCheck=Check for updates on startup (once daily)",
    "GeneralOptions_Language=Language:",
    "GeneralOptions_LanguageNote=<html><i>Menus, dialogs and messages of NMOX Studio itself. "
        + "Takes effect after a restart. Rack faceplates keep their panel vocabulary.</i></html>",
    "GeneralOptions_RestartTitle=Language changes on the next start",
    "GeneralOptions_RestartBody=Quit and start NMOX Studio again to switch the language.",
    "GeneralOptions_WriteFailed=The language could not be saved: the launcher settings file is not writable."
})
public class GeneralOptionsPanelController extends OptionsPanelController {

    private static final Logger LOG = Logger.getLogger(GeneralOptionsPanelController.class.getName());
    /** Off-EDT lane for the conf write (a disk write on the paint thread is the v1.108.0 class). */
    private static final RequestProcessor CONF_RP = new RequestProcessor("nmox-locale-conf", 1);

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private JPanel panel;
    private JCheckBox updateCheck;
    private JComboBox<String> language;
    /** The code the conf held when the panel was last updated ("" = system). */
    private String languageOnDisk = "";

    /** The shared node the update-check startup task gates on. */
    private static java.util.prefs.Preferences prefs() {
        return NbPreferences.root().node("nmox/ui");
    }

    /** The per-user launcher conf, or null when there is no userdir (tests, odd boots). */
    static Path userConf() {
        String userdir = System.getProperty("netbeans.user");
        return userdir == null || userdir.isBlank() ? null : UiLocale.userConf(Path.of(userdir));
    }

    /** The code the conf pins now, read off the EDT by the caller. */
    static String languageOnDisk(Path conf) {
        if (conf == null || !Files.isRegularFile(conf)) {
            return "";
        }
        try {
            return UiLocale.current(Files.readString(conf, StandardCharsets.UTF_8)).orElse("");
        } catch (IOException e) {
            LOG.log(Level.FINE, "launcher conf unreadable", e);
            return "";
        }
    }

    /** Writes the code into the conf, preserving every other line; the pure edit is UiLocale.apply. */
    static void writeLanguage(Path conf, String code) throws IOException {
        String existing = Files.isRegularFile(conf) ? Files.readString(conf, StandardCharsets.UTF_8) : "";
        Files.createDirectories(conf.getParent());
        AtomicFiles.writeString(conf, UiLocale.apply(existing, code));
    }

    @Override
    public void update() {
        getPanel();
        updateCheck.setSelected(prefs().getBoolean("updateCheck", true));
        Path conf = userConf();
        // the read is a small file, but it is disk: hop off the EDT and back
        CONF_RP.post(() -> {
            String code = languageOnDisk(conf);
            javax.swing.SwingUtilities.invokeLater(() -> {
                languageOnDisk = code;
                language.setSelectedIndex(UiLocale.SUPPORTED.indexOf(UiLocale.choiceFor(code)));
            });
        });
    }

    @Override
    public void applyChanges() {
        prefs().putBoolean("updateCheck", updateCheck.isSelected());
        String chosen = UiLocale.SUPPORTED.get(Math.max(0, language.getSelectedIndex())).code();
        if (chosen.equals(languageOnDisk)) {
            return;
        }
        Path conf = userConf();
        if (conf == null) {
            return;
        }
        CONF_RP.post(() -> {
            try {
                writeLanguage(conf, chosen);
                languageOnDisk = chosen;
                notifyRestart();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "language not saved", e);
                javax.swing.SwingUtilities.invokeLater(() -> org.openide.DialogDisplayer.getDefault().notify(
                        new org.openide.NotifyDescriptor.Message(Bundle.GeneralOptions_WriteFailed(),
                                org.openide.NotifyDescriptor.WARNING_MESSAGE)));
            }
        });
    }

    /** One balloon: the choice is saved, the language changes when the app next starts. */
    private static void notifyRestart() {
        try {
            org.openide.awt.NotificationDisplayer.getDefault().notify(
                    Bundle.GeneralOptions_RestartTitle(),
                    javax.swing.UIManager.getIcon("OptionPane.informationIcon"),
                    Bundle.GeneralOptions_RestartBody(), null);
        } catch (RuntimeException | LinkageError ignored) {
            // notifications unavailable (tests, stripped platform)
        }
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isChanged() {
        boolean updateChanged = updateCheck.isSelected() != prefs().getBoolean("updateCheck", true);
        String chosen = UiLocale.SUPPORTED.get(Math.max(0, language.getSelectedIndex())).code();
        return updateChanged || !chosen.equals(languageOnDisk);
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return getPanel();
    }

    private JPanel getPanel() {
        if (panel != null) {
            return panel;
        }
        panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 8, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;

        updateCheck = new JCheckBox(Bundle.GeneralOptions_UpdateCheck());
        c.gridwidth = 2;
        panel.add(updateCheck, c);
        c.gridwidth = 1;

        c.gridy++;
        JLabel languageLabel = new JLabel(Bundle.GeneralOptions_Language());
        panel.add(languageLabel, c);
        String[] names = UiLocale.SUPPORTED.stream().map(UiLocale.Choice::nativeName).toArray(String[]::new);
        language = new JComboBox<>(names);
        language.getAccessibleContext().setAccessibleName(Bundle.GeneralOptions_Language());
        languageLabel.setLabelFor(language);
        c.gridx = 1;
        panel.add(language, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        // authored markup with no spliced text: the note is the product's own literal
        panel.add(new JLabel(Bundle.GeneralOptions_LanguageNote()), c);
        return panel;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }
}
