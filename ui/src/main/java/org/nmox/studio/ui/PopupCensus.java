package org.nmox.studio.ui;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.text.EditorKit;

import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.windows.WindowManager;

import org.nmox.studio.core.util.Threads;

/**
 * Reads what the editor's RIGHT-CLICK menu would paint, without a right-click.
 *
 * <p>The menu bar can be walked: the accessibility API hands back the live
 * rows by process id. A context menu cannot — a right-click on a Swing-painted
 * editor pane is the one gesture this automation has never been able to
 * deliver (recorded since v2.87.0). So every popup row's l10n was unprovable,
 * and after v2.143.0 shipped four rows that were translated and still English
 * on screen, "unprovable" is not a state this project ships from.
 *
 * <p>This is the instrument instead. The popup is not something the platform
 * only builds on a mouse event: {@code BaseKit} exposes the construction as an
 * ordinary editor action, {@code build-popup-menu}, which sets the component's
 * popup from the {@code Editors/<mime>/Popup} folder. Asking the kit for that
 * action and firing it gives the REAL menu — the platform's own resolution of
 * every entry, including the display name each one chooses — and the items can
 * then be read as text.
 *
 * <p>Boot with {@code -J-Dnmox.popup.census=<file> -J-Dnmox.popup.files=a,b,c}
 * and the app opens each file, censuses its popup, writes
 * {@code mime|depth|text} lines, and exits. Without the property this class is
 * never loaded: the zero-boot-cost law holds.
 */
final class PopupCensus {

    private PopupCensus() {
    }

    /** ms after UI-ready before opening the first file — the boot must settle. */
    private static final int WARMUP_MS = 6_000;
    /** ms after opening a file before its pane is asked for a popup. */
    private static final int OPEN_MS = 2_500;
    /** A file that never opens a pane is reported, never waited on forever. */
    private static final int PANE_TRIES = 8;

