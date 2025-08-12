package org.nmox.studio.tools.debug.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.tools.debug.*;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * Debugger window with breakpoints, call stack, variables, and watches.
 */
@ConvertAsProperties(
    dtd = "-//org.nmox.studio.tools.debug.ui//Debugger//EN",
    autostore = false
)
@TopComponent.Description(
    preferredID = "DebuggerTopComponent",
    persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "explorer", openAtStartup = false)
@ActionID(category = "Window", id = "org.nmox.studio.tools.debug.ui.DebuggerTopComponent")
@ActionReference(path = "Menu/Window", position = 420)
@TopComponent.OpenActionRegistration(
    displayName = "#CTL_DebuggerAction",
    preferredID = "DebuggerTopComponent"
)
@Messages({
    "CTL_DebuggerAction=Debugger",
    "CTL_DebuggerTopComponent=Debugger",
    "HINT_DebuggerTopComponent=JavaScript debugger with Chrome DevTools Protocol"
})
public final class DebuggerTopComponent extends TopComponent implements DebugSession.DebugEventListener {
    
    private JTabbedPane tabbedPane;
    private JTable breakpointsTable;
    private DefaultTableModel breakpointsModel;
    private JTree callStackTree;
    private DefaultTreeModel callStackModel;
    private JTree variablesTree;
    private DefaultTreeModel variablesModel;
    private JTable watchesTable;
    private DefaultTableModel watchesModel;
    private JTextArea consoleArea;
    private JToolBar debugToolbar;
    private JButton continueButton;
    private JButton pauseButton;
    private JButton stepOverButton;
    private JButton stepIntoButton;
    private JButton stepOutButton;
    private JButton stopButton;
    private JLabel statusLabel;
    
    private DebugSession currentSession;
    private static DebuggerTopComponent instance;
    
    public DebuggerTopComponent() {
        initComponents();
        setName(NbBundle.getMessage(DebuggerTopComponent.class, "CTL_DebuggerTopComponent"));
        setToolTipText(NbBundle.getMessage(DebuggerTopComponent.class, "HINT_DebuggerTopComponent"));
    }
    
    public static DebuggerTopComponent getInstance() {
        if (instance == null) {
            instance = new DebuggerTopComponent();
        }
        return instance;
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Create debug toolbar
        debugToolbar = new JToolBar();
        debugToolbar.setFloatable(false);
        
        continueButton = new JButton("▶");
        continueButton.setToolTipText("Continue (F5)");
        continueButton.setEnabled(false);
        continueButton.addActionListener(e -> continueExecution());
        debugToolbar.add(continueButton);
        
        pauseButton = new JButton("⏸");
        pauseButton.setToolTipText("Pause");
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> pauseExecution());
        debugToolbar.add(pauseButton);
        
        stepOverButton = new JButton("⤵");
        stepOverButton.setToolTipText("Step Over (F10)");
        stepOverButton.setEnabled(false);
        stepOverButton.addActionListener(e -> stepOver());
        debugToolbar.add(stepOverButton);
        
        stepIntoButton = new JButton("⬇");
        stepIntoButton.setToolTipText("Step Into (F11)");
        stepIntoButton.setEnabled(false);
        stepIntoButton.addActionListener(e -> stepInto());
        debugToolbar.add(stepIntoButton);
        
        stepOutButton = new JButton("⬆");
        stepOutButton.setToolTipText("Step Out (Shift+F11)");
        stepOutButton.setEnabled(false);
        stepOutButton.addActionListener(e -> stepOut());
        debugToolbar.add(stepOutButton);
        
        debugToolbar.addSeparator();
        
