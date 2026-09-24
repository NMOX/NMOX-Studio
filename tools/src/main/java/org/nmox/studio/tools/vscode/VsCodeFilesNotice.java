package org.nmox.studio.tools.vscode;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javax.swing.Action;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.Chords;
import org.nmox.studio.core.util.PlainText;
import org.openide.awt.Actions;
import org.openide.awt.NotificationDisplayer;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;
import org.openide.windows.OnShowing;

/**
 * Says once, per project, that its {@code .vscode} files are read
 * (3.1.0). Tasks, launch configurations and formatting settings all work,
 * and nothing on screen said so: a switcher opening their repository had
 * no reason to type a task's name into Quick Search. The first time a
 * project with a {@code tasks.json}, a {@code launch.json} or a
 * {@code settings.json} is aimed, a balloon names what was found and
 * where it is; its click opens Quick Search.
 *
 * <p>Boot costs one listener. Aim events are coalesced ({@link #SETTLE_MS})
 * and the files are read on this class's own lane, never the EDT; the
 * balloon speaks only if the project it is about is still the one aimed
 * (a result belongs to the workspace that produced it). "Shown" is
 * recorded per project, one preference key per path, so a project that
 * has been told is never told again.
 */
@OnShowing
public final class VsCodeFilesNotice implements Runnable {

    /** How long aim events settle before the files are read. */
    static final int SETTLE_MS = 1_500;

    private static final RequestProcessor RP = new RequestProcessor("VS Code files notice", 1);
    private static final RequestProcessor.Task CHECK = RP.create(VsCodeFilesNotice::checkAimed);

    /** Where "shown" is recorded; a seam so a test never touches the user's preferences. */
    static volatile Supplier<Preferences> shownStore =
            () -> NbPreferences.forModule(VsCodeFilesNotice.class).node("vscodeFilesShown");

    /** Where the notice goes: title and detail; a seam for tests. */
    static volatile BiConsumer<String, String> sink = VsCodeFilesNotice::balloon;

    /** What one project's .vscode folder holds that the IDE reads. */
    record Found(int tasks, int configurations, boolean settings) {
        boolean nothing() {
            return tasks == 0 && configurations == 0 && !settings;
        }
    }

    @Override
    public void run() {
        ProjectAim aim = ProjectAim.find();
        if (aim == null) {
            return;
        }
        aim.addListener(() -> CHECK.schedule(SETTLE_MS));
        CHECK.schedule(SETTLE_MS);
    }

    private static void checkAimed() {
        ProjectAim aim = ProjectAim.find();
        if (aim != null) {
            check(aim.projectDir(), aim::projectDir);
        }
    }

    /**
     * Tells about {@code dir} if it has something to tell, has not been
     * told, and is still aimed ({@code aimedNow}) when the files are read.
     */
    static void check(File dir, Supplier<File> aimedNow) {
        if (dir == null) {
            return;
        }
        String key = key(dir);
        Preferences shown = shownStore.get();
        if (shown.get(key, null) != null) {
            return;
        }
        Found found = found(dir);
        if (found.nothing() || !dir.equals(aimedNow.get())) {
            return;
        }
        shown.put(key, dir.getAbsolutePath());
        sink.accept(NbBundle.getMessage(VsCodeFilesNotice.class, "VsCodeFilesNotice_title", dir.getName()), detail(found, Utilities.isMac()));
    }

    /** What {@code dir}'s .vscode folder holds that the IDE reads. */
    static Found found(File dir) {
        int tasks = VsCodeTasks.read(dir).size();
        int configurations = VsCodeLaunch.read(dir).size();
        return new Found(tasks, configurations, setsIndentation(new File(new File(dir, ".vscode"), "settings.json")));
    }

    /** The settings the editor reads for indentation (editor.standards.VsCodeSettings). */
    static final java.util.List<String> INDENTATION_KEYS =
            java.util.List.of("\"editor.tabSize\"", "\"editor.insertSpaces\"", "\"editor.indentSize\"");

    /**
     * Whether {@code settings} names an indentation setting: the notice
     * says the file "sets the indentation" only then, not for the common
     * settings.json that only excludes folders from search (the 3.1.0
     * review). Read bounded; a file that cannot be read says nothing.
     */
    static boolean setsIndentation(File settings) {
        if (!settings.isFile()) {
            return false;
        }
        try {
            String text = org.nmox.studio.core.util.BoundedReads.read(settings, VsCodeTasks.MAX_BYTES);
            return INDENTATION_KEYS.stream().anyMatch(text::contains);
        } catch (java.io.IOException unreadable) {
            return false;
        }
    }

    /** The balloon's sentences: where the tasks and configurations are, and that the settings apply. */
    static String detail(Found found, boolean mac) {
        StringBuilder out = new StringBuilder();
        if (found.tasks() > 0 || found.configurations() > 0) {
            out.append(NbBundle.getMessage(VsCodeFilesNotice.class, "VsCodeFilesNotice_run", Chords.human("DS-P", mac)));
        }
        if (found.settings()) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(NbBundle.getMessage(VsCodeFilesNotice.class, "VsCodeFilesNotice_settings"));
        }
        return out.toString();
    }

    /** A preference key for a path: short and stable, since keys are capped at 80 characters. */
    static String key(File dir) {
        return UUID.nameUUIDFromBytes(dir.getAbsolutePath().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void balloon(String title, String detail) {
        // a folder's name is somebody else's text: guarded, like every sink
        // Swing could render as markup
        EventQueue.invokeLater(() -> NotificationDisplayer.getDefault().notify(PlainText.plain(title),
                javax.swing.UIManager.getIcon("OptionPane.informationIcon"), PlainText.plain(detail),
                e -> openQuickSearch()));
    }

    private static void openQuickSearch() {
        Action search = Actions.forID("Edit", "org.netbeans.modules.quicksearch.QuickSearchAction");
        if (search != null) {
            search.actionPerformed(new ActionEvent(VsCodeFilesNotice.class, ActionEvent.ACTION_PERFORMED, ""));
        }
    }
}