    static void arm(File out) {
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            javax.swing.Timer warmup = new javax.swing.Timer(WARMUP_MS,
                    e -> Threads.startDaemon(() -> run(out), "nmox-popup-census"));
            warmup.setRepeats(false);
            warmup.start();
        });
    }

    private static void run(File out) {
        List<String> lines = new ArrayList<>();
        String spec = System.getProperty("nmox.popup.files", "");
        for (String path : spec.split(",")) {
            if (path.isBlank()) {
                continue;
            }
            try {
                census(new File(path.trim()), lines);
            } catch (RuntimeException ex) {
                lines.add("ERROR|" + path.trim() + "|" + ex);
            }
        }
        try (Writer w = Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8)) {
            for (String line : lines) {
                w.write(line);
                w.write('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        org.openide.LifecycleManager.getDefault().exit();
    }

    private static void census(File file, List<String> lines) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        if (fo == null) {
            lines.add("ERROR|" + file + "|no FileObject");
            return;
        }
        String mime = fo.getMIMEType();
        DataObject dob;
        try {
            dob = DataObject.find(fo);
        } catch (org.openide.loaders.DataObjectNotFoundException e) {
            lines.add("ERROR|" + mime + "|no DataObject");
            return;
        }
        EditorCookie ec = dob.getLookup().lookup(EditorCookie.class);
        if (ec == null) {
            lines.add("ERROR|" + mime + "|no EditorCookie");
            return;
        }
        onEdt(ec::open);
        JEditorPane pane = awaitPane(ec);
        if (pane == null) {
            lines.add("ERROR|" + mime + "|no editor pane");
            return;
        }
        onEdt(() -> {
            EditorKit kit = pane.getEditorKit();
            Action build = actionByName(kit, pane);
            if (build == null) {
                lines.add("ERROR|" + mime + "|no build-popup-menu action");
                return;
            }
            pane.setComponentPopupMenu(null);
            JPopupMenu popup = createMenu(build, pane);
            if (popup == null) {
                build.actionPerformed(new ActionEvent(pane, ActionEvent.ACTION_PERFORMED, ""));
                popup = builtMenu(pane);
            }
            if (popup == null) {
                lines.add("ERROR|" + mime + "|popup not built via "
                        + build.getClass().getName() + " | " + lastWhy);
                return;
            }
            lines.add("MIME|" + mime + "|" + file.getName());
            walk(popup, 0, mime, lines);
        });
    }

    /**
     * The kit's own action map holds {@code build-popup-menu}; a kit that
     * routes through {@code getActionByName} answers by reflection-free lookup
     * over {@link EditorKit#getActions()}, which every editor kit implements.
     */
    private static Action actionByName(EditorKit kit, JEditorPane pane) {
        for (Action a : kit.getActions()) {
            if ("build-popup-menu".equals(a.getValue(Action.NAME))) {
                return a;
            }
        }
        Action mapped = pane.getActionMap().get("build-popup-menu");
        return mapped;
    }

    private static void walk(MenuElement parent, int depth, String mime, List<String> lines) {
        for (MenuElement el : parent.getSubElements()) {
            if (el instanceof JMenuItem item) {
                String text = item.getText();
                lines.add("ROW|" + mime + "|" + depth + "|" + (text == null ? "" : text));
                if (el instanceof JMenu menu) {
                    // a JMenu's items hang off its popup, not off the menu
                    if (menu.getPopupMenu() != null) {
                        walk(menu.getPopupMenu(), depth + 1, mime, lines);
                    }
                }
            } else if (el instanceof JPopupMenu) {
                walk(el, depth, mime, lines);
            }
        }
    }

    /**
     * Ask the action to BUILD the menu rather than to show it.
     * {@code actionPerformed} is written for a real popup trigger and does
     * nothing useful without one, but the construction itself is a method on
     * the action — {@code createPopupMenu(JTextComponent)} — and that is the
     * platform's own resolution of every {@code Editors/<mime>/Popup} entry,
     * display names included. Calling it is how a context menu becomes
     * readable without a right-click.
     */
    /** Why the last build attempt produced nothing — reported, never guessed at. */
    private static volatile String lastWhy = "";

    private static JPopupMenu createMenu(Action build, JEditorPane pane) {
        for (Class<?> c = build.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Method m =
                        c.getDeclaredMethod("createPopupMenu", javax.swing.text.JTextComponent.class);
                m.setAccessible(true);
                Object menu = m.invoke(build, pane);
                if (menu instanceof JPopupMenu p) {
                    return p;
                }
                lastWhy = c.getName() + " returned " + menu;
            } catch (NoSuchMethodException e) {
                lastWhy = "no createPopupMenu on " + c.getName();
                continue;
            } catch (ReflectiveOperationException | RuntimeException e) {
                lastWhy = c.getName() + " -> " + e
                        + (e.getCause() == null ? "" : " cause " + e.getCause());
                return null;
            }
        }
        return null;
    }

    /**
     * Where the built menu lands. {@code BuildPopupMenuAction} does NOT call
     * {@code setComponentPopupMenu} — it hands the menu to the editor's own
     * {@code EditorUI}, which is what shows it on a trigger. Read through
     * reflection rather than a module dependency: this is a diagnostic
     * instrument that runs only under a boot property, and making the whole
     * ui module depend on editor-lib to reach one getter would be a real
     * coupling bought for a walk.
     */
    private static JPopupMenu builtMenu(JEditorPane pane) {
        JPopupMenu direct = pane.getComponentPopupMenu();
        if (direct != null) {
            return direct;
        }
        try {
            // NOT the pane's own loader: that is openide.text, which cannot
            // see editor-lib. The platform's system classloader is the one
            // that spans modules — the v2.67.0 lesson, in a new costume.
            ClassLoader all = org.openide.util.Lookup.getDefault().lookup(ClassLoader.class);
            Class<?> utils = Class.forName("org.netbeans.editor.Utilities", true,
                    all != null ? all : pane.getClass().getClassLoader());
            Object ui = utils.getMethod("getEditorUI", javax.swing.text.JTextComponent.class)
                    .invoke(null, pane);
            if (ui == null) {
                lastWhy = lastWhy + " ; getEditorUI null";
                return null;
            }
            Object menu = ui.getClass().getMethod("getPopupMenu").invoke(ui);
            if (menu == null) {
                lastWhy = lastWhy + " ; EditorUI.getPopupMenu null";
            }
            return menu instanceof JPopupMenu p ? p : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            lastWhy = lastWhy + " ; builtMenu " + e;
            return null;
        }
    }

    /** Swing work runs on the EDT; the census itself runs off it so it can wait. */
    private static void onEdt(Runnable body) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(body);
        } catch (java.lang.reflect.InvocationTargetException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JEditorPane awaitPane(EditorCookie ec) {
        for (int i = 0; i < PANE_TRIES; i++) {
            JEditorPane[] panes = ec.getOpenedPanes();
            if (panes != null && panes.length > 0) {
                return panes[0];
            }
            try {
                Thread.sleep(OPEN_MS / PANE_TRIES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}