        stopButton = new JButton("⏹");
        stopButton.setToolTipText("Stop Debugging");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopDebugging());
        debugToolbar.add(stopButton);
        
        debugToolbar.add(Box.createHorizontalGlue());
        
        statusLabel = new JLabel("Not debugging");
        debugToolbar.add(statusLabel);
        
        add(debugToolbar, BorderLayout.NORTH);
        
        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        
        // Breakpoints tab
        breakpointsModel = new DefaultTableModel(
            new String[]{"Enabled", "File", "Line", "Condition", "Hit Count"}, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 3;
            }
        };
        breakpointsTable = new JTable(breakpointsModel);
        breakpointsTable.getColumnModel().getColumn(0).setMaxWidth(60);
        breakpointsTable.getColumnModel().getColumn(2).setMaxWidth(60);
        breakpointsTable.getColumnModel().getColumn(4).setMaxWidth(80);
        
        JPanel breakpointsPanel = new JPanel(new BorderLayout());
        breakpointsPanel.add(new JScrollPane(breakpointsTable), BorderLayout.CENTER);
        
        JPanel breakpointButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBreakpointBtn = new JButton("Add");
        addBreakpointBtn.addActionListener(e -> addBreakpoint());
        breakpointButtons.add(addBreakpointBtn);
        
        JButton removeBreakpointBtn = new JButton("Remove");
        removeBreakpointBtn.addActionListener(e -> removeSelectedBreakpoint());
        breakpointButtons.add(removeBreakpointBtn);
        
        JButton removeAllBtn = new JButton("Remove All");
        removeAllBtn.addActionListener(e -> removeAllBreakpoints());
        breakpointButtons.add(removeAllBtn);
        
        breakpointsPanel.add(breakpointButtons, BorderLayout.SOUTH);
        tabbedPane.addTab("Breakpoints", breakpointsPanel);
        
        // Call Stack tab
        DefaultMutableTreeNode callStackRoot = new DefaultMutableTreeNode("Call Stack");
        callStackModel = new DefaultTreeModel(callStackRoot);
        callStackTree = new JTree(callStackModel);
        callStackTree.setRootVisible(false);
        
        callStackTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToStackFrame();
                }
            }
        });
        
        tabbedPane.addTab("Call Stack", new JScrollPane(callStackTree));
        
        // Variables tab
        DefaultMutableTreeNode variablesRoot = new DefaultMutableTreeNode("Variables");
        variablesModel = new DefaultTreeModel(variablesRoot);
        variablesTree = new JTree(variablesModel);
        variablesTree.setRootVisible(false);
        tabbedPane.addTab("Variables", new JScrollPane(variablesTree));
        
        // Watch tab
        watchesModel = new DefaultTableModel(
            new String[]{"Expression", "Value", "Type"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        watchesTable = new JTable(watchesModel);
        
        JPanel watchPanel = new JPanel(new BorderLayout());
        watchPanel.add(new JScrollPane(watchesTable), BorderLayout.CENTER);
        
        JPanel watchButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addWatchBtn = new JButton("Add Watch");
        addWatchBtn.addActionListener(e -> addWatch());
        watchButtons.add(addWatchBtn);
        
        JButton removeWatchBtn = new JButton("Remove");
        removeWatchBtn.addActionListener(e -> removeSelectedWatch());
        watchButtons.add(removeWatchBtn);
        
        watchPanel.add(watchButtons, BorderLayout.SOUTH);
        tabbedPane.addTab("Watch", watchPanel);
        
        // Console tab
        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consoleArea.setBackground(new Color(39, 40, 34));
        consoleArea.setForeground(new Color(248, 248, 242));
        
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.add(new JScrollPane(consoleArea), BorderLayout.CENTER);
        
        JPanel consoleButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clearConsoleBtn = new JButton("Clear");
        clearConsoleBtn.addActionListener(e -> consoleArea.setText(""));
        consoleButtons.add(clearConsoleBtn);
        
        consolePanel.add(consoleButtons, BorderLayout.SOUTH);
        tabbedPane.addTab("Console", consolePanel);
        
        add(tabbedPane, BorderLayout.CENTER);
    }
    
    public void setDebugSession(DebugSession session) {
        if (currentSession != null) {
            currentSession.removeListener(this);
        }
        
        currentSession = session;
        
        if (session != null) {
            session.addListener(this);
            updateDebugState(session.getState());
            updateCallStack();
            updateVariables();
            updateBreakpoints();
        }
    }
    
    private void updateDebugState(DebugSession.DebugState state) {
        SwingUtilities.invokeLater(() -> {
            boolean isPaused = state == DebugSession.DebugState.PAUSED;
            boolean isRunning = state == DebugSession.DebugState.RUNNING;
            boolean isConnected = state != DebugSession.DebugState.DISCONNECTED 
                && state != DebugSession.DebugState.TERMINATED;
            
            continueButton.setEnabled(isPaused);
            pauseButton.setEnabled(isRunning);
            stepOverButton.setEnabled(isPaused);
            stepIntoButton.setEnabled(isPaused);
            stepOutButton.setEnabled(isPaused);
            stopButton.setEnabled(isConnected);
            
            statusLabel.setText(state.toString());
        });
    }
    
    private void updateCallStack() {
        if (currentSession == null) return;
        
        SwingUtilities.invokeLater(() -> {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) callStackModel.getRoot();
            root.removeAllChildren();
            
            for (DebugSession.StackFrame frame : currentSession.getCallStack()) {
                String label = frame.getName() + " - " + frame.getSource() + ":" + frame.getLine();
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(frame);
                node.setUserObject(label);
                root.add(node);
            }
            
            callStackModel.reload();
        });
    }
    
    private void updateVariables() {
        if (currentSession == null) return;
        
        SwingUtilities.invokeLater(() -> {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) variablesModel.getRoot();
            root.removeAllChildren();
            
            DefaultMutableTreeNode localsNode = new DefaultMutableTreeNode("Local");
            DefaultMutableTreeNode globalsNode = new DefaultMutableTreeNode("Global");
            
            for (Map.Entry<String, DebugSession.Variable> entry : currentSession.getVariables().entrySet()) {
                DebugSession.Variable var = entry.getValue();
                String label = var.getName() + ": " + var.getValue() + " (" + var.getType() + ")";
                DefaultMutableTreeNode varNode = new DefaultMutableTreeNode(label);
                
                // Add properties if available
                if (var.getProperties() != null && !var.getProperties().isEmpty()) {
                    for (DebugSession.Variable prop : var.getProperties()) {
                        String propLabel = prop.getName() + ": " + prop.getValue();
                        varNode.add(new DefaultMutableTreeNode(propLabel));
                    }
                }
                
                localsNode.add(varNode);
            }
            
            root.add(localsNode);
            root.add(globalsNode);
            variablesModel.reload();
        });
    }
    
    private void updateBreakpoints() {
        if (currentSession == null) return;
        
        SwingUtilities.invokeLater(() -> {
            breakpointsModel.setRowCount(0);
            
            for (DebugSession.Breakpoint bp : currentSession.getBreakpoints()) {
                breakpointsModel.addRow(new Object[]{
                    bp.isEnabled(),
                    bp.getFile(),
                    bp.getLine(),
                    bp.getCondition() != null ? bp.getCondition() : "",
                    "0"
                });
            }
        });
    }
    
    private void continueExecution() {
        if (currentSession != null) {
            currentSession.resume();
        }
    }
    
    private void pauseExecution() {
        if (currentSession != null) {
            currentSession.pause();
        }
    }
    
    private void stepOver() {
        if (currentSession != null) {
            currentSession.stepOver();
        }
    }
    
    private void stepInto() {
        if (currentSession != null) {
            currentSession.stepInto();
        }
    }
    
    private void stepOut() {
        if (currentSession != null) {
            currentSession.stepOut();
        }
    }
    
    private void stopDebugging() {
        if (currentSession != null) {
            currentSession.disconnect();
            currentSession = null;
            updateDebugState(DebugSession.DebugState.DISCONNECTED);
        }
    }
    
    private void addBreakpoint() {
        String file = JOptionPane.showInputDialog(this, "Enter file path:");
        if (file != null && !file.isEmpty()) {
            String lineStr = JOptionPane.showInputDialog(this, "Enter line number:");
            try {
                int line = Integer.parseInt(lineStr);
                if (currentSession != null) {
                    currentSession.setBreakpoint(file, line);
                    updateBreakpoints();
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid line number", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void removeSelectedBreakpoint() {
        int row = breakpointsTable.getSelectedRow();
        if (row >= 0 && currentSession != null) {
            // Get breakpoint ID from row
            // currentSession.removeBreakpoint(breakpointId);
            updateBreakpoints();
        }
    }
    
    private void removeAllBreakpoints() {
        if (currentSession != null) {
            for (DebugSession.Breakpoint bp : currentSession.getBreakpoints()) {
                currentSession.removeBreakpoint(bp.getId());
            }
            updateBreakpoints();
        }
    }
    
    private void addWatch() {
        String expression = JOptionPane.showInputDialog(this, "Enter watch expression:");
        if (expression != null && !expression.isEmpty() && currentSession != null) {
            currentSession.addWatch(expression);
            updateWatches();
        }
    }
    
    private void removeSelectedWatch() {
        int row = watchesTable.getSelectedRow();
        if (row >= 0 && currentSession != null) {
            String expression = (String) watchesModel.getValueAt(row, 0);
            currentSession.removeWatch(expression);
            updateWatches();
        }
    }
    
    private void updateWatches() {
        if (currentSession == null) return;
        
        SwingUtilities.invokeLater(() -> {
            watchesModel.setRowCount(0);
            
            for (DebugSession.WatchExpression watch : currentSession.getWatches()) {
                watchesModel.addRow(new Object[]{
                    watch.getExpression(),
                    watch.getValue() != null ? watch.getValue() : "<not available>",
                    watch.getType() != null ? watch.getType() : ""
                });
            }
        });
    }
    
    private void navigateToStackFrame() {
        // Implementation would navigate to the selected stack frame in the editor
    }
    
    // DebugEventListener implementation
    @Override
    public void onStateChanged(DebugSession.DebugState newState) {
        updateDebugState(newState);
        if (newState == DebugSession.DebugState.PAUSED) {
            updateCallStack();
            updateVariables();
            updateWatches();
        }
    }
    
    @Override
    public void onBreakpointHit(DebugSession.Breakpoint breakpoint) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append("Breakpoint hit: " + breakpoint.getFile() + ":" + breakpoint.getLine() + "\n");
            tabbedPane.setSelectedIndex(1); // Switch to call stack tab
        });
    }
    
    @Override
    public void onException(String message, String stack) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append("Exception: " + message + "\n");
            if (stack != null) {
                consoleArea.append(stack + "\n");
            }
        });
    }
    
    @Override
    public void onConsoleOutput(String message, String level) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append("[" + level.toUpperCase() + "] " + message + "\n");
        });
    }
    
    @Override
    public void onSourceMapped(String original, String mapped) {
        // Handle source map notifications
    }
    
    @Override
    public void componentOpened() {
        // Component opened
    }
    
    @Override
    public void componentClosed() {
        if (currentSession != null) {
            currentSession.disconnect();
        }
    }
    
    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }
    
    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }
}