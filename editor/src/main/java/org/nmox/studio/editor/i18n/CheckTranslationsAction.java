package org.nmox.studio.editor.i18n;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.editor.i18n.I18nCheck.Finding;
import org.nmox.studio.editor.i18n.I18nCheck.Kind;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Tools ▸ Check Translations… (v2.177.0): reads the aimed project's
 * translation catalogs, runs {@link I18nCheck} off the EDT on a named
 * lane, publishes the findings to the {@link DiagnosticsBus} under the
 * tool name {@code i18n} — squiggles on open catalogs, Action Items rows
 * for the rest, the Agent Port's {@code nmox://diagnostics} — and ends
 * with one status-line sentence. Always enabled; with no project aimed it
 * refuses out loud. A catalog that cannot be parsed publishes NOTHING
 * (the slither rule: a crash is not an all-clear, and the previous run's
 * squiggles stay true) and names itself on the status line; a clean run
 * publishes an empty batch, which clears the old ones.
 *
 * <p>Lives in the editor module beside the pure cores it drives — the
 * same home as Tools ▸ Language Servers…, and the only module the cores
 * are visible from.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.editor.i18n.CheckTranslationsAction")
@ActionRegistration(displayName = "#CTL_CheckTranslationsAction")
@ActionReference(path = "Menu/Tools", position = 92)
@Messages({
    "CTL_CheckTranslationsAction=Check Translations…",
    "CheckTranslationsAction_aimFirst=Aim the studio at a project first (open a folder or project).",
    "CheckTranslationsAction_noCatalogs=Translations: no catalogs found under {0} — i18next, vue-i18n, Angular XLIFF, Lingui, Paraglide, react-intl, svelte-i18n, or a locales/ folder beside an i18n.js",
    "CheckTranslationsAction_parseError=Translations: could not read {0} ({1}) — nothing published",
    "CheckTranslationsAction_aimMoved=Translations: the project changed while checking — nothing published. Run again.",
    "CheckTranslationsAction_summary=Translations: {0}",
    "CheckTranslationsAction_join=, ",
    "CheckTranslationsAction_catalogs={1,choice,0#{0} catalogs|1#{0} catalog|1<{0} catalogs}",
    "CheckTranslationsAction_clean=nothing to report",
    "CheckTranslationsAction_missing={0} missing {1}",
    "CheckTranslationsAction_identical={0} identical {1}",
    "CheckTranslationsAction_identicalMore={0} identical {1} (…and {2} more)",
    "CheckTranslationsAction_mismatch={0}: {2,choice,1#{1} placeholder mismatch|1<{1} placeholder mismatches}",
    "CheckTranslationsAction_unused={1,choice,1#{0} key unused|1<{0} keys unused}",
    "CheckTranslationsAction_possiblyUnused={1,choice,1#{0} key possibly unused|1<{0} keys possibly unused}",
    "CheckTranslationsAction_unusedPartial=unused keys not judged (the source walk stopped at its cap)",
    "CheckTranslationsAction_unusedLingui=unused keys not judged (Lingui keys are source text)",
    "CheckTranslationsAction_yaml=YAML catalogs are not read yet — JSON catalogs are",
    "CheckTranslationsAction_findingMissing={0}: missing {1}",
    "CheckTranslationsAction_findingUntranslated={0}: {1} is not translated yet ({2})",
    "CheckTranslationsAction_findingIdentical={0}: {1} is identical to the source",
    "CheckTranslationsAction_findingMismatch={0}: {1} formats with different arguments than the source ({2})",
    "CheckTranslationsAction_findingUnused={0} is not referenced by any source file",
    "CheckTranslationsAction_findingPossiblyUnused={0} is not referenced by name — dynamic lookups seen: {1}",
    "CheckTranslationsAction_findingLost={0} is no longer in {1} — the extract is the truth"
})
public final class CheckTranslationsAction implements ActionListener {

    /** The bus tool name every consumer labels these findings with. */
    static final String TOOL = "i18n";

    private static final RequestProcessor RP = new RequestProcessor("nmox-i18n-check", 1);

