package org.nmox.studio.editor.docs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.editor.fullstack.BoundedWalk;
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
 * Tools ▸ Check Markdown Links… (3.2.0): every relative link and image in
 * the aimed project's Markdown, checked the way GitHub will render it
 * ({@link MarkdownLinks}), published to the {@link DiagnosticsBus} under
 * the tool name {@code markdown} - a squiggle on the link in an open file,
 * an Action Items row for the rest, the Agent Port's diagnostics - with one
 * status-line sentence. Nothing leaves the machine: a link with a scheme is
 * not checked.
 *
 * <p>The same shape as Tools ▸ Check Translations… (v2.177.0): always
 * enabled, a refusal without an aim, the walk off the EDT on a named lane,
 * and a publish only if the project that was read is still the aimed one
 * (a result belongs to the workspace that produced it). A clean run
 * publishes an empty batch, which clears the previous run's rows.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.editor.docs.CheckMarkdownLinksAction")
@ActionRegistration(displayName = "#CTL_CheckMarkdownLinksAction")
@ActionReference(path = "Menu/Tools", position = 93)
@Messages({
    "CTL_CheckMarkdownLinksAction=Check Markdown Links…",
    "CheckMarkdownLinksAction_aimFirst=Aim the studio at a project first (open a folder or project).",
    "CheckMarkdownLinksAction_none=Markdown links: no Markdown files under {0}",
    "CheckMarkdownLinksAction_aimMoved=Markdown links: the project changed while checking — nothing published. Run again.",
    "# {0} - files read, {1} - the same number, for the plural; {2} - links checked, {3} - the same number",
    "CheckMarkdownLinksAction_read={1,choice,1#{0} file|1<{0} files}, {3,choice,0#{2} links|1#{2} link|1<{2} links}",
    "# {0} - the count, {1} - the same number, for the plural",
    "CheckMarkdownLinksAction_missingFiles={1,choice,1#{0} goes nowhere|1<{0} go nowhere}",
    "CheckMarkdownLinksAction_missingHeadings={1,choice,1#{0} names a missing heading|1<{0} name a missing heading}",
    "CheckMarkdownLinksAction_outside={1,choice,1#{0} leaves the project|1<{0} leave the project}",
    "CheckMarkdownLinksAction_clean=every link lands",
    "CheckMarkdownLinksAction_partial=the walk stopped at {0} files",
    "# {0} - the joined fragments",
    "CheckMarkdownLinksAction_summary=Markdown links: {0}",
    "CheckMarkdownLinksAction_join=, ",
    "# {0} - the link as written",
    "CheckMarkdownLinksAction_findingMissingFile={0} goes nowhere: no such file",
    "CheckMarkdownLinksAction_findingMissingHeading={0}: the file has no such heading",
    "CheckMarkdownLinksAction_findingOutside={0} points outside the project"
})
public final class CheckMarkdownLinksAction implements ActionListener {

    /** The bus tool name every consumer labels these findings with. */
    static final String TOOL = "markdown";

    /** The most Markdown files one run reads. */
    static final int MAX_FILES = 2000;

    /** The largest Markdown file read (a bigger one is not prose). */
    static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final RequestProcessor RP = new RequestProcessor("nmox-markdown-links", 1);

    @Override
    public void actionPerformed(ActionEvent e) {
        ProjectAim aim = ProjectAim.find();
        File project = aim == null ? null : aim.projectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.CheckMarkdownLinksAction_aimFirst()));
            return;
        }
        RP.post(() -> run(project.toPath(), CheckMarkdownLinksAction::aimedDir));
    }

    static File aimedDir() {
        ProjectAim aim = ProjectAim.find();
        return aim == null ? null : aim.projectDir();
    }

    /** The whole run, off the EDT; only the status sentence hops back. */
    static void run(Path root, java.util.function.Supplier<File> aimNow) {
        List<File> found = BoundedWalk.collect(root.toFile(),
                n -> MarkdownLinks.isMarkdown(Path.of(n)), MAX_FILES);
        if (found.isEmpty()) {
            status(Bundle.CheckMarkdownLinksAction_none(String.valueOf(root.getFileName())));
            return;
        }
        List<Path> docs = new ArrayList<>();
        for (File f : found) {
            docs.add(f.toPath());
        }
        MarkdownLinks.Report report = MarkdownLinks.check(root, docs,
                p -> MarkdownLinks.readBounded(p, MAX_BYTES));
        File now = aimNow.get();
        if (now == null || !root.toAbsolutePath().normalize().equals(now.toPath().toAbsolutePath().normalize())) {
            status(Bundle.CheckMarkdownLinksAction_aimMoved());
            return;
        }
        DiagnosticsBus.publish(TOOL, problems(report));
        status(sentence(report, BoundedWalk.complete(found, MAX_FILES)));
    }

    /** Every finding as a bus problem, in the user's language. */
    static List<DiagnosticsBus.Problem> problems(MarkdownLinks.Report report) {
        List<DiagnosticsBus.Problem> out = new ArrayList<>();
        for (MarkdownLinks.Finding f : report.findings()) {
            out.add(new DiagnosticsBus.Problem(f.file().toFile(), f.line(), message(f), f.error()));
        }
        return out;
    }

    static String message(MarkdownLinks.Finding f) {
        return switch (f.kind()) {
            case MISSING_FILE -> Bundle.CheckMarkdownLinksAction_findingMissingFile(f.target());
            case MISSING_HEADING -> Bundle.CheckMarkdownLinksAction_findingMissingHeading(f.target());
            case OUTSIDE_PROJECT -> Bundle.CheckMarkdownLinksAction_findingOutside(f.target());
        };
    }

    /** The status sentence: what was read, what is wrong, and whether the walk was whole. */
    static String sentence(MarkdownLinks.Report report, boolean complete) {
        List<String> parts = new ArrayList<>();
        parts.add(Bundle.CheckMarkdownLinksAction_read(String.valueOf(report.files()), report.files(),
                String.valueOf(report.links()), report.links()));
        int[] counts = new int[MarkdownLinks.Kind.values().length];
        for (MarkdownLinks.Finding f : report.findings()) {
            counts[f.kind().ordinal()]++;
        }
        int missing = counts[MarkdownLinks.Kind.MISSING_FILE.ordinal()];
        int headings = counts[MarkdownLinks.Kind.MISSING_HEADING.ordinal()];
        int outside = counts[MarkdownLinks.Kind.OUTSIDE_PROJECT.ordinal()];
        if (missing > 0) {
            parts.add(Bundle.CheckMarkdownLinksAction_missingFiles(String.valueOf(missing), missing));
        }
        if (headings > 0) {
            parts.add(Bundle.CheckMarkdownLinksAction_missingHeadings(String.valueOf(headings), headings));
        }
        if (outside > 0) {
            parts.add(Bundle.CheckMarkdownLinksAction_outside(String.valueOf(outside), outside));
        }
        if (report.findings().isEmpty()) {
            parts.add(Bundle.CheckMarkdownLinksAction_clean());
        }
        if (!complete) {
            parts.add(Bundle.CheckMarkdownLinksAction_partial(String.valueOf(MAX_FILES)));
        }
        return Bundle.CheckMarkdownLinksAction_summary(String.join(Bundle.CheckMarkdownLinksAction_join(), parts));
    }

    private static void status(String text) {
        java.awt.EventQueue.invokeLater(
                () -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(text)));
    }
}
