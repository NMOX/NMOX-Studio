package org.nmox.studio.tools.vscode;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
 * project with a {@code tasks.json}, a {@code launch.json}, a
 * {@code settings.json} or an {@code extensions.json} is aimed, a balloon
 * names what was found and where it is. Its click opens Quick Search —
 * or, when the repository recommends extensions, the sheet that says
 * what covers each one here ({@link RecommendedExtensionsAction}): the
 * tasks are one chord away and the sentence names it, while "where are
 * my extensions?" has no other door a newcomer would find.
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

    /** Where the notice goes; a seam for tests. */
    interface Sink {

        /**
         * @param extensionsOf the project whose extensions sheet the click
         *                     opens, or null when the click opens Quick Search
         */
        void tell(String title, String detail, File extensionsOf);
    }

    static volatile Sink sink = VsCodeFilesNotice::balloon;

    /** Puts the production balloon back; a test that swapped the sink calls this. */
    static void resetSink() {
        sink = VsCodeFilesNotice::balloon;
    }

    /**
     * What one project's .vscode folder holds that the IDE reads.
     * {@code extensions} is how many extensions its extensions.json
     * recommends: the IDE installs none of them, and says what covers each.
     */
    record Found(int tasks, int configurations, boolean settings, int extensions) {

        /** A folder with no extensions.json. */
        Found(int tasks, int configurations, boolean settings) {
            this(tasks, configurations, settings, 0);
        }

        boolean nothing() {
            return tasks == 0 && configurations == 0 && !settings && extensions == 0;
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
        sink.tell(title(dir),
                detail(found, Utilities.isMac()), found.extensions() > 0 ? dir : null);
    }

    /**
     * The balloon's title. A folder's name is somebody else's text, and the
     * platform builds a notification into markup with a builder that throws
     * on a control character (3.5.13), so the name is one line of ordinary
     * characters, and a long one is cut.
     */
    static String title(File dir) {
        return NbBundle.getMessage(VsCodeFilesNotice.class, "VsCodeFilesNotice_title",
                PlainText.oneLine(dir.getName(), NAME_MAX));
    }

    /** The most of a folder's name the title shows. */
    static final int NAME_MAX = 80;

    /** What {@code dir}'s .vscode folder holds that the IDE reads. */
    static Found found(File dir) {
        int tasks = VsCodeTasks.read(dir).size();
        int configurations = VsCodeLaunch.read(dir).size();
        return new Found(tasks, configurations, setsSomethingRead(new File(new File(dir, ".vscode"), "settings.json")),
                VsCodeExtensions.read(dir).total());
    }

    /**
     * Every setting a project's settings.json is read for: the editor's
     * ({@code editor.standards.VsCodeSettings}: indentation, trimming,
     * the final newline, line endings, the ruler, word wrap, format on
     * save) and the trees' and search's ({@code core.util.VsCodeExcludes}).
     * {@code VsCodeFilesNoticeTest} reads both files and fails when one
     * reads a key this list lacks.
     */
    static final java.util.Set<String> SETTINGS_KEYS = java.util.Set.of(
            "editor.tabSize", "editor.insertSpaces", "editor.indentSize",
            "files.trimTrailingWhitespace", "files.insertFinalNewline", "files.eol",
            "editor.rulers", "editor.wordWrap", "editor.formatOnSave",
            "files.exclude", "search.exclude");

    /**
     * Whether {@code settings} holds a setting this IDE reads, at the top
     * level or in a language block: the notice says the file applies only
     * then, not for a settings.json of colour themes and fonts. Read
     * bounded and parsed as JSONC, so a commented-out line is not a
     * setting; a file that cannot be read or parsed says nothing.
     */
    static boolean setsSomethingRead(File settings) {
        if (!settings.isFile()) {
            return false;
        }
        try {
            String text = org.nmox.studio.core.util.BoundedReads.read(settings, VsCodeTasks.MAX_BYTES);
            org.json.JSONObject json = new org.json.JSONObject(org.nmox.studio.core.util.Jsonc.strip(text));
            for (String key : json.keySet()) {
                if (SETTINGS_KEYS.contains(key)) {
                    return true;
                }
                if (key.startsWith("[") && json.opt(key) instanceof org.json.JSONObject block
                        && block.keySet().stream().anyMatch(SETTINGS_KEYS::contains)) {
                    return true;
                }
            }
            return false;
        } catch (java.io.IOException | org.json.JSONException | StackOverflowError unreadable) {
            return false;
        }
    }

    /**
     * The balloon's sentences: where the tasks and configurations are, that
     * the settings apply, and how many extensions are recommended.
     */
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
        if (found.extensions() > 0) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(NbBundle.getMessage(VsCodeFilesNotice.class, "VsCodeFilesNotice_extensions", found.extensions()));
        }
        return out.toString();
    }

    /** A preference key for a path: short and stable, since keys are capped at 80 characters. */
    static String key(File dir) {
        return UUID.nameUUIDFromBytes(dir.getAbsolutePath().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void balloon(String title, String detail, File extensionsOf) {
        // a folder's name is somebody else's text: one line already (title),
        // and guarded, like every sink Swing could render as markup
        EventQueue.invokeLater(() -> NotificationDisplayer.getDefault().notify(PlainText.plain(title),
                javax.swing.UIManager.getIcon("OptionPane.informationIcon"), PlainText.plain(detail),
                e -> {
                    if (extensionsOf == null) {
                        openQuickSearch();
                    } else {
                        openExtensions(extensionsOf);
                    }
                }));
    }

    /**
     * The sheet for the project the notice was about. It is shown only if
     * that project is still the one aimed when its file has been read: a
     * balloon can be clicked long after the aim moved on.
     */
    private static void openExtensions(File dir) {
        ProjectAim aim = ProjectAim.find();
        if (aim != null) {
            RecommendedExtensionsAction.show(dir, aim::projectDir);
        }
    }

    private static void openQuickSearch() {
        Action search = Actions.forID("Edit", "org.netbeans.modules.quicksearch.QuickSearchAction");
        if (search != null) {
            search.actionPerformed(new ActionEvent(VsCodeFilesNotice.class, ActionEvent.ACTION_PERFORMED, ""));
        }
    }
}
