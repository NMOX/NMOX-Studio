package org.nmox.studio.editor.docs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.core.util.HeavyDirs;
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
@ActionReference(path = "Menu/Tools", position = 91)
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
    "# {0} - how many Markdown files were too large to read, {1} - the same number, for the plural",
    "CheckMarkdownLinksAction_tooLarge={1,choice,1#{0} file over 2 MiB not read|1<{0} files over 2 MiB not read}",
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

    /** How deep the walk goes: far past any real documentation tree, short of a link cycle's worth. */
    static final int MAX_DEPTH = 32;

    /**
     * What the walk found: the Markdown files to read, how many were too
     * large to read, and whether it stopped at {@link #MAX_FILES}.
     */
    record Walk(List<Path> docs, int tooLarge, boolean capped) {
    }

    /**
     * Every Markdown file under {@code root} that is a regular file: links
     * are not followed (a link to a folder outside the project, or to a
     * device, is not the project's documentation), heavy and hidden folders
     * are not entered, and a file over {@link #MAX_BYTES} is counted rather
     * than read, so the sentence can say it was left out.
     */
    static Walk walk(Path root) {
        List<Path> docs = new ArrayList<>();
        int[] tooLarge = {0};
        boolean[] capped = {false};
        try {
            Files.walkFileTree(root, EnumSet.noneOf(java.nio.file.FileVisitOption.class), MAX_DEPTH,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                            String n = dir.getFileName() == null ? "" : dir.getFileName().toString();
                            return !dir.equals(root) && (HeavyDirs.NAMES.contains(n) || n.startsWith("."))
                                    ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                            if (!a.isRegularFile() || !MarkdownLinks.isMarkdown(f)) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (a.size() > MAX_BYTES) {
                                tooLarge[0]++;
                                return FileVisitResult.CONTINUE;
                            }
                            docs.add(f);
                            if (docs.size() >= MAX_FILES) {
                                capped[0] = true;
                                return FileVisitResult.TERMINATE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path f, IOException e) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            // the root itself vanished: what was found is what there is
        }
        return new Walk(docs, tooLarge[0], capped[0]);
    }

    /** The whole run, off the EDT; only the status sentence hops back. */
    static void run(Path root, java.util.function.Supplier<File> aimNow) {
        Walk walk = walk(root);
        MarkdownLinks.Report report = walk.docs().isEmpty() ? null
                : MarkdownLinks.check(root, walk.docs(), p -> MarkdownLinks.readBounded(p, MAX_BYTES));
        File now = aimNow.get();
        if (now == null || !root.toAbsolutePath().normalize().equals(now.toPath().toAbsolutePath().normalize())) {
            status(Bundle.CheckMarkdownLinksAction_aimMoved());
            return;
        }
        if (report == null) {
            // still a batch: it clears what the last run published
            DiagnosticsBus.publish(TOOL, List.of());
            status(Bundle.CheckMarkdownLinksAction_none(String.valueOf(root.getFileName())));
            return;
        }
        DiagnosticsBus.publish(TOOL, problems(report));
        status(sentence(report, !walk.capped(), walk.tooLarge()));
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
    static String sentence(MarkdownLinks.Report report, boolean complete, int tooLarge) {
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
        if (tooLarge > 0) {
            parts.add(Bundle.CheckMarkdownLinksAction_tooLarge(String.valueOf(tooLarge), tooLarge));
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
