package org.nmox.studio.tools.test.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import javax.swing.*;
import javax.swing.tree.*;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.tools.test.*;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * Test results viewer window.
 */
@ConvertAsProperties(
    dtd = "-//org.nmox.studio.tools.test.ui//TestResults//EN",
    autostore = false
)
@TopComponent.Description(
    preferredID = "TestResultsTopComponent",
    persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "output", openAtStartup = false)
@ActionID(category = "Window", id = "org.nmox.studio.tools.test.ui.TestResultsTopComponent")
@ActionReference(path = "Menu/Window", position = 410)
@TopComponent.OpenActionRegistration(
    displayName = "#CTL_TestResultsAction",
    preferredID = "TestResultsTopComponent"
)
@Messages({
    "CTL_TestResultsAction=Test Results",
    "CTL_TestResultsTopComponent=Test Results",
    "HINT_TestResultsTopComponent=Shows test execution results and coverage"
})
public final class TestResultsTopComponent extends TopComponent {
    
    private JTree testTree;
    private DefaultTreeModel treeModel;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel statsLabel;
    private JTextArea detailsArea;
    private JSplitPane splitPane;
    private JToolBar toolbar;
    private JButton runButton;
    private JButton stopButton;
    private JButton rerunFailedButton;
    private JToggleButton coverageButton;
    
    private TestResult currentResult;
    private static TestResultsTopComponent instance;
    
    public TestResultsTopComponent() {
        initComponents();
        setName(NbBundle.getMessage(TestResultsTopComponent.class, "CTL_TestResultsTopComponent"));
        setToolTipText(NbBundle.getMessage(TestResultsTopComponent.class, "HINT_TestResultsTopComponent"));
    }
    
    public static TestResultsTopComponent getInstance() {
        if (instance == null) {
            instance = new TestResultsTopComponent();
        }
        return instance;
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Create toolbar
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        runButton = new JButton(new ImageIcon(getClass().getResource("/icons/run.png")));
        runButton.setToolTipText("Run All Tests");
        runButton.addActionListener(e -> runAllTests());
        toolbar.add(runButton);
        
        stopButton = new JButton(new ImageIcon(getClass().getResource("/icons/stop.png")));
        stopButton.setToolTipText("Stop Tests");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopTests());
        toolbar.add(stopButton);
        
        rerunFailedButton = new JButton(new ImageIcon(getClass().getResource("/icons/rerun.png")));
        rerunFailedButton.setToolTipText("Rerun Failed Tests");
        rerunFailedButton.setEnabled(false);
        rerunFailedButton.addActionListener(e -> rerunFailedTests());
        toolbar.add(rerunFailedButton);
        
        toolbar.addSeparator();
        
        coverageButton = new JToggleButton(new ImageIcon(getClass().getResource("/icons/coverage.png")));
        coverageButton.setToolTipText("Show Coverage");
        coverageButton.addActionListener(e -> toggleCoverage());
        toolbar.add(coverageButton);
        
        toolbar.add(Box.createHorizontalGlue());
        
        statsLabel = new JLabel("No tests run");
        toolbar.add(statsLabel);
        
        add(toolbar, BorderLayout.NORTH);
        
        // Create test tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Test Results");
        treeModel = new DefaultTreeModel(root);
        testTree = new JTree(treeModel);
        testTree.setCellRenderer(new TestTreeCellRenderer());
        testTree.setRootVisible(false);
        testTree.setShowsRootHandles(true);
        
        testTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = testTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        showTestDetails(path.getLastPathComponent());
                    }
                }
            }
        });
        
        JScrollPane treeScroll = new JScrollPane(testTree);
        
        // Create details area
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        
        // Create split pane
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, detailsScroll);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.7);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Progress bar at bottom
        JPanel bottomPanel = new JPanel(new BorderLayout());
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        bottomPanel.add(progressBar, BorderLayout.CENTER);
        
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    public void displayTestResult(TestResult result) {
        this.currentResult = result;
        
        SwingUtilities.invokeLater(() -> {
            // Update tree
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
            root.removeAllChildren();
            
            for (TestResult.TestSuite suite : result.getTestSuites()) {
                DefaultMutableTreeNode suiteNode = new DefaultMutableTreeNode(suite);
                
                for (TestCase testCase : suite.getTestCases()) {
                    DefaultMutableTreeNode testNode = new DefaultMutableTreeNode(testCase);
                    suiteNode.add(testNode);
                }
                
                root.add(suiteNode);
            }
            
            treeModel.reload();
            expandAll();
            
            // Update statistics
            updateStatistics(result);
            
            // Update progress
            progressBar.setMaximum(result.getTotalTests());
            progressBar.setValue(result.getPassedTests() + result.getFailedTests());
            
            // Update status
            if (result.isSuccess()) {
                statusLabel.setText("All tests passed");
                statusLabel.setForeground(new Color(0, 128, 0));
            } else {
                statusLabel.setText(result.getFailedTests() + " tests failed");
                statusLabel.setForeground(Color.RED);
            }
            
            // Enable rerun button if there are failures
            rerunFailedButton.setEnabled(result.getFailedTests() > 0);
        });
    }
    
    private void updateStatistics(TestResult result) {
        StringBuilder stats = new StringBuilder();
        stats.append("Tests: ");
        
        if (result.getPassedTests() > 0) {
            stats.append("<font color='green'>✓ ").append(result.getPassedTests()).append("</font> ");
        }
        
        if (result.getFailedTests() > 0) {
            stats.append("<font color='red'>✗ ").append(result.getFailedTests()).append("</font> ");
        }
        
        if (result.getSkippedTests() > 0) {
            stats.append("<font color='gray'>○ ").append(result.getSkippedTests()).append("</font> ");
        }
        
        stats.append(" | Duration: ").append(formatDuration(result.getDuration()));
        
        if (result.getCoverage() != null) {
            DecimalFormat df = new DecimalFormat("#.#");
            stats.append(" | Coverage: ").append(df.format(result.getCoverage().getLineCoverage())).append("%");
        }
        
        statsLabel.setText("<html>" + stats.toString() + "</html>");
    }
    
    private void showTestDetails(Object node) {
        if (node instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
            
            if (userObject instanceof TestCase) {
                TestCase test = (TestCase) userObject;
                StringBuilder details = new StringBuilder();
                
                details.append("Test: ").append(test.getFullName()).append("\n");
                details.append("Status: ").append(test.getStatus().getDisplayName()).append("\n");
                details.append("Duration: ").append(test.getDuration()).append("ms\n");
                
                if (test.getFile() != null) {
                    details.append("File: ").append(test.getFile());
                    if (test.getLine() > 0) {
                        details.append(":").append(test.getLine());
                    }
                    details.append("\n");
                }
                
                if (test.getErrorMessage() != null) {
                    details.append("\nError:\n").append(test.getErrorMessage()).append("\n");
                }
                
                if (test.getErrorStack() != null) {
                    details.append("\nStack Trace:\n").append(test.getErrorStack());
                }
                
                detailsArea.setText(details.toString());
                detailsArea.setCaretPosition(0);
            }
        }
    }
    
    private void expandAll() {
        for (int i = 0; i < testTree.getRowCount(); i++) {
            testTree.expandRow(i);
        }
    }
    
    private void runAllTests() {
        // Implementation would trigger test execution
        runButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusLabel.setText("Running tests...");
        progressBar.setIndeterminate(true);
    }
    
    private void stopTests() {
        // Implementation would stop test execution
        runButton.setEnabled(true);
        stopButton.setEnabled(false);
        statusLabel.setText("Tests stopped");
        progressBar.setIndeterminate(false);
    }
    
    private void rerunFailedTests() {
        // Implementation would rerun only failed tests
    }
    
    private void toggleCoverage() {
        // Implementation would toggle coverage display
    }
    
    private String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    @Override
    public void componentOpened() {
        // Component opened
    }
    
    @Override
    public void componentClosed() {
        // Component closed
    }
    
    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }
    
    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }
    
    /**
     * Custom tree cell renderer for test results.
     */
    private class TestTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Icon passedIcon = new ImageIcon(getClass().getResource("/icons/test-pass.png"));
        private final Icon failedIcon = new ImageIcon(getClass().getResource("/icons/test-fail.png"));
        private final Icon skippedIcon = new ImageIcon(getClass().getResource("/icons/test-skip.png"));
        private final Icon suiteIcon = new ImageIcon(getClass().getResource("/icons/test-suite.png"));
        
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            
            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                
                if (userObject instanceof TestCase) {
                    TestCase test = (TestCase) userObject;
                    setText(test.getName());
                    
                    switch (test.getStatus()) {
                        case PASSED:
                            setIcon(passedIcon);
                            setForeground(new Color(0, 128, 0));
                            break;
                        case FAILED:
                            setIcon(failedIcon);
                            setForeground(Color.RED);
                            break;
                        case SKIPPED:
                        case PENDING:
                            setIcon(skippedIcon);
                            setForeground(Color.GRAY);
                            break;
                    }
                } else if (userObject instanceof TestResult.TestSuite) {
                    TestResult.TestSuite suite = (TestResult.TestSuite) userObject;
                    setText(suite.getName() + " (" + suite.getTestCases().size() + " tests)");
                    setIcon(suiteIcon);
                    
                    if (suite.getFailedCount() > 0) {
                        setForeground(Color.RED);
                    } else if (suite.getPassedCount() == suite.getTestCases().size()) {
                        setForeground(new Color(0, 128, 0));
                    }
                }
            }
            
            return this;
        }
    }
}