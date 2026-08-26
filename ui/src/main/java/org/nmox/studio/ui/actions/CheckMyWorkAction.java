package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.time.Duration;
import java.util.List;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.process.ProcessSupport;
import org.nmox.studio.rack.projectstudio.Checkpoints;
import org.nmox.studio.rack.projectstudio.LearningCatalog;
import org.nmox.studio.rack.projectstudio.LearningSpace;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Check My Work (v2.39.1, the elevation arc: a tutorial that checks
 * beats a tutorial that describes): aimed at a learning space whose
 * catalog entry declares checkpoints, one gesture runs them — file
 * claims verified pure-Java, command claims through the space's own
 * toolchain, each answer ✓ or ✗ with the space's hint. Kit-action
 * idiom: always enabled, honest refusals (not a space; a space whose
 * catalog entry has no checkpoints yet). SPAWN CLASSIFICATION
 * (v1.224.0 ledger): command checkpoints execute catalog-validated
 * argv (bare tool names, no shell — Checkpoints.parse enforces the
 * device-file law) inside the pre-trusted ~/.nmox/learn home, at the
 * learner's explicit button press, bounded by runBounded's leash.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.CheckMyWorkAction")
@ActionRegistration(displayName = "#CTL_CheckMyWorkAction")
@ActionReference(path = "Menu/File", position = 139)
@Messages("CTL_CheckMyWorkAction=Check My Work")
public final class CheckMyWorkAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File dir = RackService.getDefault().getRack().getProjectDir();
        if (dir == null || !LearningSpace.isLearningSpace(dir)) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Check My Work verifies a learning space's exercises — aim the"
                    + " studio at one first (File ▸ New Learning Space…)."));
            return;
        }
        String slug = LearningSpace.info(dir).slug();
        LearningCatalog.Space space = LearningCatalog.find(slug);
        List<Checkpoints.Checkpoint> checks =
                space == null ? List.of() : space.checkpoints();
        if (checks.isEmpty()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "This space has no checkpoints yet — the flagship spaces"
                    + " (Your First Web Page, Go, Rust, Playwright) check work"
                    + " today, and any catalog entry can declare its own."));
            return;
        }
        // file checks read disk, command checks spawn — off the EDT
        ManageLearningSpacesAction.SPACES_RP.post(() -> {
            Checkpoints.Runner runner = (d, argv) -> {
                ProcessSupport.BoundedResult r;
                try {
                    r = ProcessSupport.runBounded(argv, d, Duration.ofSeconds(120));
                } catch (Exception ex) {
                    return new Checkpoints.Runner.Run(-1,
                            String.valueOf(ex.getMessage()));
                }
                return new Checkpoints.Runner.Run(r.exitCode(),
                        r.stdout() + "\n" + r.stderr());
            };
            StringBuilder report = new StringBuilder();
            int passed = 0;
            for (Checkpoints.Checkpoint c : checks) {
                Checkpoints.Result r = Checkpoints.run(dir, c, runner);
                report.append(r.passed() ? "  ✓ " : "  ✗ ").append(r.label()).append('\n');
                if (!r.passed() && !r.detail().isBlank()) {
                    report.append("      ").append(r.detail()).append('\n');
                }
                if (r.passed()) {
                    passed++;
                }
            }
            String head = passed == checks.size()
                    ? "All " + checks.size() + " checks pass — nicely done.\n\n"
                    : passed + " of " + checks.size() + " checks pass.\n\n";
            SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message(head + report,
                            NotifyDescriptor.INFORMATION_MESSAGE)));
        });
    }
}
