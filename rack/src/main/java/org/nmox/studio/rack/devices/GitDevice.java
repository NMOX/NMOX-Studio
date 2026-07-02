package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.List;
import javax.swing.JOptionPane;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * TIMELINE Git Sequencer: source-control transport. The branch LCD and
 * DIRTY LED refresh after every operation; COMMIT prompts for a message
 * then stages everything and commits.
 */
public class GitDevice extends CommandDevice {

    private final LcdDisplay branchLcd;
    private final Led dirtyLed;

    public GitDevice() {
        super("git", "TIMELINE", "GIT SEQUENCER", new Color(222, 78, 54), 2);

        branchLcd = place(new LcdDisplay(150, 1), 44, 46);
        branchLcd.setText("…");
        dirtyLed = place(new Led("DIRTY", RackStyle.MUTATE), 200, 52);

        RackButton status = place(new RackButton("STATUS", RackStyle.QUERY), 240, 52);
        RackButton pull = place(new RackButton("PULL", RackStyle.MUTATE), 304, 52);
        RackButton commit = place(new RackButton("COMMIT", RackStyle.MUTATE), 368, 52);
        RackButton push = place(new RackButton("PUSH", RackStyle.MUTATE), 432, 52);

        status.addActionListener(e -> launch(List.of("git", "status", "--short")));
        pull.addActionListener(e -> launch(List.of("git", "pull")));
        push.addActionListener(e -> launch(List.of("git", "push")));
        commit.addActionListener(e -> {
            String msg = JOptionPane.showInputDialog(this, "Commit message:", "Commit",
                    JOptionPane.PLAIN_MESSAGE);
            if (msg != null && !msg.isBlank()) {
                exec(List.of("git", "add", "-A"), line -> {
                }, code -> {
                    if (code == 0) {
                        launch(List.of("git", "commit", "-m", msg));
                    } else {
                        onEdt(() -> statusLcd.setText("ADD FAILED [" + code + "]"));
                    }
                });
            }
        });
    }

    /** Git works in any repository, project manifest or not. */
    @Override
    protected boolean requiresProjectManifest() {
        return false;
    }

    @Override
    protected void onAttached() {
        refreshBranch();
    }

    @Override
    public void projectChanged(File dir) {
        refreshBranch();
    }

    @Override
    protected void onFinished(int exitCode) {
        refreshBranch();
    }

    private void refreshBranch() {
        // lightweight probes outside the main launch pipeline
        StringBuilder branch = new StringBuilder();
        CommandProbe.run(projectDir(), List.of("git", "rev-parse", "--abbrev-ref", "HEAD"),
                branch::append, code -> onEdt(() ->
                        branchLcd.setText(code == 0 ? branch.toString() : "NO REPO")));
        StringBuilder dirt = new StringBuilder();
        CommandProbe.run(projectDir(), List.of("git", "status", "--porcelain"),
                dirt::append, code -> onEdt(() ->
                        dirtyLed.setOn(code == 0 && dirt.length() > 0)));
    }

    @Override
    protected List<String> buildCommand() {
        return List.of("git", "status", "--short");
    }
}
