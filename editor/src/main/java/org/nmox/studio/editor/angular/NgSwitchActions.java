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

/**
 * The Angular component ↔ template switcher's UI half (v1.219.0):
 * two context-menu actions riding the same focused-editor idiom as
 * Ask ORACLE (a global always-enabled action reading
 * {@link EditorRegistry} — the one popup registration shape proven to
 * render and fire on every editor pane in this product). Resolution
 * lives in the pure {@link NgSwitch}; misses end in an honest status
 * line, never a beep.
 */
public final class NgSwitchActions {

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
            File template = NgSwitch.templateFor(component, readQuietly(component));
            if (template == null) {
                StatusDisplayer.getDefault().setStatusText(
                        "No template file for " + component.getName()
                        + " (inline template, or none on disk)");
                return;
            }
            open(template);
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
            File component = NgSwitch.componentFor(template, NgSwitchActions::readQuietly);
            if (component == null) {
                StatusDisplayer.getDefault().setStatusText(
                        "No component class found beside " + template.getName());
                return;
            }
            open(component);
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