    @Override
    public void actionPerformed(ActionEvent e) {
        ProjectAim aim = ProjectAim.find();
        File project = aim == null ? null : aim.projectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.CheckTranslationsAction_aimFirst()));
            return;
        }
        // the catalogs and the source walk are disk reads: never on the EDT
        RP.post(() -> run(project.toPath(), CheckTranslationsAction::aimedDir));
    }

    /** The project aimed NOW, or null — read again at publish time, never assumed from click time. */
    static File aimedDir() {
        ProjectAim aim = ProjectAim.find();
        return aim == null ? null : aim.projectDir();
    }

    /**
     * The whole run, off the EDT; only the status sentence hops back. The
     * findings publish only if {@code aimNow} still answers {@code root}: a
     * result belongs to the workspace that produced it (the v1.172.0 law), and
     * a walk of four hundred files takes long enough for the aim to move —
     * publishing then would land the OLD project's rows as the bus's current
     * {@code i18n} batch (the 2026-09-17 arc review, re-aim lens).
     */
    static void run(Path root, java.util.function.Supplier<File> aimNow) {
        I18nCatalogs.Catalogs catalogs;
        try {
            catalogs = I18nCatalogs.detect(root);
        } catch (I18nCatalogs.ParseFailure failed) {
            // publish nothing: the last good batch stays true until a real
            // one replaces it, and the reason lands where the user looks
            status(Bundle.CheckTranslationsAction_parseError(
                    root.relativize(failed.file()).toString(), failed.getMessage()));
            return;
        }
        if (catalogs == null) {
            status(Bundle.CheckTranslationsAction_noCatalogs(root.getFileName().toString()));
            return;
        }
        I18nUsage.Usage usage = catalogs.yamlRefusal() == null
                && catalogs.format() != I18nCatalogs.Format.LINGUI_PO
                && catalogs.format() != I18nCatalogs.Format.ANGULAR_XLIFF
                ? I18nUsage.scan(root) : null;
        I18nCheck.Report report = I18nCheck.run(catalogs, usage);
        if (!stillAimed(root, aimNow.get())) {
            status(Bundle.CheckTranslationsAction_aimMoved());
            return;
        }
        DiagnosticsBus.publish(TOOL, problems(report));
        status(sentence(catalogs, report));
    }

    /** True when the project aimed now is the one the run read. */
    static boolean stillAimed(Path root, File now) {
        return now != null
                && root.toAbsolutePath().normalize().equals(now.toPath().toAbsolutePath().normalize());
    }

    /** The status sentence: the fragments joined the language's own way. */
    static String sentence(I18nCatalogs.Catalogs catalogs, I18nCheck.Report report) {
        return Bundle.CheckTranslationsAction_summary(
                String.join(Bundle.CheckTranslationsAction_join(), summary(catalogs, report)));
    }

    /** Every finding as a bus problem, its message in the user's language. */
    static List<DiagnosticsBus.Problem> problems(I18nCheck.Report report) {
        List<DiagnosticsBus.Problem> out = new ArrayList<>();
        for (Finding f : report.findings()) {
            out.add(new DiagnosticsBus.Problem(f.file().toFile(), f.line(), message(f), f.error()));
        }
        return out;
    }

    static String message(Finding f) {
        return switch (f.kind()) {
            case MISSING -> f.detail().isEmpty()
                    ? Bundle.CheckTranslationsAction_findingMissing(f.locale(), f.key())
                    : Bundle.CheckTranslationsAction_findingUntranslated(f.locale(), f.key(), f.detail());
            case IDENTICAL -> Bundle.CheckTranslationsAction_findingIdentical(f.locale(), f.key());
            case MISMATCH -> Bundle.CheckTranslationsAction_findingMismatch(f.locale(), f.key(), f.detail());
            case UNUSED -> f.detail().isEmpty()
                    ? Bundle.CheckTranslationsAction_findingUnused(f.key())
                    : Bundle.CheckTranslationsAction_findingLost(f.key(), f.detail());
            case POSSIBLY_UNUSED -> Bundle.CheckTranslationsAction_findingPossiblyUnused(f.key(), f.detail());
            case YAML_REFUSED -> Bundle.CheckTranslationsAction_yaml();
        };
    }

    /**
     * The sentence's fragments: the catalog count, then per locale what
     * it misses, copies and mismatches, then the unused verdict — or the
     * reason it was not judged. Counts ride the bundles' choice formats
     * (Polish and Arabic need their own plural branches), the number
     * passed twice, once as text and once for the choice.
     */
    static List<String> summary(I18nCatalogs.Catalogs catalogs, I18nCheck.Report report) {
        List<String> parts = new ArrayList<>();
        int n = catalogs.catalogs().size();
        parts.add(Bundle.CheckTranslationsAction_catalogs(String.valueOf(n), n));
        if (catalogs.yamlRefusal() != null) {
            parts.add(Bundle.CheckTranslationsAction_yaml());
            return parts;
        }
        Map<String, int[]> perLocale = new TreeMap<>();
        for (Finding f : report.findings()) {
            if (f.locale() == null) {
                continue;
            }
            int[] counts = perLocale.computeIfAbsent(f.locale(), k -> new int[3]);
            switch (f.kind()) {
                case MISSING -> counts[0]++;
                case IDENTICAL -> counts[1]++;
                case MISMATCH -> counts[2]++;
                default -> { }
            }
        }
        for (Map.Entry<String, int[]> e : perLocale.entrySet()) {
            String locale = e.getKey();
            int[] c = e.getValue();
            if (c[0] > 0) {
                parts.add(Bundle.CheckTranslationsAction_missing(locale, String.valueOf(c[0])));
            }
            int identicalTotal = report.identicalTotals().getOrDefault(locale, 0);
            if (identicalTotal > c[1]) {
                parts.add(Bundle.CheckTranslationsAction_identicalMore(locale,
                        String.valueOf(c[1]), String.valueOf(identicalTotal - c[1])));
            } else if (c[1] > 0) {
                parts.add(Bundle.CheckTranslationsAction_identical(locale, String.valueOf(c[1])));
            }
            if (c[2] > 0) {
                parts.add(Bundle.CheckTranslationsAction_mismatch(locale, String.valueOf(c[2]), c[2]));
            }
        }
        int unused = report.of(Kind.UNUSED).size();
        int possibly = report.of(Kind.POSSIBLY_UNUSED).size();
        if (unused > 0) {
            parts.add(Bundle.CheckTranslationsAction_unused(String.valueOf(unused), unused));
        }
        if (possibly > 0) {
            parts.add(Bundle.CheckTranslationsAction_possiblyUnused(String.valueOf(possibly), possibly));
        }
        if (I18nCheck.SKIPPED_PARTIAL.equals(report.unusedSkipped())) {
            parts.add(Bundle.CheckTranslationsAction_unusedPartial());
        } else if (I18nCheck.SKIPPED_LINGUI.equals(report.unusedSkipped())) {
            parts.add(Bundle.CheckTranslationsAction_unusedLingui());
        }
        if (parts.size() == 1 && report.findings().isEmpty()) {
            parts.add(Bundle.CheckTranslationsAction_clean());
        }
        return parts;
    }

    private static void status(String text) {
        java.awt.EventQueue.invokeLater(
                () -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(text)));
    }
}
