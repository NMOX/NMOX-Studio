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
import org.openide.awt.ActionReferences;
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

    /** In a component class: open its stylesheet (v1.313.0). */
    @ActionID(category = "Edit", id = "org.nmox.studio.editor.angular.OpenStyles")
    @ActionRegistration(displayName = "#CTL_OpenNgStyles", lazy = true)
    @ActionReference(path = "Editors/text/typescript/Popup", position = 96)
    @Messages("CTL_OpenNgStyles=Open Angular Styles")
    public static final class OpenStyles implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            File component = focusedFile();
            if (component == null || !component.getName().endsWith(".ts")) {
                return;
            }
            RESOLVE_RP.post(() -> {
                File styles = NgSwitch.stylesFor(component, readQuietly(component));
                if (styles == null) {
                    StatusDisplayer.getDefault().setStatusText(
                            "No stylesheet for " + component.getName()
                            + " (inline styles, or none on disk)");
                    return;
                }
                java.awt.EventQueue.invokeLater(() -> open(styles));
            });
        }
    }

    /** In a component class: open its spec (v1.313.0). */
    @ActionID(category = "Edit", id = "org.nmox.studio.editor.angular.OpenSpec")
    @ActionRegistration(displayName = "#CTL_OpenNgSpec", lazy = true)
    @ActionReference(path = "Editors/text/typescript/Popup", position = 97)
    @Messages("CTL_OpenNgSpec=Open Angular Spec")
    public static final class OpenSpec implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            File component = focusedFile();
            if (component == null || !component.getName().endsWith(".ts")) {
                return;
            }
            RESOLVE_RP.post(() -> {
                File spec = NgSwitch.specFor(component);
                if (spec == null) {
                    StatusDisplayer.getDefault().setStatusText(
                            "No spec beside " + component.getName()
                            + " (generated with --skip-tests, or this IS the spec)");
                    return;
                }
                java.awt.EventQueue.invokeLater(() -> open(spec));
            });
        }
    }

    /**
     * In a stylesheet: open the component class it belongs to
     * (v1.313.0). A stylesheet names its component only by the basename
     * convention, so this rides {@link NgSwitch#componentForSibling}
     * rather than the templateUrl scan the template direction uses.
     */
    @ActionID(category = "Edit", id = "org.nmox.studio.editor.angular.OpenComponentFromStyles")
    @ActionRegistration(displayName = "#CTL_OpenNgComponentFromStyles", lazy = true)
    @ActionReferences({
        // text/css alone is NOT enough: the platform's css-prep module
        // resolves .scss/.less to their OWN mimes before our resolvers see
        // them, so a css-only registration never reaches the stylesheets
        // Angular projects actually use (`ng new --style=scss` is the common
        // choice). This is the v1.230.0 finding — where the same mime gap
        // had silently kept the swatches, the colour picker and the Prettier
        // menu off real SCSS files — and the v1.313.0 live walk caught the
        // menu item missing on a .scss editor before ship.
        @ActionReference(path = "Editors/text/css/Popup", position = 95),
        @ActionReference(path = "Editors/text/scss/Popup", position = 95),
        @ActionReference(path = "Editors/text/less/Popup", position = 95),
        // v2.20.x review: ng new --style=sass exists, NgSwitch's
        // extension list always knew .sass, and the indented dialect
        // has its own mime since v2.20.0 — the gesture just needed
        // the registration
        @ActionReference(path = "Editors/text/x-sass/Popup", position = 95)
    })
    @Messages("CTL_OpenNgComponentFromStyles=Open Component Class")
    public static final class OpenComponentFromStyles implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            File styles = focusedFile();
            if (styles == null) {
                return;
            }
            RESOLVE_RP.post(() -> {
                File component = NgSwitch.componentForSibling(styles);
                if (component == null) {
                    StatusDisplayer.getDefault().setStatusText(
                            "No component class beside " + styles.getName());
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
