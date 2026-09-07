package org.nmox.studio.editor.lsp;

import org.nmox.studio.core.util.PlainText;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.nmox.studio.editor.lsp.LanguageServerCatalog.Server;
import org.nmox.studio.editor.lsp.LanguageServerInstaller.Result;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.openide.util.NbBundle;

/**
 * The install interface behind Tools ▸ Language Servers…: every language
 * NMOX can light up, whether its server is present, and a one-click
 * Install that runs the ecosystem's own command with the bar running
 * across while it downloads. Installs run one at a time (the bar tracks
 * the batch); output streams to the Output window; Cancel kills the run.
 */
public final class LanguageServersPanel extends JPanel {

    private static final Color OK = new Color(120, 200, 130);
    private static final Color MISSING = new Color(220, 140, 90);

    private final JProgressBar bar = new JProgressBar();
    private static final String BLANK = " ";
    private final JLabel status = new JLabel(BLANK);
    private final JButton cancelBtn = new JButton(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_cancel"));
    private final List<Row> rows = new ArrayList<>();
    private final Deque<Row> queue = new ArrayDeque<>();
    private CommandExecutor.Handle current;
    private int batchTotal;
    private int batchDone;

    public LanguageServersPanel() {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setPreferredSize(new Dimension(560, 520));

        JLabel header = new JLabel(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_header"));
        add(header, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (Server s : LanguageServerCatalog.all()) {
            Row r = new Row(s);
            rows.add(r);
            list.add(r);
        }
        list.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        JButton all = new JButton(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_installAll"));
        all.addActionListener(e -> installAllMissing());
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(e -> cancel());
        bar.setVisible(false);

        JPanel footer = new JPanel(new BorderLayout(8, 4));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(all);
        buttons.add(cancelBtn);
        footer.add(buttons, BorderLayout.WEST);
        JPanel progress = new JPanel(new BorderLayout(6, 0));
        progress.add(bar, BorderLayout.CENTER);
        progress.add(status, BorderLayout.SOUTH);
        footer.add(progress, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private void installAllMissing() {
        if (current != null) {
            return;
        }
        queue.clear();
        for (Row r : rows) {
            if (!LanguageServerCatalog.isInstalled(r.server.binary()) && r.server.autoInstallable()) {
                queue.add(r);
            }
        }
        if (queue.isEmpty()) {
            status.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_allInstalled"));
            return;
        }
        batchTotal = queue.size();
        batchDone = 0;
        startNext();
    }

    private void startNext() {
        Row row = queue.poll();
        if (row == null) {
            bar.setVisible(false);
            cancelBtn.setEnabled(false);
            status.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_done"));
            return;
        }
        install(row, true);
    }

    private void install(Row row, boolean batch) {
        if (current != null) {
            return; // one at a time
        }
        row.button.setEnabled(false);
        row.statusLabel.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_pending"));
        bar.setVisible(true);
        cancelBtn.setEnabled(true);
        if (batch && batchTotal > 0) {
            bar.setIndeterminate(false);
            bar.setMinimum(0);
            bar.setMaximum(batchTotal);
            bar.setValue(batchDone);
            bar.setStringPainted(true);
            bar.setString(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_batchProgress", row.server.language(), String.valueOf(batchDone + 1), String.valueOf(batchTotal)));
        } else {
            bar.setIndeterminate(true);
            bar.setStringPainted(true);
            bar.setString(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_installing", row.server.language()));
        }
        status.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_running", String.join(" ", row.server.command())));

        current = LanguageServerInstaller.install(row.server, new LanguageServerInstaller.Listener() {
            @Override
            public void onStarted(Server server) {
            }

            @Override
            public void onFinished(Server server, Result result, int exitCode) {
                SwingUtilities.invokeLater(() -> finished(row, result, batch));
            }
        });
        // synchronous-failure cases (not auto / missing toolchain) already
        // delivered onFinished and returned null; nothing is running
    }

    private void finished(Row row, Result result, boolean batch) {
        current = null;
        switch (result) {
            case INSTALLED -> {
                row.refresh();
                status.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_installed", row.server.language()));
            }
            case FAILED -> {
                row.statusLabel.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_missingMark"));
                row.statusLabel.setForeground(MISSING);
                row.button.setEnabled(true);
                status.setText(PlainText.plain(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_installFailed", row.server.language())));
            }
            case NEEDS_TOOLCHAIN -> {
                row.button.setEnabled(false);
                row.button.setText(PlainText.plain(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_installerNotFound", row.server.installer())));
                status.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_installToolchainFirst", row.server.installer(), row.server.language()));
            }
            case NEEDS_PROJECT -> {
                row.button.setEnabled(true);
                status.setText(PlainText.plain(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_needsProject", row.server.language())));
            }
            default -> row.button.setEnabled(true);
        }
        if (batch) {
            batchDone++;
            startNext();
        } else {
            bar.setVisible(false);
            cancelBtn.setEnabled(false);
        }
    }

    private void cancel() {
        queue.clear();
        if (current != null) {
            current.kill();
            current = null;
        }
        bar.setVisible(false);
        cancelBtn.setEnabled(false);
        status.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_cancelled"));
        for (Row r : rows) {
            r.refresh();
        }
    }

    /** One language's row: status, name, and its Install action. */
    private final class Row extends JPanel {

        private final Server server;
        private final JLabel statusLabel = new JLabel();
        private final JButton button = new JButton();

        Row(Server server) {
            super(new BorderLayout(8, 0));
            this.server = server;
            setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            statusLabel.setPreferredSize(new Dimension(18, 18));
            add(statusLabel, BorderLayout.WEST);

            JLabel name = new JLabel("<html><b>" + server.language() + "</b>&nbsp;&nbsp;"
                    + "<span style='color:#888'><code>" + server.binary() + "</code></span></html>");
            add(name, BorderLayout.CENTER);

            button.addActionListener(e -> install(this, false));
            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            east.add(button);
            add(east, BorderLayout.EAST);

            refresh();
        }

        void refresh() {
            boolean ok = LanguageServerCatalog.isInstalled(server.binary());
            if (ok) {
                statusLabel.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_okMark"));
                statusLabel.setForeground(OK);
                button.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_installedButton"));
                button.setEnabled(false);
            } else {
                statusLabel.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_missingMark"));
                statusLabel.setForeground(MISSING);
                if (server.autoInstallable()) {
                    button.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_install"));
                    button.setToolTipText(PlainText.plain(String.join(" ", server.command())));
                    button.setEnabled(true);
                } else {
                    button.setText(NbBundle.getMessage(LanguageServersPanel.class, "LanguageServersPanel_manual"));
                    button.setToolTipText(PlainText.plain(server.install()));
                    button.setEnabled(false);
                }
            }
        }
    }
}
