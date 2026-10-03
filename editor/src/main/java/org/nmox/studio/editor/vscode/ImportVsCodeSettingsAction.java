package org.nmox.studio.editor.vscode;

import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Location;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Plan;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Tools ▸ Import VS Code Settings… — and, in Quick Search,
 * <i>Preferences: Import VS Code Settings</i>. A person moving from VS
 * Code has years of preferences in their own {@code settings.json}; this
 * reads that file, once, when they ask, and shows what it can bring
 * across before anything is written ({@link VsCodeUserSettings}).
 *
 * <p>Nothing is read at startup, on aim, or anywhere but this gesture:
 * the file is the user's, outside every project. It is found
 * ({@link VsCodeUserSettings#candidates}), read bounded
 * ({@link BoundedReads}) and planned on this class's own lane
 * ({@link #prepare}), never the event thread; the sheet is shown on the
 * event thread ({@link #present}); and preferences are written only when
 * the person presses Apply in it. A refusal (no file, too large, not
 * JSON, nothing this product has a place for) is said on the status line.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.editor.vscode.ImportVsCodeSettingsAction")
@ActionRegistration(displayName = "#CTL_ImportVsCodeSettings", lazy = true)
@ActionReference(path = "Menu/Tools", position = 89)
public final class ImportVsCodeSettingsAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("VS Code user settings import", 1);

    /** Why nothing is shown, when nothing is. */
    enum Problem {
        NOT_FOUND, TOO_LARGE, UNREADABLE, NOTHING
    }

    /**
     * What was found and read, off the event thread.
     *
     * @param candidates every place looked in
     * @param found      the places that had a file, in order
     * @param read       the one read, or null when none was found
     * @param plan       what it says, or null on a problem
     * @param problem    why there is no plan, or null
     */
    record Prepared(List<Location> candidates, List<Location> found, Location read, Plan plan, Problem problem) {
    }

    /** How a file is read; a seam so a test can count the reads. The product's is bounded. */
    interface Reader {
        String read(Path file) throws IOException;
    }

    /** Where a prepared answer goes: a seam, so a test needs no window. */
    interface Presenter {

        /** Nothing is shown; this is why. */
        void refuse(String message);

        /** The sheet over a plan. */
        void sheet(Prepared prepared);
    }

    static final Reader BOUNDED = file -> BoundedReads.read(file, VsCodeUserSettings.MAX_BYTES);

    /** The places looked in on this system; a seam. */
    static volatile Supplier<List<Location>> candidates = () -> VsCodeUserSettings.candidates(
            System.getProperty("os.name"), System.getProperty("user.home"), System::getenv);

    /** Where answers go in the product: the status line, or the sheet. */
    static volatile Presenter presenter = new Presenter() {
        @Override
        public void refuse(String message) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(message));
        }

        @Override
        public void sheet(Prepared prepared) {
            ImportVsCodeSettingsSheet.show(prepared);
        }
    };

    @Override
    public void actionPerformed(ActionEvent e) {
        show(null);
    }

    /** Finds, reads and plans on this class's lane ({@code choice} null: the first found), then presents. */
    static void show(Location choice) {
        prepareThen(choice, prepared -> present(prepared, presenter));
    }

    /** Prepares on this class's lane and hands the result to {@code then} on the event thread. */
    static void prepareThen(Location choice, java.util.function.Consumer<Prepared> then) {
        RP.post(() -> {
            Prepared prepared = prepare(candidates.get(), Files::isRegularFile, choice, BOUNDED,
                    ImportVsCodeSettingsAction::installedFamilies);
            EventQueue.invokeLater(() -> then.accept(prepared));
        });
    }

    /**
     * The disk half. Never called on the event thread.
     *
     * @param all    every place to look, in order
     * @param exists whether a place has a file
     * @param choice the place to read, or null for the first that has a file
     * @param reader how a file is read
     * @param fonts  the font families this machine has, asked only when a file is read
     */
    static Prepared prepare(List<Location> all, Predicate<Path> exists, Location choice, Reader reader,
            Supplier<Collection<String>> fonts) {
        List<Location> found = VsCodeUserSettings.found(all, exists);
        if (found.isEmpty()) {
            return new Prepared(all, found, null, null, Problem.NOT_FOUND);
        }
        Location read = choice != null && found.contains(choice) ? choice : found.get(0);
        String text;
        try {
            text = reader.read(read.file());
        } catch (BoundedReads.TooLarge tooLarge) {
            return new Prepared(all, found, read, null, Problem.TOO_LARGE);
        } catch (IOException | RuntimeException unreadable) {
            return new Prepared(all, found, read, null, Problem.UNREADABLE);
        }
        Plan plan;
        try {
            plan = VsCodeUserSettings.plan(text, fonts.get());
        } catch (VsCodeUserSettings.Unreadable notJson) {
            return new Prepared(all, found, read, null, Problem.UNREADABLE);
        }
        if (plan.rows().isEmpty()) {
            return new Prepared(all, found, read, plan, Problem.NOTHING);
        }
        return new Prepared(all, found, read, plan, null);
    }

    /** The event-thread half: the sheet, or the sentence saying why there is none. */
    static void present(Prepared prepared, Presenter out) {
        if (prepared.problem() != null) {
            out.refuse(problem(prepared));
            return;
        }
        out.sheet(prepared);
    }

    /** The sentence for a prepared answer that has a problem. */
    static String problem(Prepared prepared) {
        return switch (prepared.problem()) {
            case NOT_FOUND -> {
                List<String> places = new ArrayList<>();
                for (Location l : prepared.candidates()) {
                    places.add(l.file().toString());
                }
                yield message("ImportVsCode_notFound", String.join(", ", places));
            }
            case TOO_LARGE -> message("ImportVsCode_tooLarge", prepared.read().file().toString());
            case UNREADABLE -> message("ImportVsCode_unreadable", prepared.read().file().toString());
            case NOTHING -> message("ImportVsCode_nothing", prepared.read().file().toString());
        };
    }

    /** The font families this machine has; empty where there is no font system to ask. */
    static Collection<String> installedFamilies() {
        try {
            return List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        } catch (RuntimeException | LinkageError none) {
            return List.of();
        }
    }

    static String message(String key, Object... args) {
        return args.length == 0 ? NbBundle.getMessage(ImportVsCodeSettingsAction.class, key)
                : NbBundle.getMessage(ImportVsCodeSettingsAction.class, key, args);
    }
}
