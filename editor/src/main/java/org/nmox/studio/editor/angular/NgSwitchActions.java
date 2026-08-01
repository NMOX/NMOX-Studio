package org.nmox.studio.editor.angular;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * The Angular component ↔ template switcher's UI half (v1.219.0):
 * two context-menu actions riding the same focused-editor idiom as
 * Ask ORACLE (a global always-enabled action reading
 * {@link EditorRegistry} — the one popup registration shape proven to
 * render and fire on every editor pane in this product). Resolution
 * lives in the pure {@link NgSwitch}; misses end in an honest status
 * line, never a beep.
 *
 * <p>Resolution reads files (the component source for its templateUrl,
 * and in the template→owner direction potentially every sibling .ts),
 * so it rides {@link #RESOLVE_RP}, never the EDT — a wedged mount must
 * not freeze the UI on a context-menu click (the v1.108.0 law; found
 * by the v1.220.0 arc review in day-old code).
 */
public final class NgSwitchActions {

    private static final RequestProcessor RESOLVE_RP =
            new RequestProcessor("nmox-ng-switch", 1);

    private NgSwitchActions() {
    }

    /** In a component class: open the template its decorator points at. */
    @ActionID(category = "Edit", id = "org.nmox.studio.editor.angular.OpenTemplate")
    @ActionRegistration(displayName = "#CTL_OpenNgTemplate", lazy = true)
    @ActionReference(path = "Editors/text/typescript/Popup", position = 95)
    @Messages("CTL_OpenNgTemplate=Open Angular Template")
    public static final class OpenTemplate implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            File component = focusedFile();
            if (component == null || !component.getName().endsWith(".ts")) {
                return;
            }
            RESOLVE_RP.post(() -> {
                File template = NgSwitch.templateFor(component, readQuietly(component));
                if (template == null) {
                    StatusDisplayer.getDefault().setStatusText(
                            "No template file for " + component.getName()
                            + " (inline template, or none on disk)");
                    return;
                }
                java.awt.EventQueue.invokeLater(() -> open(template));
            });
        }
    }

    /** In a template: open the component class that owns it. */
    @ActionID(category = "Edit", id = "org.nmox.studio.editor.angular.OpenComponent")
    @ActionRegistration(displayName = "#CTL_OpenNgComponent", lazy = true)
    @ActionReference(path = "Editors/text/x-ng-template/Popup", position = 95)
    @Messages("CTL_OpenNgComponent=Open Component Class")
    public static final class OpenComponent implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            File template = focusedFile();
            if (template == null) {
                return;
            }
            RESOLVE_RP.post(() -> {
                File component = NgSwitch.componentFor(template, NgSwitchActions::readQuietly);
                if (component == null) {
                    StatusDisplayer.getDefault().setStatusText(
                            "No component class found beside " + template.getName());
                    return;
                }
                java.awt.EventQueue.invokeLater(() -> open(component));
            });
        }
    }

    static File focusedFile() {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) {
            return null;
        }
        Object sd = editor.getDocument().getProperty(
                javax.swing.text.Document.StreamDescriptionProperty);
        if (sd instanceof DataObject dob) {
            FileObject fo = dob.getPrimaryFile();
            return fo == null ? null : FileUtil.toFile(fo);
        }
        return null;
    }

    static String readQuietly(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException ex) {
            return null; // unreadable or non-UTF-8: treat as no source
        }
    }

    static void open(File file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        if (fo == null) {
            return;
        }
        try {
            OpenCookie cookie = DataObject.find(fo).getLookup().lookup(OpenCookie.class);
            if (cookie != null) {
                cookie.open();
            }
        } catch (IOException ex) {
            StatusDisplayer.getDefault().setStatusText(
                    "Could not open " + file.getName() + ": " + ex.getMessage());
        }
    }
}
