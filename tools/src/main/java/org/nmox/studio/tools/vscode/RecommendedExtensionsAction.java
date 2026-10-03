package org.nmox.studio.tools.vscode;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.Action;

import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.spi.ServerCatalog;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Door;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Equivalent;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Facts;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Kind;
import org.nmox.studio.tools.vscode.VsCodeExtensions.Recommendations;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Tools ▸ Recommended VS Code Extensions… — and, in Quick Search, VS
 * Code's own title for it, <i>Extensions: Show Recommended Extensions</i>.
 * A switcher's first question on opening their repository is "where are
 * my extensions?". They do not install here; this opens a sheet that
 * lists what the repository's {@code .vscode/extensions.json} recommends
 * and says, per extension, what covers it in this product
 * ({@link ExtensionEquivalents}).
 *
 * <p>Always enabled, like the kit actions: with no project, no file, or
 * a file that does not parse, it says which on the status line rather
 * than sitting grey without a reason.
 *
 * <p>The file is read and the language servers are looked for on this
 * class's own lane ({@link #prepare}), never the event thread; the
 * result is shown on the event thread ({@link #present}) only if the
 * project it was read from is still the one aimed — a result belongs to
 * the workspace that produced it — and the sheet closes itself when the
 * aim moves on.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.tools.vscode.RecommendedExtensionsAction")
@ActionRegistration(displayName = "#CTL_RecommendedExtensionsAction", lazy = true)
@ActionReference(path = "Menu/Tools", position = 94)
public final class RecommendedExtensionsAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("VS Code extensions sheet", 1);

    /** Where a prepared answer goes: a seam, so a test needs no window. */
    interface Presenter {

        /** Nothing is shown; this is why. */
        void refuse(String message);

        /** The sheet for {@code root}: its heading and its rows. */
        void sheet(File root, String heading, List<Line> lines, Map<Door, Action> doors);
    }

    /**
     * One row of the sheet.
     *
     * @param id    the extension id (or a malformed entry's text)
     * @param here  what covers it here, already a sentence
     * @param opens the door the Open button runs for this row, or null
     */
    record Line(String id, String here, Door opens) {
    }

    /**
     * What was read for one project, off the event thread.
     *
     * @param root        the project it was read from
     * @param recommended what its extensions.json recommends
     * @param catalog     whether the editor's server catalog was there to ask
     * @param servers     the catalog's answer for each server a row names
     */
    record Prepared(File root, Recommendations recommended, boolean catalog,
            Map<String, ServerCatalog.Server> servers) {
    }

    /** The doors that resolve, asked on the event thread; a seam for tests. */
    static volatile Supplier<Map<Door, Action>> doors = ExtensionDoors::resolve;

    /** Puts the production door lookup back; a test that swapped it calls this. */
    static void resetDoors() {
        doors = ExtensionDoors::resolve;
    }

    /** Where answers go in the product: the status line, or the dialog. */
    static volatile Presenter presenter = new Presenter() {
        @Override
        public void refuse(String message) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(message));
        }

        @Override
        public void sheet(File root, String heading, List<Line> lines, Map<Door, Action> doors) {
            RecommendedExtensionsSheet.show(root, heading, lines, doors);
        }
    };

    @Override
    public void actionPerformed(ActionEvent e) {
        ProjectAim aim = ProjectAim.find();
        File root = aim == null ? null : aim.projectDir();
        if (root == null) {
            presenter.refuse(message("VsCodeExtensions_noProject"));
            return;
        }
        show(root, aim::projectDir);
    }

    /**
     * Reads {@code root}'s recommendations on this class's lane and shows
     * them on the event thread, if {@code root} is still what
     * {@code aimedNow} answers by then.
     */
    static void show(File root, Supplier<File> aimedNow) {
        RP.post(() -> {
            Prepared prepared = prepare(root, ServerCatalog.find());
            EventQueue.invokeLater(() -> present(prepared, aimedNow, presenter));
        });
    }

    /**
     * The disk half: the file, and for every row that names a language
     * server, whether the editor starts it and has it installed. Never
     * called on the event thread.
     */
    static Prepared prepare(File root, ServerCatalog catalog) {
        Recommendations recommended = VsCodeExtensions.read(root);
        Map<String, ServerCatalog.Server> servers = new HashMap<>();
        if (catalog != null) {
            Set<String> asked = new HashSet<>();
            for (VsCodeExtensions.Entry entry : recommended.entries()) {
                Equivalent e = ExtensionEquivalents.of(entry);
                if (e.kind() == Kind.SERVER && asked.add(e.server())) {
                    ServerCatalog.Server known = catalog.server(e.server(), root);
                    if (known != null) {
                        servers.put(e.server(), known);
                    }
                }
            }
        }
        return new Prepared(root, recommended, catalog != null, servers);
    }

    /**
     * The event-thread half: refuses out loud when the project is no
     * longer the one aimed, when its file could not be read, or when it
     * recommends nothing; otherwise hands the sheet its rows.
     */
    static void present(Prepared prepared, Supplier<File> aimedNow, Presenter out) {
        File root = prepared.root();
        if (!root.equals(aimedNow.get())) {
            out.refuse(message("VsCodeExtensions_aimedAway", root.getName()));
            return;
        }
        Recommendations recommended = prepared.recommended();
        if (recommended.unreadable()) {
            out.refuse(message("VsCodeExtensions_unreadable", root.getName()));
            return;
        }
        if (recommended.entries().isEmpty()) {
            out.refuse(message("VsCodeExtensions_none", root.getName()));
            return;
        }
        Map<Door, Action> resolved = doors.get();
        Map<Door, String> names = new EnumMap<>(Door.class);
        resolved.forEach((door, action) -> {
            String name = ExtensionDoors.nameOf(action);
            if (name != null) {
                names.put(door, name);
            }
        });
        Facts facts = new Facts(names, prepared.catalog(), prepared.servers());
        List<Line> lines = new ArrayList<>();
        for (VsCodeExtensions.Entry entry : recommended.entries()) {
            Equivalent e = ExtensionEquivalents.of(entry);
            lines.add(new Line(entry.id(), ExtensionEquivalents.sentence(e, facts), ExtensionEquivalents.opens(e, facts)));
        }
        out.sheet(root, heading(recommended), lines, resolved);
    }

    /** How many the repository recommends, and how many of those the sheet does not list. */
    static String heading(Recommendations recommended) {
        String heading = message("VsCodeExtensions_heading", recommended.total());
        return recommended.notShown() == 0 ? heading
                : heading + " " + message("VsCodeExtensions_more", recommended.notShown());
    }

    static String message(String key, Object... args) {
        return args.length == 0 ? NbBundle.getMessage(RecommendedExtensionsAction.class, key)
                : NbBundle.getMessage(RecommendedExtensionsAction.class, key, args);
    }
}
