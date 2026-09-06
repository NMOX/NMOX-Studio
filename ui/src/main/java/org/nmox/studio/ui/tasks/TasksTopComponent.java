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
import org.nmox.studio.core.util.FilePulse;
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
 *       re-reads on aim change, re-checks the file before stacking a
 *       mutation on a foreign edit, and — since v2.7.0 — a live
 *       {@link FilePulse} notices an external edit while the tab is
 *       visible and reloads within ~1.5 s. The v1.323.0 javadoc
 *       recorded "no live watcher until that seam moves to core" as a
 *       written limit; the seam moved (apiclient's pulse was promoted
 *       to {@code core.util}) and the limit is closed. Self-writes are
 *       discriminated by the tracker so the studio's own saves never
 *       bounce back as reloads.</li>
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
    /** The v2.4.0 dashboard face; lives beside the strip in a CardLayout. */
    private final OverviewPanel overviewPanel =
            new OverviewPanel(this::editRetroDialog);
    private final java.awt.CardLayout faces = new java.awt.CardLayout();
    private final JPanel center = new JPanel(faces);
    private javax.swing.JToggleButton overviewToggle;

    private TaskBoard board = TaskBoard.starter();
    private File boundDir;
    private boolean built;
    /** Newest-wins guard for async loads (the v1.100.0 idiom). */
    private volatile int loadSeq;
    private ProjectAim.Listener aimListener;
    /** The live external-edit pulse over the bound .nmoxtasks.json
     *  (v2.7.0); re-aimed with the project, stopped with the tab. */
    private FilePulse filePulse;
    /** Ticks the header's running-clock elapsed while the tab shows;
     *  label-only on purpose — see headerText(). */
    private final javax.swing.Timer clockTicker = new javax.swing.Timer(
            30_000, e -> {
                if (boundDir != null && board.runningCard() != null) {
                    boardLabel.setText(headerText());
                }
            });
    /**
     * The card to re-select after the next rebuild. Every mutation
     * rebuilds the whole strip, which discards the JLists and with them
     * the selection — so without this a ⌘↓ moved the card once and then
     * needed the mouse again. Set it before the mutation; {@link
     * #rebuild()} consumes it.
     */
    private String focusCardId;

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
        clockTicker.start();
    }

    @Override
    protected void componentHidden() {
        clockTicker.stop();
        stopFilePulse();
    }

    private synchronized void stopFilePulse() {
        if (filePulse != null) {
            filePulse.stop();
            filePulse = null;
        }
    }

    /**
     * (Re)aims the pulse at the bound project's file. The callback runs
     * on the pulse's own daemon thread: the tracker's isForeign is the
     * FIRST gate — the studio's own atomic saves change mtime+size too,
     * and reloading on those would discard the strip's selection for no
     * reason. Foreign means someone else wrote the file; the file wins.
     */
    private synchronized void restartFilePulse() {
        stopFilePulse();
        if (boundDir == null || !isOpened()) {
            return;
        }
        filePulse = new FilePulse(TasksIO.fileFor(boundDir),
                (mtime, size) -> {
                    if (tracker.isForeign(mtime, size)) {
                        java.awt.EventQueue.invokeLater(this::reload);
                    }
                });
        filePulse.start(FilePulse.DEFAULT_INTERVAL_MS);
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
                restartFilePulse();
            });
        });
    }

    /**
     * Runs {@code mutation} against the CURRENT board; when the board
     * ACCEPTS it, repaints and saves, and when the board refuses (an
     * edge move, a double clock-in, a blank name) NOTHING is written —
     * a refused gesture must not dirty the checked-in file (v2.18.0).
     * The foreign-edit check rides the IO lane with the save (the
     * v1.108.0 disk-off-EDT law): if an outside write landed since our
     * last sync, the file wins — the applied gesture is rolled back by
     * a reload with a status note rather than silently overwriting
     * someone's merge (the never-clobber law).
     *
     * @return true when the board accepted the mutation
     */
    private boolean mutate(java.util.function.BooleanSupplier mutation) {
        File dir = boundDir;
        if (dir == null) {
            return false;
        }
        if (!mutation.getAsBoolean()) {
            return false;
        }
        rebuild();
        TaskBoard snapshot = board;
        IO_RP.post(() -> {
            if (TasksIO.foreignEdit(dir, tracker)) {
                java.awt.EventQueue.invokeLater(() -> {
                    status(TasksIO.FILENAME + " changed outside the IDE"
                            + " — reloaded; repeat your change");
                    reload();
                });
                return;
            }
            try {
                TasksIO.save(dir, snapshot, tracker);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Could not save " + TasksIO.FILENAME, ex);
            }
        });
        return true;
    }

    /** One-line outcome report on the status line — every refused or
     *  destructive-by-side-effect gesture on this board says what
     *  happened (the blockDialog precedent, made the house rule). */
    private static void status(String text) {
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(text);
    }

    // ---- UI --------------------------------------------------------------

    private void buildUi() {
        setLayout(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton addCard = new JButton("New Card…");
        addCard.getAccessibleContext().setAccessibleName("New card");
        addCard.setToolTipText("Adds a card to the first column");
        addCard.addActionListener(e -> newCardDialog(0));
        JButton addColumn = new JButton("New Column…");
        addColumn.getAccessibleContext().setAccessibleName("New column");
        addColumn.setToolTipText("Adds a column at the end of the board");
        addColumn.addActionListener(e -> newColumnDialog());
        overviewToggle = new javax.swing.JToggleButton("Overview");
        overviewToggle.getAccessibleContext().setAccessibleName(
                "Toggle board overview");
        overviewToggle.setToolTipText(
                "Dashboard read of this board: WIP, flow, aging cards");
        overviewToggle.addActionListener(e -> {
            faces.show(center, overviewToggle.isSelected() ? "overview" : "board");
            rebuild();
        });
        JButton standup = new JButton("Standup…");
        standup.getAccessibleContext().setAccessibleName(
                "Generate standup report");
        standup.setToolTipText("Yesterday / today / blockers, from this"
                + " board's clock and stamps plus the git log — as markdown");
        standup.addActionListener(e -> showStandup());
        JButton sprint = new JButton("Sprint…");
        sprint.getAccessibleContext().setAccessibleName("Sprint menu");
        sprint.setToolTipText("Set the sprint window, generate the sprint report,"
                + " or close the sprint — the scrum ceremonies live here");
        sprint.addActionListener(e -> showSprintMenu(sprint));
        top.add(addCard);
        top.add(addColumn);
        top.add(overviewToggle);
        top.add(standup);
        top.add(sprint);
        top.add(boardLabel);
        add(top, BorderLayout.NORTH);

        columnsPanel.setLayout(new BoxLayout(columnsPanel, BoxLayout.X_AXIS));
        JScrollPane scroll = new JScrollPane(columnsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane overviewScroll = new JScrollPane(overviewPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        overviewScroll.setBorder(BorderFactory.createEmptyBorder());
        overviewScroll.getViewport().setBackground(OverviewPanel.GROUND);
        center.add(scroll, "board");
        center.add(overviewScroll, "overview");
        add(center, BorderLayout.CENTER);
    }

    /** Rebuilds the visible face from the model. Cheap at kanban scale. */
    private void rebuild() {
        if (overviewToggle != null && overviewToggle.isSelected()) {
            // the overview reads the SAME model; every mutation and reload
            // lands here too, so its numbers can never go stale
            overviewPanel.show(board,
                    boundDir == null ? null : boundDir.getName());
        }
        columnsPanel.removeAll();
        List<TaskBoard.Column> cols = board.columns();
        for (int i = 0; i < cols.size(); i++) {
            columnsPanel.add(columnPanel(i, cols.get(i)));
            columnsPanel.add(Box.createHorizontalStrut(6));
        }
        columnsPanel.add(Box.createHorizontalGlue());
        boardLabel.setText(boundDir == null ? " " : headerText());
        columnsPanel.revalidate();
        columnsPanel.repaint();
        focusCardId = null; // consumed by the panels just built
    }

    /** The header line; the running clock's elapsed rides here so the
     *  30s ticker can refresh it WITHOUT rebuilding the strip (a rebuild
     *  would drop the list selection every tick). */
    private String headerText() {
        String base = boundDir.getName() + " — " + org.nmox.studio.core.util.Plural.of(board.cardCount(), "card");
        TaskBoard.Card running = board.runningCard();
        if (running == null) {
            return base;
        }
        long since = running.sessions().get(running.sessions().size() - 1)[0];
        return base + "   \u23f1 " + running.title() + " · "
                + BoardStats.duration(System.currentTimeMillis() - since);
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
                "Column " + col.name() + ", " + count + (col.cards().size() == 1 ? " card" : " cards")
                + (col.overLimit() ? ", over limit" : ""));
        header.setComponentPopupMenu(columnMenu(index));
        panel.add(header, BorderLayout.NORTH);

        DefaultListModel<TaskBoard.Card> model = new DefaultListModel<>();
        col.cards().forEach(model::addElement);
        // drag is enabled below, and on a drag-enabled list the plain
        // selectOnTrigger listener never runs (v1.326.0, measured in the
        // shipped app) — this form hooks the popup path itself
        JList<TaskBoard.Card> list = Popups.popupTargetList(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new CardRenderer());
        list.getAccessibleContext().setAccessibleName(col.name() + " cards");
        wireList(list, index);
        // a just-moved card keeps selection and focus, so ⌘↓ ⌘↓ ⌘→ reads
        // as one continuous gesture instead of one move per mouse click
        if (focusCardId != null) {
            for (int r = 0; r < model.size(); r++) {
                if (model.get(r).id().equals(focusCardId)) {
                    list.setSelectedIndex(r);
                    list.ensureIndexIsVisible(r);
                    java.awt.EventQueue.invokeLater(list::requestFocusInWindow);
                    break;
                }
            }
        }
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
                String head = c.blocked() ? "\u26d4 " + c.title() : c.title();
                if (c.clockedIn()) {
                    head = "\u23f1 " + head;
                }
                String tail = c.label().isEmpty() ? "" : "  [" + c.label() + "]";
                setText((c.notes().isEmpty() ? head
                        : head + "  — " + firstLine(c.notes())) + tail);
                if (c.blocked() && !selected) {
                    setForeground(new Color(220, 80, 80));
                }
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
        JMenuItem label = new JMenuItem("Set Label…");
        label.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                setLabelDialog(list.getSelectedValue());
            }
        });
        JMenuItem clockIn = new JMenuItem("Clock In");
        clockIn.addActionListener(e -> {
            TaskBoard.Card sel = list.getSelectedValue();
            if (sel == null) {
                return;
            }
            // ONE clock runs board-wide, so clocking in here clocks out
            // whatever was running — say so instead of moving it silently
            TaskBoard.Card was = board.runningCard();
            if (mutate(() -> board.clockIn(sel.id(), System.currentTimeMillis()))) {
                status(was == null ? "Clocked in"
                        : "Clocked in — stopped the clock on \"" + was.title()
                                + "\" (one clock per board)");
            } else {
                status("That card's clock is already running");
            }
        });
        JMenuItem clockOut = new JMenuItem("Clock Out");
        clockOut.addActionListener(e -> {
            TaskBoard.Card sel = list.getSelectedValue();
            if (sel == null) {
                return;
            }
            long now = System.currentTimeMillis();
            // computed BEFORE the mutation removes the session: a sub-minute
            // session is dropped whole, and a deletion must never be mute
            boolean blip = sel.clockedIn() && now
                    - sel.sessions().get(sel.sessions().size() - 1)[0]
                    < TaskBoard.BLIP_MS;
            if (mutate(() -> board.clockOut(sel.id(), now))) {
                status(blip ? "Clocked out — under a minute, dropped as a blip"
                        : "Clocked out");
            } else {
                status("No clock running on that card");
            }
        });
        JMenuItem block = new JMenuItem("Mark Blocked…");
        block.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                blockDialog(list.getSelectedValue());
            }
        });
        JMenuItem unblock = new JMenuItem("Unblock");
        unblock.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                mutate(() -> board.unblock(list.getSelectedValue().id()));
            }
        });
        menu.add(edit);
        menu.add(label);
        menu.addSeparator();
        menu.add(clockIn);
        menu.add(clockOut);
        menu.addSeparator();
        menu.add(block);
        menu.add(unblock);
        menu.addSeparator();
        menu.add(delete);
        // no selectOnTrigger here: the list is drag-enabled, so the
        // clicked card is claimed by popupTargetList's getPopupLocation
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
        focusCardId = sel.id();
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
                focusCardId = id; // the dropped card stays selected where it landed
                mutate(() -> board.moveCard(id, columnIndex, finalAt));
                return true;
            } catch (Exception ex) {
                // a failed drop is visible (the card snaps back), but a
                // broken drag pipeline must leave a trace to debug
                LOG.log(Level.FINE, "Card drop refused", ex);
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
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION) {
            if (title.getText().strip().isEmpty()) {
                status("A card needs a title — nothing added");
                return;
            }
            mutate(() -> board.addCard(preferredColumn, title.getText(),
                    notes.getText()) != null);
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
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION
                && !mutate(() -> board.editCard(card.id(), title.getText(),
                        notes.getText()))) {
            status("A card needs a title — unchanged");
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
                && !mutate(() -> board.addColumn(in.getInputText(), 0))) {
            status("A column needs a name — nothing added");
        }
    }

    /**
     * The standup (v2.8.0): gathers the git log OFF the EDT (a fixed-argv
     * read-only spawn, the GitFacts family — no project code executes, so
     * no trust gate), then assembles and shows the markdown on the EDT
     * where the board may be touched safely.
     */
    private void showStandup() {
        File dir = boundDir;
        if (dir == null) {
            return;
        }
        IO_RP.post(() -> {
            List<StandupReport.Commit> commits = new ArrayList<>();
            try {
                org.nmox.studio.core.process.ProcessSupport.BoundedResult r =
                        org.nmox.studio.core.process.ProcessSupport.runBounded(
                                List.of("git", "log", "--since=yesterday.midnight",
                                        "--format=%ct%x09%h %s"),
                                dir, java.time.Duration.ofSeconds(5));
                if (r.exitCode() == 0) {
                    for (String line : r.stdout().split("\n")) {
                        int tab = line.indexOf('\t');
                        if (tab > 0) {
                            try {
                                commits.add(new StandupReport.Commit(
                                        line.substring(tab + 1).strip(),
                                        Long.parseLong(line.substring(0, tab).strip())
                                                * 1000L));
                            } catch (NumberFormatException skip) {
                                // a malformed line loses itself, not the report
                            }
                        }
                    }
                }
            } catch (IOException noGit) {
                // no repo / no git on PATH: the commits section just
                // doesn't appear — the report stays honest without it
            }
            java.awt.EventQueue.invokeLater(() -> {
                String md = StandupReport.build(board, commits,
                        System.currentTimeMillis(),
                        java.time.ZoneId.systemDefault());
                JTextArea text = new JTextArea(md, 18, 52);
        text.getAccessibleContext().setAccessibleName("Standup report");
                text.setEditable(false);
                text.setCaretPosition(0);
                JScrollPane scroll = new JScrollPane(text);
                scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                JButton copy = new JButton("Copy to Clipboard");
                copy.getAccessibleContext().setAccessibleName(
                        "Copy standup to clipboard");
                copy.addActionListener(ev -> {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer
                                    .StringSelection(text.getText()), null);
                    org.openide.awt.StatusDisplayer.getDefault()
                            .setStatusText("Standup copied");
                });
                DialogDescriptor d = new DialogDescriptor(scroll, "Standup");
                d.setOptions(new Object[]{copy, NotifyDescriptor.CANCEL_OPTION});
                d.setClosingOptions(new Object[]{NotifyDescriptor.CANCEL_OPTION});
                DialogDisplayer.getDefault().notify(d);
            });
        });
    }

    // ---- the sprint ceremonies (v2.37.0, the scrum-master pass) ----------

    private void showSprintMenu(java.awt.Component owner) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem set = new javax.swing.JMenuItem(
                board.hasSprint() ? "Edit Sprint…" : "Start Sprint…");
        set.addActionListener(e -> editSprint());
        menu.add(set);
        javax.swing.JMenuItem report = new javax.swing.JMenuItem("Sprint Report…");
        report.setEnabled(board.hasSprint());
        report.addActionListener(e -> showSprintReport());
        menu.add(report);
        javax.swing.JMenuItem close = new javax.swing.JMenuItem("Close Sprint…");
        close.setEnabled(board.hasSprint());
        close.addActionListener(e -> closeSprint());
        menu.add(close);
        menu.show(owner, 0, owner.getHeight());
    }

    private void editSprint() {
        editSprint(null, null, null);
    }

    /** Non-null prefills override the defaults — the roll-over's seam. */
    private void editSprint(String prefillName, java.time.LocalDate prefillStart,
            java.time.LocalDate prefillEnd) {
        javax.swing.JTextField name = new javax.swing.JTextField(prefillName != null
                ? prefillName
                : board.hasSprint() ? board.sprintName() : "", 18);
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        javax.swing.JTextField start = new javax.swing.JTextField(prefillStart != null
                ? prefillStart.toString()
                : board.hasSprint()
                ? java.time.LocalDate.ofInstant(java.time.Instant
                        .ofEpochMilli(board.sprintStart()), zone).toString()
                : today.toString(), 10);
        javax.swing.JTextField end = new javax.swing.JTextField(prefillEnd != null
                ? prefillEnd.toString()
                : board.hasSprint()
                ? java.time.LocalDate.ofInstant(java.time.Instant
                        .ofEpochMilli(board.sprintEnd()), zone).toString()
                : today.plusDays(13).toString(), 10);
        javax.swing.JPanel panel = new javax.swing.JPanel(
                new java.awt.GridLayout(0, 2, 6, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new javax.swing.JLabel("Sprint name:"));
        panel.add(name);
        panel.add(new javax.swing.JLabel("Start (YYYY-MM-DD):"));
        panel.add(start);
        panel.add(new javax.swing.JLabel("End (YYYY-MM-DD):"));
        panel.add(end);
        String velocity = SprintRoll.velocityLine(board.sprintHistory());
        if (velocity != null) {
            javax.swing.JLabel v = new javax.swing.JLabel(
                    "<html><small>" + velocity + "</small></html>");
            v.getAccessibleContext().setAccessibleName(velocity);
            panel.add(v);
            panel.add(new javax.swing.JLabel(""));
        }
        DialogDescriptor d = new DialogDescriptor(panel, "Sprint");
        if (DialogDisplayer.getDefault().notify(d) != DialogDescriptor.OK_OPTION) {
            return;
        }
        java.time.LocalDate s0;
        java.time.LocalDate s1;
        try {
            s0 = java.time.LocalDate.parse(start.getText().strip());
            s1 = java.time.LocalDate.parse(end.getText().strip());
        } catch (java.time.format.DateTimeParseException bad) {
            status("Sprint dates must be YYYY-MM-DD — nothing changed");
            return;
        }
        if (name.getText().isBlank()) {
            status("A sprint needs a name — nothing changed");
            return;
        }
        if (s1.isBefore(s0)) {
            status("The sprint can't end before it starts — nothing changed");
            return;
        }
        long startMs = s0.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = s1.atStartOfDay(zone).toInstant().toEpochMilli();
        String n = name.getText().strip();
        mutate(() -> {
            board.setSprint(n, startMs, endMs);
            return true;
        });
        status("Sprint " + n + " — " + s0 + " … " + s1);
    }

    private void showSprintReport() {
        String md = SprintReport.build(board, System.currentTimeMillis(),
                java.time.ZoneId.systemDefault());
        if (md.isEmpty()) {
            return;
        }
        JTextArea text = new JTextArea(md, 18, 52);
        text.getAccessibleContext().setAccessibleName("Sprint report");
        text.setEditable(false);
        text.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton copy = new JButton("Copy to Clipboard");
        copy.getAccessibleContext().setAccessibleName("Copy sprint report to clipboard");
        copy.addActionListener(ev -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer
                            .StringSelection(text.getText()), null);
            org.openide.awt.StatusDisplayer.getDefault()
                    .setStatusText("Sprint report copied");
        });
        DialogDescriptor d = new DialogDescriptor(scroll, "Sprint Report");
        d.setOptions(new Object[]{copy, NotifyDescriptor.CANCEL_OPTION});
        d.setClosingOptions(new Object[]{NotifyDescriptor.CANCEL_OPTION});
        DialogDisplayer.getDefault().notify(d);
    }

    private void closeSprint() {
        // closing archives and clears — irreversible bookkeeping, so the
        // reflexive Enter lands on No (the v1.98.0 safe default)
        NotifyDescriptor confirm = new NotifyDescriptor(
                "Close sprint " + board.sprintName() + "? The window, done count,"
                + " and retro notes are archived; cards stay where they are.",
                "Close Sprint", NotifyDescriptor.YES_NO_OPTION,
                NotifyDescriptor.QUESTION_MESSAGE,
                new Object[]{NotifyDescriptor.YES_OPTION, NotifyDescriptor.NO_OPTION},
                NotifyDescriptor.NO_OPTION);
        if (DialogDisplayer.getDefault().notify(confirm) != NotifyDescriptor.YES_OPTION) {
            return;
        }
        TaskBoard.ClosedSprint[] out = new TaskBoard.ClosedSprint[1];
        mutate(() -> {
            out[0] = board.closeSprint();
            return out[0] != null;
        });
        if (out[0] != null) {
            status("Sprint " + out[0].name() + " closed — " + out[0].done()
                    + " done, archived for velocity");
            // the roll-over (v2.38.1): consecutive sprints are the norm,
            // so offer the next one pre-filled — name incremented, window
            // the day after at the same length. Enter accepts (starting a
            // sprint is not destructive); the dialog stays editable and
            // its Cancel starts nothing.
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.LocalDate closedStart = java.time.LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(out[0].start()), zone);
            java.time.LocalDate closedEnd = java.time.LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(out[0].end()), zone);
            java.time.LocalDate[] next = SprintRoll.nextWindow(closedStart, closedEnd);
            NotifyDescriptor roll = new NotifyDescriptor(
                    "Start the next sprint now? The dialog comes pre-filled"
                    + " and editable.",
                    "Next Sprint", NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.QUESTION_MESSAGE, null, NotifyDescriptor.YES_OPTION);
            if (DialogDisplayer.getDefault().notify(roll) == NotifyDescriptor.YES_OPTION) {
                editSprint(SprintRoll.nextName(out[0].name()), next[0], next[1]);
            }
        }
    }

    /** Board-level retro notes (v2.5.0) — the overview's Edit Retro…. */
    private void editRetroDialog() {
        JTextArea text = new JTextArea(board.retro(), 10, 44);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.getAccessibleContext().setAccessibleName("Retro notes");
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        DialogDescriptor d = new DialogDescriptor(scroll,
                "Retro — went well / bit us / changed");
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION) {
            mutate(() -> { board.setRetro(text.getText()); return true; });
        }
    }

    private void setLabelDialog(TaskBoard.Card card) {
        NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine(
                "Label (blank clears):", "Set Label");
        in.setInputText(card.label());
        if (DialogDisplayer.getDefault().notify(in) == NotifyDescriptor.OK_OPTION) {
            mutate(() -> board.setLabel(card.id(), in.getInputText()));
        }
    }

    private void blockDialog(TaskBoard.Card card) {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        JTextField owner = new JTextField(card.blockOwner(), 28);
        owner.getAccessibleContext().setAccessibleName("Blocker owner");
        JTextField action = new JTextField(card.blockAction(), 28);
        action.getAccessibleContext().setAccessibleName("Unblock action");
        form.add(new JLabel("Owner (who is on the hook):"));
        form.add(owner);
        form.add(new JLabel("Unblock action (what gets it moving — required):"));
        form.add(action);
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        DialogDescriptor d = new DialogDescriptor(form, "Mark Blocked");
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION) {
            if (action.getText().strip().isEmpty()) {
                org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                        "A blocker needs an unblock action — that is what"
                        + " makes the register actionable");
                return;
            }
            mutate(() -> board.block(card.id(), owner.getText(),
                    action.getText()));
        }
    }

    private JPopupMenu columnMenu(int index) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem("Rename…");
        rename.addActionListener(e -> {
            NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine(
                    "Name:", "Rename Column");
            in.setInputText(board.column(index).name());
            if (DialogDisplayer.getDefault().notify(in) == NotifyDescriptor.OK_OPTION
                    && !mutate(() -> board.renameColumn(index, in.getInputText()))) {
                status("A column needs a name — kept \""
                        + board.column(index).name() + "\"");
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
                    // not a number: keep the limit, and say so — a swallowed
                    // gesture reads as a broken dialog
                    status("WIP limit must be a number — kept "
                            + board.column(index).wipLimit());
                }
            }
        });
        JMenuItem left = new JMenuItem("Move Left");
        left.addActionListener(e -> {
            if (!mutate(() -> board.moveColumn(index, index - 1))) {
                status("Already the first column");
            }
        });
        JMenuItem right = new JMenuItem("Move Right");
        right.addActionListener(e -> {
            if (!mutate(() -> board.moveColumn(index, index + 1))) {
                status("Already the last column");
            }
        });
        JMenuItem remove = new JMenuItem("Delete Column…");
        remove.addActionListener(e -> {
            int n = board.column(index).cards().size();
            NotifyDescriptor d = new NotifyDescriptor(
                    n == 0 ? "Delete this empty column?"
                           : "Delete this column AND its " + n + " cards?",
                    "Delete Column", NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.QUESTION_MESSAGE, null,
                    NotifyDescriptor.NO_OPTION);
            if (DialogDisplayer.getDefault().notify(d) != NotifyDescriptor.YES_OPTION) {
                return;
            }
            // the refusal is checked BEFORE the mutation so the message is
            // the only outcome; the delete itself must ride mutate() like
            // every other mutation, or it repaints nothing and saves
            // nothing (v1.325.0 — it did neither, and the stale header
            // menus then aimed a second click at a different column)
            if (board.columnCount() <= 1) {
                org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                        "A board keeps at least one column");
                return;
            }
            mutate(() -> board.removeColumn(index));
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
