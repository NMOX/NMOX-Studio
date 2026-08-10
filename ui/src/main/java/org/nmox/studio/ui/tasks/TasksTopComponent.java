package org.nmox.studio.ui.tasks;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.PlainTables;
import org.nmox.studio.core.util.Popups;
import org.nmox.studio.core.util.SelfWriteTracker;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * The Task Board (v1.323.0): a per-project kanban on ⌥⌘1 — columns of
 * cards, dragged or keyed between them, persisted beside the project as
 * {@code .nmoxtasks.json} so a checked-in board is the team's and an
 * ignored one stays personal.
 *
 * <p>House laws carried, with their origins:
 * <ul>
 *   <li><b>Zero boot cost</b> (v1.38.0): the tab is default-open for
 *       discovery like its siblings, but builds nothing and reads
 *       nothing until first shown — all work hangs off
 *       {@code componentShowing}.</li>
 *   <li><b>Disk off the EDT</b> (v1.108.0): loads ride {@link #IO_RP}
 *       with a newest-wins generation, saves ride the same single lane;
 *       the EDT only ever repaints.</li>
 *   <li><b>Studio persistence laws</b> (v1.39.0): atomic writes,
 *       self-write discrimination, corrupt files kept as .bak — see
 *       {@link TasksIO}.</li>
 *   <li><b>External edits reload</b> (v1.35.0 family): the board
 *       re-reads on aim change and re-checks the file whenever the tab
 *       is shown or a mutation is about to stack on top of a foreign
 *       edit. V1 LIMIT, in writing: there is no live file WATCHER — an
 *       external edit while the tab is visible is picked up at the next
 *       show/mutation, not the same instant. The five older studios
 *       ride the rack's watcher; this window deliberately stays
 *       watcher-less until that seam moves to core.</li>
 *   <li><b>Plain rendering</b> (v1.311.0): card and column text comes
 *       from a file a cloned repo can carry, so every renderer routes
 *       through {@link PlainTables#plain} — a {@code <html><img>} title
 *       paints as characters, never fetches.</li>
 *   <li><b>Clicked-item-wins popups</b> (v1.270.0): the card context
 *       menu targets the card under the pointer via
 *       {@link Popups#selectOnTrigger}.</li>
 *   <li><b>Safe destructive defaults</b> (v1.98.0): deleting a card or
 *       a non-empty column asks first, Enter answering No.</li>
 * </ul>
 */
@TopComponent.Description(preferredID = "TasksTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 357)
@ActionID(category = "Window", id = "org.nmox.studio.ui.tasks.TasksTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 269),
    @ActionReference(path = "Shortcuts", name = "DA-1")
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_TasksAction",
        preferredID = "TasksTopComponent")
@Messages({
    "CTL_TasksAction=Tasks",
    "CTL_TasksTopComponent=Tasks"
})
public final class TasksTopComponent extends TopComponent {

    private static final Logger LOG = Logger.getLogger(TasksTopComponent.class.getName());

    /** One lane for every read and write — order is the correctness. */
    private static final RequestProcessor IO_RP =
            new RequestProcessor("nmox-tasks-io", 1);

    private static final DataFlavor CARD_FLAVOR = new DataFlavor(
            String.class, "NMOX task card id");

    private final SelfWriteTracker tracker = new SelfWriteTracker();
    private final JPanel columnsPanel = new JPanel();
    private final JLabel boardLabel = new JLabel(" ");

    private TaskBoard board = TaskBoard.starter();
    private File boundDir;
    private boolean built;
    /** Newest-wins guard for async loads (the v1.100.0 idiom). */
    private volatile int loadSeq;
    private ProjectAim.Listener aimListener;

    public TasksTopComponent() {
        setName(Bundle.CTL_TasksTopComponent());
        setToolTipText("Per-project task board (.nmoxtasks.json)");
    }

    // ---- lifecycle -------------------------------------------------------

    @Override
    protected void componentShowing() {
        if (!built) {
            built = true;
            buildUi();
            ProjectAim aim = ProjectAim.find();
            if (aim != null) {
                aimListener = () -> java.awt.EventQueue.invokeLater(this::reload);
                aim.addListener(aimListener);
            }
        }
        reload();
    }

    /** Re-reads the aimed project's board off the EDT, newest wins. */
    private void reload() {
        ProjectAim aim = ProjectAim.find();
        File dir = aim == null ? null : aim.projectDir();
        if (dir == null) {
            return;
        }
        int seq = ++loadSeq;
        IO_RP.post(() -> {
            TaskBoard loaded = TasksIO.load(dir);
            File f = TasksIO.fileFor(dir);
            if (f.isFile()) {
                tracker.noteSync(f);
            }
            java.awt.EventQueue.invokeLater(() -> {
                if (seq != loadSeq) {
                    return; // a newer aim/reload superseded this read
                }
                board = loaded;
                boundDir = dir;
                rebuild();
            });
        });
    }

    /**
     * Runs {@code mutation} against the CURRENT board and saves. If a
     * foreign edit landed since our last sync, the file wins: the board
     * reloads and the gesture is dropped with a status note rather than
     * silently overwriting someone's merge (the never-clobber law).
     */
    private void mutate(Runnable mutation) {
        File dir = boundDir;
        if (dir == null) {
            return;
        }
        if (TasksIO.foreignEdit(dir, tracker)) {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                    TasksIO.FILENAME + " changed outside the IDE — reloaded;"
                    + " repeat your change");
            reload();
            return;
        }
        mutation.run();
        rebuild();
        TaskBoard snapshot = board;
        IO_RP.post(() -> {
            try {
                TasksIO.save(dir, snapshot, tracker);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Could not save " + TasksIO.FILENAME, ex);
            }
        });
    }

    // ---- UI --------------------------------------------------------------

    private void buildUi() {
        setLayout(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton addCard = new JButton("New Card…");
        addCard.getAccessibleContext().setAccessibleName("New card");
        addCard.addActionListener(e -> newCardDialog(0));
        JButton addColumn = new JButton("New Column…");
        addColumn.getAccessibleContext().setAccessibleName("New column");
        addColumn.addActionListener(e -> newColumnDialog());
        top.add(addCard);
        top.add(addColumn);
        top.add(boardLabel);
        add(top, BorderLayout.NORTH);

        columnsPanel.setLayout(new BoxLayout(columnsPanel, BoxLayout.X_AXIS));
        JScrollPane scroll = new JScrollPane(columnsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    /** Rebuilds the whole column strip from the model. Cheap at kanban scale. */
    private void rebuild() {
        columnsPanel.removeAll();
        List<TaskBoard.Column> cols = board.columns();
        for (int i = 0; i < cols.size(); i++) {
            columnsPanel.add(columnPanel(i, cols.get(i)));
            columnsPanel.add(Box.createHorizontalStrut(6));
        }
        columnsPanel.add(Box.createHorizontalGlue());
        boardLabel.setText(boundDir == null ? " "
                : boundDir.getName() + " — " + board.cardCount() + " cards");
        columnsPanel.revalidate();
        columnsPanel.repaint();
    }

    private JPanel columnPanel(int index, TaskBoard.Column col) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setPreferredSize(new Dimension(230, 100));
        panel.setMaximumSize(new Dimension(230, Integer.MAX_VALUE));
        panel.setBorder(BorderFactory.createEtchedBorder());

        String count = col.wipLimit() > 0
                ? col.cards().size() + "/" + col.wipLimit()
                : String.valueOf(col.cards().size());
        JLabel header = PlainTables.plain(new JLabel(col.name() + "  " + count));
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        if (col.overLimit()) {
            header.setForeground(new Color(220, 80, 80)); // over WIP limit
        }
        header.getAccessibleContext().setAccessibleName(
                "Column " + col.name() + ", " + count + " cards"
                + (col.overLimit() ? ", over limit" : ""));
        header.setComponentPopupMenu(columnMenu(index));
        panel.add(header, BorderLayout.NORTH);

        DefaultListModel<TaskBoard.Card> model = new DefaultListModel<>();
        col.cards().forEach(model::addElement);
        JList<TaskBoard.Card> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new CardRenderer());
        list.getAccessibleContext().setAccessibleName(col.name() + " cards");
        wireList(list, index);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    /** Card text as PLAIN text (v1.311.0 — board files arrive with clones). */
    private static final class CardRenderer
            extends javax.swing.DefaultListCellRenderer {
        CardRenderer() {
            PlainTables.plain(this);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof TaskBoard.Card c) {
                setText(c.notes().isEmpty() ? c.title()
                        : c.title() + "  — " + firstLine(c.notes()));
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            }
            return this;
        }

        private static String firstLine(String notes) {
            int nl = notes.indexOf('\n');
            String head = nl < 0 ? notes : notes.substring(0, nl);
            return head.length() > 40 ? head.substring(0, 40) + "…" : head;
        }
    }

    // ---- gestures --------------------------------------------------------

    private void wireList(JList<TaskBoard.Card> list, int columnIndex) {
        // double-click edits
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    editCardDialog(list.getSelectedValue());
                }
            }
        });
        // keyboard: Enter edit, Delete remove, N new, cmd+arrows move
        InputBind.bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), () -> {
            if (list.getSelectedValue() != null) {
                editCardDialog(list.getSelectedValue());
            }
        });
        InputBind.bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), () -> {
            if (list.getSelectedValue() != null) {
                confirmRemoveCard(list.getSelectedValue());
            }
        });
        InputBind.bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), () -> {
            if (list.getSelectedValue() != null) {
                confirmRemoveCard(list.getSelectedValue());
            }
        });
        InputBind.bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_N, 0),
                () -> newCardDialog(columnIndex));
        int menuMask = java.awt.Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx();
        InputBind.bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, menuMask),
                () -> moveSelected(list, columnIndex, -1, 0));
        InputBind.bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, menuMask),
                () -> moveSelected(list, columnIndex, +1, 0));
        InputBind.bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_UP, menuMask),
                () -> moveSelected(list, columnIndex, 0, -1));
        InputBind.bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, menuMask),
                () -> moveSelected(list, columnIndex, 0, +1));

        // context menu — clicked card wins (v1.270.0)
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem("Edit…");
        edit.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                editCardDialog(list.getSelectedValue());
            }
        });
        JMenuItem delete = new JMenuItem("Delete…");
        delete.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                confirmRemoveCard(list.getSelectedValue());
            }
        });
        menu.add(edit);
        menu.add(delete);
        Popups.selectOnTrigger(list);
        list.setComponentPopupMenu(menu);

        // drag & drop between and within columns
        list.setDragEnabled(true);
        list.setDropMode(javax.swing.DropMode.INSERT);
        list.setTransferHandler(new CardTransfer(columnIndex));
    }

    private void moveSelected(JList<TaskBoard.Card> list, int fromColumn,
            int dCol, int dRow) {
        TaskBoard.Card sel = list.getSelectedValue();
        if (sel == null) {
            return;
        }
        int toColumn = fromColumn + dCol;
        if (dCol != 0) {
            if (toColumn < 0 || toColumn >= board.columnCount()) {
                return;
            }
            mutate(() -> board.moveCard(sel.id(), toColumn, Integer.MAX_VALUE));
        } else {
            int at = list.getSelectedIndex() + dRow;
            if (at < 0) {
                return;
            }
            mutate(() -> board.moveCard(sel.id(), fromColumn, at));
        }
    }

    private final class CardTransfer extends TransferHandler {
        private final int columnIndex;

        CardTransfer(int columnIndex) {
            this.columnIndex = columnIndex;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            @SuppressWarnings("unchecked")
            JList<TaskBoard.Card> list = (JList<TaskBoard.Card>) c;
            TaskBoard.Card sel = list.getSelectedValue();
            return sel == null ? null : new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] {CARD_FLAVOR};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor f) {
                    return CARD_FLAVOR.equals(f);
                }

                @Override
                public Object getTransferData(DataFlavor f) {
                    return sel.id();
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(CARD_FLAVOR);
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                String id = (String) support.getTransferable()
                        .getTransferData(CARD_FLAVOR);
                int at = support.getDropLocation() instanceof JList.DropLocation dl
                        ? dl.getIndex() : Integer.MAX_VALUE;
                // moving DOWN within the same column: the model removes the
                // card first, which shifts the insertion point up by one
                int fromCol = board.columnOf(id);
                if (fromCol == columnIndex) {
                    List<TaskBoard.Card> cards = board.column(columnIndex).cards();
                    for (int i = 0; i < cards.size(); i++) {
                        if (cards.get(i).id().equals(id) && i < at) {
                            at--;
                            break;
                        }
                    }
                }
                int finalAt = at;
                mutate(() -> board.moveCard(id, columnIndex, finalAt));
                return true;
            } catch (Exception ex) {
                return false;
            }
        }

        // MOVE semantics live entirely in importData (the model relocates
        // the card); exportDone must NOT also delete, or a same-column
        // drag would remove what it just placed.
        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
        }
    }

    /** Tiny helper: bind a KeyStroke to a Runnable on a component. */
    private static final class InputBind {
        static void bind(JComponent c, KeyStroke key, Runnable action) {
            String name = "tasks-" + key.toString();
            c.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
            c.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    action.run();
                }
            });
        }
    }

    // ---- dialogs ---------------------------------------------------------

    private void newCardDialog(int preferredColumn) {
        JPanel form = new JPanel(new BorderLayout(0, 6));
        JTextField title = new JTextField(28);
        title.getAccessibleContext().setAccessibleName("Card title");
        JTextArea notes = new JTextArea(5, 28);
        notes.getAccessibleContext().setAccessibleName("Card notes");
        form.add(title, BorderLayout.NORTH);
        form.add(new JScrollPane(notes), BorderLayout.CENTER);
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        DialogDescriptor d = new DialogDescriptor(form, "New Card");
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION
                && !title.getText().strip().isEmpty()) {
            mutate(() -> board.addCard(preferredColumn, title.getText(),
                    notes.getText()));
        }
    }

    private void editCardDialog(TaskBoard.Card card) {
        JPanel form = new JPanel(new BorderLayout(0, 6));
        JTextField title = new JTextField(card.title(), 28);
        title.getAccessibleContext().setAccessibleName("Card title");
        JTextArea notes = new JTextArea(card.notes(), 5, 28);
        notes.getAccessibleContext().setAccessibleName("Card notes");
        form.add(title, BorderLayout.NORTH);
        form.add(new JScrollPane(notes), BorderLayout.CENTER);
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        DialogDescriptor d = new DialogDescriptor(form, "Edit Card");
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION) {
            mutate(() -> board.editCard(card.id(), title.getText(),
                    notes.getText()));
        }
    }

    private void confirmRemoveCard(TaskBoard.Card card) {
        // full ctor with NO as the initial value — a reflexive Enter must
        // not delete (v1.98.0)
        NotifyDescriptor d = new NotifyDescriptor(
                "Delete card \"" + card.title() + "\"?", "Delete Card",
                NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.QUESTION_MESSAGE,
                null, NotifyDescriptor.NO_OPTION);
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.YES_OPTION) {
            mutate(() -> board.removeCard(card.id()));
        }
    }

    private void newColumnDialog() {
        NotifyDescriptor.InputLine in =
                new NotifyDescriptor.InputLine("Name:", "New Column");
        if (DialogDisplayer.getDefault().notify(in) == NotifyDescriptor.OK_OPTION
                && !in.getInputText().strip().isEmpty()) {
            mutate(() -> board.addColumn(in.getInputText(), 0));
        }
    }

    private JPopupMenu columnMenu(int index) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem("Rename…");
        rename.addActionListener(e -> {
            NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine(
                    "Name:", "Rename Column");
            in.setInputText(board.column(index).name());
            if (DialogDisplayer.getDefault().notify(in) == NotifyDescriptor.OK_OPTION) {
                mutate(() -> board.renameColumn(index, in.getInputText()));
            }
        });
        JMenuItem wip = new JMenuItem("Set WIP Limit…");
        wip.addActionListener(e -> {
            NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine(
                    "Limit (0 = none):", "WIP Limit");
            in.setInputText(String.valueOf(board.column(index).wipLimit()));
            if (DialogDisplayer.getDefault().notify(in) == NotifyDescriptor.OK_OPTION) {
                try {
                    int limit = Integer.parseInt(in.getInputText().strip());
                    mutate(() -> board.setWipLimit(index, limit));
                } catch (NumberFormatException ignore) {
                    // not a number: leave the limit as it was
                }
            }
        });
        JMenuItem left = new JMenuItem("Move Left");
        left.addActionListener(e -> mutate(() -> board.moveColumn(index, index - 1)));
        JMenuItem right = new JMenuItem("Move Right");
        right.addActionListener(e -> mutate(() -> board.moveColumn(index, index + 1)));
        JMenuItem remove = new JMenuItem("Delete Column…");
        remove.addActionListener(e -> {
            int n = board.column(index).cards().size();
            NotifyDescriptor d = new NotifyDescriptor(
                    n == 0 ? "Delete this empty column?"
                           : "Delete this column AND its " + n + " cards?",
                    "Delete Column", NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.QUESTION_MESSAGE, null,
                    NotifyDescriptor.NO_OPTION);
            if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.YES_OPTION
                    && !board.removeColumn(index)) {
                org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                        "A board keeps at least one column");
            }
        });
        menu.add(rename);
        menu.add(wip);
        menu.addSeparator();
        menu.add(left);
        menu.add(right);
        menu.addSeparator();
        menu.add(remove);
        return menu;
    }

    // ---- ⌘I reach --------------------------------------------------------

    /** Snapshot for the Quick Search provider: (title, column) pairs. */
    List<String[]> searchSnapshot() {
        List<String[]> out = new ArrayList<>();
        for (TaskBoard.Column col : board.columns()) {
            for (TaskBoard.Card c : col.cards()) {
                out.add(new String[] {c.title(), col.name()});
            }
        }
        return out;
    }
}
