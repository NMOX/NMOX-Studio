package org.nmox.studio.ui.tasks;

import org.nmox.studio.core.util.PlainText;
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
        // v2.118.0, David's call after the coherence pass measured the
        // first launch: an empty three-column board opened on a project that has none, and touching
        // it writes .nmoxtasks.json into someone else's repository.
        // The window is one ⌥⌘ chord, one Welcome link and one Window-menu
        // row away — discovery keeps three surfaces, and the tab strip stops
        // being one of them. Only a userdir with no saved layout is affected;
        // an existing install keeps the layout it has (ledger 96a).
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 357)
@ActionID(category = "Window", id = "org.nmox.studio.ui.tasks.TasksTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 269),
    @ActionReference(path = "Shortcuts", name = "DA-1")
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_TasksAction",
        preferredID = "TasksTopComponent")
@Messages({
    "CTL_TasksAction=Task Board",
    "CTL_TasksTopComponent=Task Board",
    "TasksTopComponent_tooltip=Per-project task board (.nmoxtasks.json)",
    "TasksTopComponent_changedOutside={0} changed outside the IDE — reloaded; repeat your change",
    "TasksTopComponent_newCard=New Card…",
    "TasksTopComponent_newCardA11y=New card",
    "TasksTopComponent_newCardTip=Adds a card to the first column",
    "TasksTopComponent_newColumn=New Column…",
    "TasksTopComponent_newColumnA11y=New column",
    "TasksTopComponent_newColumnTip=Adds a column at the end of the board",
    "TasksTopComponent_overview=Overview",
    "TasksTopComponent_overviewA11y=Toggle board overview",
    "TasksTopComponent_overviewTip=Dashboard read of this board: WIP, flow, aging cards",
    "TasksTopComponent_standup=Standup…",
    "TasksTopComponent_standupA11y=Generate standup report",
    "TasksTopComponent_standupTip=Yesterday / today / blockers, from this board's clock and stamps plus the git log — as markdown",
    "TasksTopComponent_sprint=Sprint…",
    "TasksTopComponent_sprintA11y=Sprint menu",
    "TasksTopComponent_sprintTip=Set the sprint window, generate the sprint report, or close the sprint — the scrum ceremonies live here",
    "TasksTopComponent_card=card",
    "TasksTopComponent_cards=cards",
    "TasksTopComponent_header={0} — {1}",
    "TasksTopComponent_headerRunning={0}   \u23f1 {1} · {2}",
    "TasksTopComponent_countOfLimit={0}/{1}",
    "TasksTopComponent_columnHeader={0}  {1}",
    "TasksTopComponent_columnA11y=Column {0}, {1} {2}{3}",
    "TasksTopComponent_overLimitSuffix=, over limit",
    "TasksTopComponent_columnCardsA11y={0} cards",
    "TasksTopComponent_blockedCard=\u26d4 {0}",
    "TasksTopComponent_clockedCard=\u23f1 {0}",
    "TasksTopComponent_cardWithNotes={0}  — {1}",
    "TasksTopComponent_cardWithLabel={0}  [{1}]",
    "TasksTopComponent_edit=Edit…",
    "TasksTopComponent_delete=Delete…",
    "TasksTopComponent_setLabel=Set Label…",
    "TasksTopComponent_clockIn=Clock In",
    "TasksTopComponent_clockedIn=Clocked in",
    "TasksTopComponent_clockedInStopped=Clocked in — stopped the clock on \"{0}\" (one clock per board)",
    "TasksTopComponent_clockAlreadyRunning=That card's clock is already running",
    "TasksTopComponent_clockOut=Clock Out",
    "TasksTopComponent_clockedOutBlip=Clocked out — under a minute, dropped as a blip",
    "TasksTopComponent_clockedOut=Clocked out",
    "TasksTopComponent_noClockRunning=No clock running on that card",
    "TasksTopComponent_markBlocked=Mark Blocked…",
    "TasksTopComponent_unblock=Unblock",
    "TasksTopComponent_cardTitleA11y=Card title",
    "TasksTopComponent_cardNotesA11y=Card notes",
    "TasksTopComponent_newCardTitle=New Card",
    "TasksTopComponent_cardNeedsTitleNothingAdded=A card needs a title — nothing added",
    "TasksTopComponent_editCardTitle=Edit Card",
    "TasksTopComponent_cardNeedsTitleUnchanged=A card needs a title — unchanged",
    "TasksTopComponent_deleteCardQuestion=Delete card \"{0}\"?",
    "TasksTopComponent_messageA11y=Message",
    "TasksTopComponent_deleteCardTitle=Delete Card",
    "TasksTopComponent_nameLabel=Name:",
    "TasksTopComponent_newColumnTitle=New Column",
    "TasksTopComponent_columnNeedsNameNothingAdded=A column needs a name — nothing added",
    "TasksTopComponent_standupReportA11y=Standup report",
    "TasksTopComponent_copyToClipboard=Copy to Clipboard",
    "TasksTopComponent_copyStandupA11y=Copy standup to clipboard",
    "TasksTopComponent_standupCopied=Standup copied",
    "TasksTopComponent_standupTitle=Standup",
    "TasksTopComponent_editSprint=Edit Sprint…",
    "TasksTopComponent_startSprint=Start Sprint…",
    "TasksTopComponent_sprintReport=Sprint Report…",
    "TasksTopComponent_closeSprint=Close Sprint…",
    "TasksTopComponent_sprintNameLabel=Sprint name:",
    "TasksTopComponent_sprintStartLabel=Start (YYYY-MM-DD):",
    "TasksTopComponent_sprintEndLabel=End (YYYY-MM-DD):",
    "TasksTopComponent_velocityHtml=<html><small>{0}</small></html>",
    "TasksTopComponent_sprintTitle=Sprint",
    "TasksTopComponent_sprintDatesInvalid=Sprint dates must be YYYY-MM-DD — nothing changed",
    "TasksTopComponent_sprintNeedsName=A sprint needs a name — nothing changed",
    "TasksTopComponent_sprintEndsBeforeStart=The sprint can't end before it starts — nothing changed",
    "TasksTopComponent_sprintSet=Sprint {0} — {1} … {2}",
    "TasksTopComponent_sprintReportA11y=Sprint report",
    "TasksTopComponent_copySprintReportA11y=Copy sprint report to clipboard",
    "TasksTopComponent_sprintReportCopied=Sprint report copied",
    "TasksTopComponent_sprintReportTitle=Sprint Report",
    "TasksTopComponent_closeSprintQuestion=Close sprint {0}? The window, done count, and retro notes are archived; cards stay where they are.",
    "TasksTopComponent_closeSprintTitle=Close Sprint",
    "TasksTopComponent_sprintClosed=Sprint {0} closed — {1} done, archived for velocity",
    "TasksTopComponent_nextSprintQuestion=Start the next sprint now? The dialog comes pre-filled and editable.",
    "TasksTopComponent_nextSprintTitle=Next Sprint",
    "TasksTopComponent_retroNotesA11y=Retro notes",
    "TasksTopComponent_retroTitle=Retro — went well / bit us / changed",
    "TasksTopComponent_labelPrompt=Label (blank clears):",
    "TasksTopComponent_setLabelTitle=Set Label",
    "TasksTopComponent_blockerOwnerA11y=Blocker owner",
    "TasksTopComponent_unblockActionA11y=Unblock action",
    "TasksTopComponent_ownerLabel=Owner (who is on the hook):",
    "TasksTopComponent_unblockActionLabel=Unblock action (what gets it moving — required):",
    "TasksTopComponent_markBlockedTitle=Mark Blocked",
    "TasksTopComponent_blockerNeedsAction=A blocker needs an unblock action — that is what makes the register actionable",
    "TasksTopComponent_rename=Rename…",
    "TasksTopComponent_renameColumnTitle=Rename Column",
    "TasksTopComponent_columnNeedsNameKept=A column needs a name — kept \"{0}\"",
    "TasksTopComponent_setWipLimit=Set WIP Limit…",
    "TasksTopComponent_wipLimitPrompt=Limit (0 = none):",
    "TasksTopComponent_wipLimitTitle=WIP Limit",
    "TasksTopComponent_wipLimitNotNumber=WIP limit must be a number — kept {0}",
    "TasksTopComponent_moveLeft=Move Left",
    "TasksTopComponent_alreadyFirst=Already the first column",
    "TasksTopComponent_moveRight=Move Right",
    "TasksTopComponent_alreadyLast=Already the last column",
    "TasksTopComponent_deleteColumn=Delete Column…",
    "TasksTopComponent_deleteEmptyColumnQuestion=Delete this empty column?",
    "TasksTopComponent_deleteColumnWithCardsQuestion=Delete this column AND its {0} cards?",
    "TasksTopComponent_deleteColumnTitle=Delete Column",
    "TasksTopComponent_boardKeepsOneColumn=A board keeps at least one column"
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

    private TaskBoard board = TasksIO.starterBoard();
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
                    boardLabel.setText(PlainText.plain(headerText()));
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
        setToolTipText(Bundle.TasksTopComponent_tooltip());
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
                    status(Bundle.TasksTopComponent_changedOutside(TasksIO.FILENAME));
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
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(text));
    }

    // ---- UI --------------------------------------------------------------

    private void buildUi() {
        setLayout(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton addCard = new JButton(Bundle.TasksTopComponent_newCard());
        addCard.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_newCardA11y());
        addCard.setToolTipText(Bundle.TasksTopComponent_newCardTip());
        addCard.addActionListener(e -> newCardDialog(0));
        JButton addColumn = new JButton(Bundle.TasksTopComponent_newColumn());
        addColumn.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_newColumnA11y());
        addColumn.setToolTipText(Bundle.TasksTopComponent_newColumnTip());
        addColumn.addActionListener(e -> newColumnDialog());
        overviewToggle = new javax.swing.JToggleButton(Bundle.TasksTopComponent_overview());
        overviewToggle.getAccessibleContext().setAccessibleName(
                Bundle.TasksTopComponent_overviewA11y());
        overviewToggle.setToolTipText(
                Bundle.TasksTopComponent_overviewTip());
        overviewToggle.addActionListener(e -> {
            faces.show(center, overviewToggle.isSelected() ? "overview" : "board");
            rebuild();
        });
        JButton standup = new JButton(Bundle.TasksTopComponent_standup());
        standup.getAccessibleContext().setAccessibleName(
                Bundle.TasksTopComponent_standupA11y());
        standup.setToolTipText(Bundle.TasksTopComponent_standupTip());
        standup.addActionListener(e -> showStandup());
        JButton sprint = new JButton(Bundle.TasksTopComponent_sprint());
        sprint.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_sprintA11y());
        sprint.setToolTipText(Bundle.TasksTopComponent_sprintTip());
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
        boardLabel.setText(PlainText.plain(boundDir == null ? " " : headerText()));
        columnsPanel.revalidate();
        columnsPanel.repaint();
        focusCardId = null; // consumed by the panels just built
    }

    /** The header line; the running clock's elapsed rides here so the
     *  30s ticker can refresh it WITHOUT rebuilding the strip (a rebuild
     *  would drop the list selection every tick). */
    private String headerText() {
        String base = Bundle.TasksTopComponent_header(boundDir.getName(),
                org.nmox.studio.core.util.Plural.of(board.cardCount(),
                        Bundle.TasksTopComponent_card(), Bundle.TasksTopComponent_cards()));
        TaskBoard.Card running = board.runningCard();
        if (running == null) {
            return base;
        }
        long since = running.sessions().get(running.sessions().size() - 1)[0];
        return Bundle.TasksTopComponent_headerRunning(base, running.title(),
                BoardStats.duration(System.currentTimeMillis() - since));
    }

    private JPanel columnPanel(int index, TaskBoard.Column col) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setPreferredSize(new Dimension(230, 100));
        panel.setMaximumSize(new Dimension(230, Integer.MAX_VALUE));
        panel.setBorder(BorderFactory.createEtchedBorder());

        String count = col.wipLimit() > 0
                ? Bundle.TasksTopComponent_countOfLimit(
                        String.valueOf(col.cards().size()), String.valueOf(col.wipLimit()))
                : String.valueOf(col.cards().size());
        JLabel header = new JLabel(PlainText.plain(
                Bundle.TasksTopComponent_columnHeader(col.name(), count)));
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        if (col.overLimit()) {
            header.setForeground(new Color(220, 80, 80)); // over WIP limit
        }
        header.getAccessibleContext().setAccessibleName(
                Bundle.TasksTopComponent_columnA11y(col.name(), count,
                        col.cards().size() == 1 ? Bundle.TasksTopComponent_card()
                                : Bundle.TasksTopComponent_cards(),
                        col.overLimit() ? Bundle.TasksTopComponent_overLimitSuffix() : ""));
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
        list.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_columnCardsA11y(col.name()));
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
                String head = c.blocked() ? Bundle.TasksTopComponent_blockedCard(c.title()) : c.title();
                if (c.clockedIn()) {
                    head = Bundle.TasksTopComponent_clockedCard(head);
                }
                String text = c.notes().isEmpty() ? head
                        : Bundle.TasksTopComponent_cardWithNotes(head, firstLine(c.notes()));
                setText(c.label().isEmpty() ? text
                        : Bundle.TasksTopComponent_cardWithLabel(text, c.label()));
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
        JMenuItem edit = new JMenuItem(Bundle.TasksTopComponent_edit());
        edit.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                editCardDialog(list.getSelectedValue());
            }
        });
        JMenuItem delete = new JMenuItem(Bundle.TasksTopComponent_delete());
        delete.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                confirmRemoveCard(list.getSelectedValue());
            }
        });
        JMenuItem label = new JMenuItem(Bundle.TasksTopComponent_setLabel());
        label.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                setLabelDialog(list.getSelectedValue());
            }
        });
        JMenuItem clockIn = new JMenuItem(Bundle.TasksTopComponent_clockIn());
        clockIn.addActionListener(e -> {
            TaskBoard.Card sel = list.getSelectedValue();
            if (sel == null) {
                return;
            }
            // ONE clock runs board-wide, so clocking in here clocks out
            // whatever was running — say so instead of moving it silently
            TaskBoard.Card was = board.runningCard();
            if (mutate(() -> board.clockIn(sel.id(), System.currentTimeMillis()))) {
                status(was == null ? Bundle.TasksTopComponent_clockedIn()
                        : Bundle.TasksTopComponent_clockedInStopped(was.title()));
            } else {
                status(Bundle.TasksTopComponent_clockAlreadyRunning());
            }
        });
        JMenuItem clockOut = new JMenuItem(Bundle.TasksTopComponent_clockOut());
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
                status(blip ? Bundle.TasksTopComponent_clockedOutBlip()
                        : Bundle.TasksTopComponent_clockedOut());
            } else {
                status(Bundle.TasksTopComponent_noClockRunning());
            }
        });
        JMenuItem block = new JMenuItem(Bundle.TasksTopComponent_markBlocked());
        block.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                blockDialog(list.getSelectedValue());
            }
        });
        JMenuItem unblock = new JMenuItem(Bundle.TasksTopComponent_unblock());
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
        title.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_cardTitleA11y());
        JTextArea notes = new JTextArea(5, 28);
        notes.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_cardNotesA11y());
        form.add(title, BorderLayout.NORTH);
        form.add(new JScrollPane(notes), BorderLayout.CENTER);
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        DialogDescriptor d = new DialogDescriptor(form, Bundle.TasksTopComponent_newCardTitle());
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION) {
            if (title.getText().strip().isEmpty()) {
                status(Bundle.TasksTopComponent_cardNeedsTitleNothingAdded());
                return;
            }
            mutate(() -> board.addCard(preferredColumn, title.getText(),
                    notes.getText()) != null);
        }
    }

    private void editCardDialog(TaskBoard.Card card) {
        JPanel form = new JPanel(new BorderLayout(0, 6));
        JTextField title = new JTextField(card.title(), 28);
        title.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_cardTitleA11y());
        JTextArea notes = new JTextArea(card.notes(), 5, 28);
        notes.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_cardNotesA11y());
        form.add(title, BorderLayout.NORTH);
        form.add(new JScrollPane(notes), BorderLayout.CENTER);
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        DialogDescriptor d = new DialogDescriptor(form, Bundle.TasksTopComponent_editCardTitle());
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION
                && !mutate(() -> board.editCard(card.id(), title.getText(),
                        notes.getText()))) {
            status(Bundle.TasksTopComponent_cardNeedsTitleUnchanged());
        }
    }

    private void confirmRemoveCard(TaskBoard.Card card) {
        // full ctor with NO as the initial value — a reflexive Enter must
        // not delete (v1.98.0)
        NotifyDescriptor d = new NotifyDescriptor(
                org.nmox.studio.core.util.PlainDialogs.plain(Bundle.TasksTopComponent_deleteCardQuestion(card.title()),
                        Bundle.TasksTopComponent_messageA11y()), Bundle.TasksTopComponent_deleteCardTitle(),
                NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.QUESTION_MESSAGE,
                null, NotifyDescriptor.NO_OPTION);
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.YES_OPTION) {
            mutate(() -> board.removeCard(card.id()));
        }
    }

    private void newColumnDialog() {
        NotifyDescriptor.InputLine in =
                new NotifyDescriptor.InputLine(Bundle.TasksTopComponent_nameLabel(), Bundle.TasksTopComponent_newColumnTitle());
        if (DialogDisplayer.getDefault().notify(in) == NotifyDescriptor.OK_OPTION
                && !mutate(() -> board.addColumn(in.getInputText(), 0))) {
            status(Bundle.TasksTopComponent_columnNeedsNameNothingAdded());
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
        text.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_standupReportA11y());
                text.setEditable(false);
                text.setCaretPosition(0);
                JScrollPane scroll = new JScrollPane(text);
                scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                JButton copy = new JButton(Bundle.TasksTopComponent_copyToClipboard());
                copy.getAccessibleContext().setAccessibleName(
                        Bundle.TasksTopComponent_copyStandupA11y());
                copy.addActionListener(ev -> {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer
                                    .StringSelection(text.getText()), null);
                    org.openide.awt.StatusDisplayer.getDefault()
                            .setStatusText(Bundle.TasksTopComponent_standupCopied());
                });
                DialogDescriptor d = new DialogDescriptor(scroll, Bundle.TasksTopComponent_standupTitle());
                d.setOptions(new Object[]{copy, NotifyDescriptor.CANCEL_OPTION});
                d.setClosingOptions(new Object[]{NotifyDescriptor.CANCEL_OPTION});
                DialogDisplayer.getDefault().notify(d);
            });
        });
    }

    // ---- the sprint ceremonies (v2.37.0, the scrum-master pass) ----------

    private void showSprintMenu(java.awt.Component owner) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem set = new javax.swing.JMenuItem(PlainText.plain(
                board.hasSprint() ? Bundle.TasksTopComponent_editSprint() : Bundle.TasksTopComponent_startSprint()));
        set.addActionListener(e -> editSprint());
        menu.add(set);
        javax.swing.JMenuItem report = new javax.swing.JMenuItem(Bundle.TasksTopComponent_sprintReport());
        report.setEnabled(board.hasSprint());
        report.addActionListener(e -> showSprintReport());
        menu.add(report);
        javax.swing.JMenuItem close = new javax.swing.JMenuItem(Bundle.TasksTopComponent_closeSprint());
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
        panel.add(new javax.swing.JLabel(Bundle.TasksTopComponent_sprintNameLabel()));
        panel.add(name);
        panel.add(new javax.swing.JLabel(Bundle.TasksTopComponent_sprintStartLabel()));
        panel.add(start);
        panel.add(new javax.swing.JLabel(Bundle.TasksTopComponent_sprintEndLabel()));
        panel.add(end);
        String velocity = SprintRoll.velocityLine(board.sprintHistory());
        if (velocity != null) {
            javax.swing.JLabel v = new javax.swing.JLabel(
                    Bundle.TasksTopComponent_velocityHtml(velocity));
            v.getAccessibleContext().setAccessibleName(velocity);
            panel.add(v);
            panel.add(new javax.swing.JLabel(""));
        }
        DialogDescriptor d = new DialogDescriptor(panel, Bundle.TasksTopComponent_sprintTitle());
        if (DialogDisplayer.getDefault().notify(d) != DialogDescriptor.OK_OPTION) {
            return;
        }
        java.time.LocalDate s0;
        java.time.LocalDate s1;
        try {
            s0 = java.time.LocalDate.parse(start.getText().strip());
            s1 = java.time.LocalDate.parse(end.getText().strip());
        } catch (java.time.format.DateTimeParseException bad) {
            status(Bundle.TasksTopComponent_sprintDatesInvalid());
            return;
        }
        if (name.getText().isBlank()) {
            status(Bundle.TasksTopComponent_sprintNeedsName());
            return;
        }
        if (s1.isBefore(s0)) {
            status(Bundle.TasksTopComponent_sprintEndsBeforeStart());
            return;
        }
        long startMs = s0.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = s1.atStartOfDay(zone).toInstant().toEpochMilli();
        String n = name.getText().strip();
        mutate(() -> {
            board.setSprint(n, startMs, endMs);
            return true;
        });
        status(Bundle.TasksTopComponent_sprintSet(n, s0.toString(), s1.toString()));
    }

    private void showSprintReport() {
        String md = SprintReport.build(board, System.currentTimeMillis(),
                java.time.ZoneId.systemDefault());
        if (md.isEmpty()) {
            return;
        }
        JTextArea text = new JTextArea(md, 18, 52);
        text.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_sprintReportA11y());
        text.setEditable(false);
        text.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton copy = new JButton(Bundle.TasksTopComponent_copyToClipboard());
        copy.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_copySprintReportA11y());
        copy.addActionListener(ev -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer
                            .StringSelection(text.getText()), null);
            org.openide.awt.StatusDisplayer.getDefault()
                    .setStatusText(Bundle.TasksTopComponent_sprintReportCopied());
        });
        DialogDescriptor d = new DialogDescriptor(scroll, Bundle.TasksTopComponent_sprintReportTitle());
        d.setOptions(new Object[]{copy, NotifyDescriptor.CANCEL_OPTION});
        d.setClosingOptions(new Object[]{NotifyDescriptor.CANCEL_OPTION});
        DialogDisplayer.getDefault().notify(d);
    }

    private void closeSprint() {
        // closing archives and clears — irreversible bookkeeping, so the
        // reflexive Enter lands on No (the v1.98.0 safe default)
        NotifyDescriptor confirm = new NotifyDescriptor(
                org.nmox.studio.core.util.PlainDialogs.plain(
                        Bundle.TasksTopComponent_closeSprintQuestion(board.sprintName()),
                        Bundle.TasksTopComponent_messageA11y()),
                Bundle.TasksTopComponent_closeSprintTitle(), NotifyDescriptor.YES_NO_OPTION,
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
            status(Bundle.TasksTopComponent_sprintClosed(out[0].name(),
                    String.valueOf(out[0].done())));
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
                    Bundle.TasksTopComponent_nextSprintQuestion(),
                    Bundle.TasksTopComponent_nextSprintTitle(), NotifyDescriptor.YES_NO_OPTION,
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
        text.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_retroNotesA11y());
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        DialogDescriptor d = new DialogDescriptor(scroll,
                Bundle.TasksTopComponent_retroTitle());
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION) {
            mutate(() -> { board.setRetro(text.getText()); return true; });
        }
    }

    private void setLabelDialog(TaskBoard.Card card) {
        NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine(
                Bundle.TasksTopComponent_labelPrompt(), Bundle.TasksTopComponent_setLabelTitle());
        in.setInputText(card.label());
        if (DialogDisplayer.getDefault().notify(in) == NotifyDescriptor.OK_OPTION) {
            mutate(() -> board.setLabel(card.id(), in.getInputText()));
        }
    }

    private void blockDialog(TaskBoard.Card card) {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        JTextField owner = new JTextField(card.blockOwner(), 28);
        owner.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_blockerOwnerA11y());
        JTextField action = new JTextField(card.blockAction(), 28);
        action.getAccessibleContext().setAccessibleName(Bundle.TasksTopComponent_unblockActionA11y());
        form.add(new JLabel(Bundle.TasksTopComponent_ownerLabel()));
        form.add(owner);
        form.add(new JLabel(Bundle.TasksTopComponent_unblockActionLabel()));
        form.add(action);
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        DialogDescriptor d = new DialogDescriptor(form, Bundle.TasksTopComponent_markBlockedTitle());
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION) {
            if (action.getText().strip().isEmpty()) {
                org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                        Bundle.TasksTopComponent_blockerNeedsAction());
                return;
            }
            mutate(() -> board.block(card.id(), owner.getText(),
                    action.getText()));
        }
    }

    private JPopupMenu columnMenu(int index) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem(Bundle.TasksTopComponent_rename());
        rename.addActionListener(e -> {
            NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine(
                    Bundle.TasksTopComponent_nameLabel(), Bundle.TasksTopComponent_renameColumnTitle());
            in.setInputText(board.column(index).name());
            if (DialogDisplayer.getDefault().notify(in) == NotifyDescriptor.OK_OPTION
                    && !mutate(() -> board.renameColumn(index, in.getInputText()))) {
                status(Bundle.TasksTopComponent_columnNeedsNameKept(
                        board.column(index).name()));
            }
        });
        JMenuItem wip = new JMenuItem(Bundle.TasksTopComponent_setWipLimit());
        wip.addActionListener(e -> {
            NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine(
                    Bundle.TasksTopComponent_wipLimitPrompt(), Bundle.TasksTopComponent_wipLimitTitle());
            in.setInputText(String.valueOf(board.column(index).wipLimit()));
            if (DialogDisplayer.getDefault().notify(in) == NotifyDescriptor.OK_OPTION) {
                try {
                    int limit = Integer.parseInt(in.getInputText().strip());
                    mutate(() -> board.setWipLimit(index, limit));
                } catch (NumberFormatException ignore) {
                    // not a number: keep the limit, and say so — a swallowed
                    // gesture reads as a broken dialog
                    status(Bundle.TasksTopComponent_wipLimitNotNumber(
                            String.valueOf(board.column(index).wipLimit())));
                }
            }
        });
        JMenuItem left = new JMenuItem(Bundle.TasksTopComponent_moveLeft());
        left.addActionListener(e -> {
            if (!mutate(() -> board.moveColumn(index, index - 1))) {
                status(Bundle.TasksTopComponent_alreadyFirst());
            }
        });
        JMenuItem right = new JMenuItem(Bundle.TasksTopComponent_moveRight());
        right.addActionListener(e -> {
            if (!mutate(() -> board.moveColumn(index, index + 1))) {
                status(Bundle.TasksTopComponent_alreadyLast());
            }
        });
        JMenuItem remove = new JMenuItem(Bundle.TasksTopComponent_deleteColumn());
        remove.addActionListener(e -> {
            int n = board.column(index).cards().size();
            NotifyDescriptor d = new NotifyDescriptor(
                    org.nmox.studio.core.util.PlainDialogs.plain(n == 0
                            ? Bundle.TasksTopComponent_deleteEmptyColumnQuestion()
                            : Bundle.TasksTopComponent_deleteColumnWithCardsQuestion(String.valueOf(n)),
                            Bundle.TasksTopComponent_messageA11y()),
                    Bundle.TasksTopComponent_deleteColumnTitle(), NotifyDescriptor.YES_NO_OPTION,
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
                        Bundle.TasksTopComponent_boardKeepsOneColumn());
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
