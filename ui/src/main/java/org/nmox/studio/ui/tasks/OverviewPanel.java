package org.nmox.studio.ui.tasks;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.ZoneId;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.nmox.studio.core.util.PlainTables;

/**
 * The Task Board's OVERVIEW face (v2.4.0, David's ask): the sprint-
 * dashboard read of the same {@code .nmoxtasks.json} the kanban strip
 * edits — a tile row (cards, WIP now, done today, done this week), a
 * per-column WIP register with the v1.323.0 advisory-limit verdicts, a
 * painted N-day flow strip fed by the done stamps, and an attention
 * list of the oldest unfinished cards.
 *
 * <p>Every number comes from {@link BoardStats}, the pure core — this
 * panel is only rendering, in the product's phosphor idiom (dark
 * ground, green headings). Card and column names are external text
 * (a checked-in board arrives with clones), so every label routes
 * through {@link PlainTables#plain} — the v1.311.0 law.
 */
final class OverviewPanel extends JPanel {

    static final Color GROUND = new Color(0x10, 0x14, 0x10);
    static final Color PANEL = new Color(0x16, 0x1c, 0x16);
    static final Color EDGE = new Color(0x2a, 0x36, 0x2a);
    static final Color PHOSPHOR = new Color(0x7c, 0xe3, 0x8b);
    static final Color DIM = new Color(0x8a, 0x9a, 0x8a);
    static final Color TEXT = new Color(0xd8, 0xe2, 0xd8);
    static final Color OVER = new Color(0xdc, 0x50, 0x50);

    private static final int FLOW_DAYS = 14;
    private static final int AGING_ROWS = 5;

    /** Invoked by the Edit Retro button; the window supplies the
     *  dialog and routes the change through its one mutate() path. */
    private final Runnable editRetro;

    OverviewPanel(Runnable editRetro) {
        this.editRetro = editRetro;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(GROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        getAccessibleContext().setAccessibleName("Board overview");
    }

    /** Rebuilds the whole face from the board. Cheap at kanban scale. */
    void show(TaskBoard board, String projectName) {
        removeAll();
        BoardStats s = BoardStats.of(board, System.currentTimeMillis(),
                ZoneId.systemDefault(), FLOW_DAYS, AGING_ROWS);

        add(heading(projectName == null ? "BOARD OVERVIEW"
                : "BOARD OVERVIEW · " + projectName));
        add(Box.createVerticalStrut(8));

        if (board.hasSprint()) {
            BoardStats.Burndown burn = BoardStats.burndown(board,
                    System.currentTimeMillis(), ZoneId.systemDefault());
            int day = burn.remainingPerDay().size();
            add(sectionLabel("SPRINT " + board.sprintName().toUpperCase(java.util.Locale.ROOT)
                    + " — day " + day + " of " + burn.totalDays()
                    + " · " + burn.committed() + " committed · "
                    + (burn.remainingPerDay().isEmpty() ? 0
                            : burn.remainingPerDay().get(day - 1)) + " remaining"));
            add(Box.createVerticalStrut(4));
            BurndownStrip strip = new BurndownStrip(burn);
            strip.setAlignmentX(LEFT_ALIGNMENT);
            add(strip);
            add(Box.createVerticalStrut(10));
        }

        JPanel tiles = new JPanel(new GridLayout(1, 5, 8, 0));
        tiles.setOpaque(false);
        tiles.setAlignmentX(LEFT_ALIGNMENT);
        tiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        tiles.add(tile(String.valueOf(s.totalCards()), "CARDS ON THE BOARD"));
        tiles.add(tile(String.valueOf(s.wipNow()),
                "WIP NOW (MIDDLE COLUMNS)"));
        tiles.add(tile(String.valueOf(s.doneToday()), "DONE TODAY"));
        tiles.add(tile(String.valueOf(s.doneThisWeek()), "DONE THIS WEEK"));
        JComponent blockedTile = tile(String.valueOf(s.blockedCount()),
                "BLOCKED");
        if (s.blockedCount() > 0) {
            // the one number on the board that should read as an alarm
            ((JLabel) ((JPanel) blockedTile).getComponent(0))
                    .setForeground(OVER);
        }
        tiles.add(blockedTile);
        add(tiles);
        add(Box.createVerticalStrut(10));

        add(sectionLabel("COLUMNS & WIP — limits are advisory; red means over"));
        add(Box.createVerticalStrut(4));
        JPanel cols = new JPanel();
        cols.setLayout(new BoxLayout(cols, BoxLayout.Y_AXIS));
        cols.setOpaque(false);
        cols.setAlignmentX(LEFT_ALIGNMENT);
        int max = 1;
        for (BoardStats.ColumnStat c : s.columnStats()) {
            max = Math.max(max, c.count());
        }
        for (BoardStats.ColumnStat c : s.columnStats()) {
            cols.add(columnRow(c, max));
            cols.add(Box.createVerticalStrut(3));
        }
        add(cols);
        add(Box.createVerticalStrut(10));

        add(sectionLabel("FLOW — cards finished per day, last "
                + FLOW_DAYS + " days"));
        add(Box.createVerticalStrut(4));
        FlowStrip flow = new FlowStrip(s.flow());
        flow.setAlignmentX(LEFT_ALIGNMENT);
        add(flow);
        add(Box.createVerticalStrut(10));

        add(sectionLabel("BLOCKER REGISTER — every blocker has an owner"
                + " and an unblock action"));
        add(Box.createVerticalStrut(4));
        if (s.blockers().isEmpty()) {
            JLabel none = PlainTables.plain(new JLabel(
                    "No blocked cards — nothing is waiting on anyone."));
            none.setForeground(DIM);
            add(none);
        } else {
            for (BoardStats.Blocker b : s.blockers()) {
                add(blockerRow(b));
                add(Box.createVerticalStrut(2));
            }
        }
        add(Box.createVerticalStrut(10));

        if (!s.timeEntries().isEmpty()) {
            add(sectionLabel("TIME — clocked today "
                    + BoardStats.duration(s.trackedTodayMs())
                    + " · last 7 days "
                    + BoardStats.duration(s.trackedWeekMs())));
            add(Box.createVerticalStrut(4));
            for (BoardStats.TimeEntry t : s.timeEntries()) {
                add(timeRow(t));
                add(Box.createVerticalStrut(2));
            }
            add(Box.createVerticalStrut(10));
        }

        if (!s.labels().isEmpty()) {
            add(sectionLabel("EPICS — labels in use, busiest first"));
            add(Box.createVerticalStrut(4));
            JPanel legend = new JPanel(
                    new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 14, 2));
            legend.setOpaque(false);
            legend.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            for (BoardStats.LabelCount lc : s.labels()) {
                legend.add(legendChip(lc));
            }
            add(legend);
            add(Box.createVerticalStrut(10));
        }

        add(sectionLabel("NEEDS ATTENTION — oldest unfinished cards"));
        add(Box.createVerticalStrut(4));
        if (s.oldestActive().isEmpty()) {
            JLabel none = PlainTables.plain(new JLabel(
                    "Nothing waiting — the board is clear."));
            none.setForeground(DIM);
            none.setAlignmentX(LEFT_ALIGNMENT);
            add(none);
        } else {
            for (BoardStats.AgingCard a : s.oldestActive()) {
                add(agingRow(a));
                add(Box.createVerticalStrut(2));
            }
        }
        add(Box.createVerticalStrut(10));
        JPanel retroHead = new JPanel(new BorderLayout(8, 0));
        retroHead.setOpaque(false);
        retroHead.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        retroHead.add(sectionLabel("RETRO — what went well, what bit us,"
                + " what changed"), BorderLayout.WEST);
        javax.swing.JButton edit = new javax.swing.JButton("Edit Retro…");
        edit.getAccessibleContext().setAccessibleName("Edit retro notes");
        edit.setFont(mono(Font.PLAIN, 10f));
        edit.addActionListener(e -> editRetro.run());
        retroHead.add(edit, BorderLayout.EAST);
        add(retroHead);
        add(Box.createVerticalStrut(4));
        String retro = board.retro();
        if (retro.isEmpty()) {
            JLabel none = PlainTables.plain(new JLabel(
                    "No retro notes yet — Edit Retro… starts them."));
            none.setForeground(DIM);
            add(none);
        } else {
            javax.swing.JTextArea text = new javax.swing.JTextArea(retro);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setOpaque(false);
            text.setForeground(TEXT);
            text.setFont(mono(Font.PLAIN, 12f));
            text.getAccessibleContext().setAccessibleName("Retro notes");
            add(text);
        }
        add(Box.createVerticalGlue());
        revalidate();
        repaint();
    }

    private JComponent blockerRow(BoardStats.Blocker b) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel title = PlainTables.plain(new JLabel(clip(b.title())));
        title.setForeground(OVER);
        title.setFont(mono(Font.PLAIN, 12f));
        String owner = b.owner().isEmpty() ? "unowned" : b.owner();
        JLabel meta = PlainTables.plain(new JLabel(
                owner + " · " + b.sinceDays() + "d · " + clip(b.action())));
        meta.setForeground(DIM);
        meta.setFont(mono(Font.PLAIN, 11f));
        row.add(title, BorderLayout.WEST);
        row.add(meta, BorderLayout.EAST);
        row.getAccessibleContext().setAccessibleName("Blocked: " + b.title()
                + ", owner " + owner + ", " + b.sinceDays()
                + " days, unblock: " + b.action());
        return row;
    }

    private JComponent timeRow(BoardStats.TimeEntry t) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel title = PlainTables.plain(new JLabel(
                (t.running() ? "\u23f1 " : "") + clip(t.title())));
        title.setForeground(t.running() ? PHOSPHOR : TEXT);
        title.setFont(mono(Font.PLAIN, 12f));
        JLabel meta = PlainTables.plain(new JLabel(
                "today " + BoardStats.duration(t.todayMs())
                + " · week " + BoardStats.duration(t.weekMs())));
        meta.setForeground(DIM);
        meta.setFont(mono(Font.PLAIN, 11f));
        row.add(title, BorderLayout.WEST);
        row.add(meta, BorderLayout.EAST);
        row.getAccessibleContext().setAccessibleName("Time on " + t.title()
                + (t.running() ? ", clock running" : "")
                + ": today " + BoardStats.duration(t.todayMs())
                + ", week " + BoardStats.duration(t.weekMs()));
        return row;
    }

    private JComponent legendChip(BoardStats.LabelCount lc) {
        JPanel chip = new JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        chip.setOpaque(false);
        JPanel dot = new JPanel();
        dot.setBackground(labelColor(lc.label()));
        dot.setPreferredSize(new Dimension(9, 9));
        JLabel text = PlainTables.plain(new JLabel(
                lc.label() + " — " + lc.count()));
        text.setForeground(TEXT);
        text.setFont(mono(Font.PLAIN, 11f));
        chip.add(dot);
        chip.add(text);
        chip.getAccessibleContext().setAccessibleName(
                "Epic " + lc.label() + ", " + org.nmox.studio.core.util.Plural.of(lc.count(), "card"));
        return chip;
    }

    /** A stable per-label hue — same recipe family as IRC nick colors.
     *  floorMod, not Math.abs: abs(Integer.MIN_VALUE) is still negative
     *  (SpotBugs RV_ABSOLUTE_VALUE_OF_HASHCODE, caught by the verify). */
    static Color labelColor(String label) {
        float hue = Math.floorMod(label.hashCode(), 360) / 360f;
        return Color.getHSBColor(hue, 0.55f, 0.85f);
    }

    // ---- pieces ----------------------------------------------------------

    private JLabel heading(String text) {
        JLabel l = PlainTables.plain(new JLabel(text));
        l.setForeground(PHOSPHOR);
        l.setFont(mono(Font.BOLD, 15f));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = PlainTables.plain(new JLabel(text));
        l.setForeground(DIM);
        l.setFont(mono(Font.BOLD, 11f));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JComponent tile(String number, String caption) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EDGE),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JLabel big = new JLabel(number);
        big.setForeground(PHOSPHOR);
        big.setFont(mono(Font.BOLD, 26f));
        JLabel cap = new JLabel(caption);
        cap.setForeground(DIM);
        cap.setFont(mono(Font.PLAIN, 10f));
        p.add(big, BorderLayout.CENTER);
        p.add(cap, BorderLayout.SOUTH);
        p.getAccessibleContext().setAccessibleName(caption + ": " + number);
        return p;
    }

    private JComponent columnRow(BoardStats.ColumnStat c, int maxCount) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        String count = c.wipLimit() > 0
                ? c.count() + " / " + c.wipLimit() : String.valueOf(c.count());
        JLabel name = PlainTables.plain(new JLabel(c.name()));
        name.setForeground(c.overLimit() ? OVER : TEXT);
        name.setFont(mono(Font.PLAIN, 12f));
        name.setPreferredSize(new Dimension(160, 18));
        JLabel n = new JLabel(count + (c.overLimit() ? "  OVER" : ""));
        n.setForeground(c.overLimit() ? OVER : DIM);
        n.setFont(mono(Font.PLAIN, 12f));
        n.setPreferredSize(new Dimension(90, 18));
        Bar bar = new Bar(c.count(), maxCount, c.overLimit());
        row.add(name, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        row.add(n, BorderLayout.EAST);
        row.getAccessibleContext().setAccessibleName(
                "Column " + c.name() + ", " + count
                + (c.overLimit() ? ", over limit" : ""));
        return row;
    }

    private JComponent agingRow(BoardStats.AgingCard a) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel title = PlainTables.plain(new JLabel(clip(a.title())));
        title.setForeground(TEXT);
        title.setFont(mono(Font.PLAIN, 12f));
        String tail = a.ageDays() < 0 ? a.column()
                : a.column() + " · " + a.ageDays() + "d";
        JLabel meta = PlainTables.plain(new JLabel(tail));
        meta.setForeground(DIM);
        meta.setFont(mono(Font.PLAIN, 11f));
        row.add(title, BorderLayout.CENTER);
        row.add(meta, BorderLayout.EAST);
        row.getAccessibleContext().setAccessibleName(
                "Aging card " + a.title() + ", " + tail);
        return row;
    }

    private static String clip(String s) {
        // display clip only, code-point safe (the v1.287.0 surrogate law)
        int max = 70;
        if (s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, max)) + "…";
    }

    private static Font mono(int style, float size) {
        return new Font(Font.MONOSPACED, style, Math.round(size));
    }

    /** One column's count bar; red when over its advisory limit.
     *  A JPanel, not a bare JComponent: JComponent has NO accessible
     *  context of its own, and the walk found the null the hard way. */
    private static final class Bar extends JPanel {
        private final int count;
        private final int max;
        private final boolean over;

        Bar(int count, int max, boolean over) {
            setOpaque(false);
            this.count = count;
            this.max = Math.max(1, max);
            this.over = over;
            setPreferredSize(new Dimension(80, 14));
            getAccessibleContext().setAccessibleName(
                    count + " of " + this.max + (over ? ", over limit" : ""));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int h = 8;
            int y = (getHeight() - h) / 2;
            g2.setColor(PANEL);
            g2.fillRoundRect(0, y, getWidth(), h, h, h);
            int w = (int) Math.round((double) getWidth() * count / max);
            g2.setColor(over ? OVER : PHOSPHOR);
            g2.fillRoundRect(0, y, Math.max(count > 0 ? 4 : 0, w), h, h, h);
            g2.dispose();
        }
    }

    /** The N-day painted flow strip: one bar per day, oldest first.
     *  JPanel for the same accessible-context reason as Bar. */
    /**
     * The burndown (v2.37.0): remaining-per-day as a phosphor line over
     * the ideal straight line to zero. A JPanel like every painted
     * widget here — the v2.4.0 walk law: bare JComponent has a NULL
     * AccessibleContext, JPanel does not.
     */
    private static final class BurndownStrip extends JPanel {

        private final BoardStats.Burndown burn;

        BurndownStrip(BoardStats.Burndown burn) {
            this.burn = burn;
            setOpaque(false);
            setPreferredSize(new Dimension(100, 72));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            getAccessibleContext().setAccessibleName("Sprint burndown");
            getAccessibleContext().setAccessibleDescription(
                    burn.committed() + " committed, "
                    + (burn.remainingPerDay().isEmpty() ? 0
                            : burn.remainingPerDay().get(burn.remainingPerDay().size() - 1))
                    + " remaining on day " + burn.remainingPerDay().size()
                    + " of " + burn.totalDays());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(PANEL);
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.setColor(EDGE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            int top = 8;
            int bottom = h - 8;
            int left = 8;
            int right = w - 8;
            int peak = Math.max(1, burn.committed());
            int days = Math.max(1, burn.totalDays());
            // the ideal line: committed at day 0 straight to zero at the end
            g2.setColor(DIM);
            g2.drawLine(left, y(top, bottom, peak, peak),
                    right, y(top, bottom, 0, peak));
            // the real line, day by day
            g2.setColor(PHOSPHOR);
            java.util.List<Integer> rem = burn.remainingPerDay();
            int prevX = left;
            int prevY = y(top, bottom, peak, peak);
            for (int i = 0; i < rem.size(); i++) {
                int x = left + (right - left) * (i + 1) / days;
                int yy = y(top, bottom, rem.get(i), peak);
                g2.drawLine(prevX, prevY, x, yy);
                g2.fillOval(x - 2, yy - 2, 5, 5);
                prevX = x;
                prevY = yy;
            }
            g2.dispose();
        }

        private static int y(int top, int bottom, int value, int peak) {
            return bottom - (bottom - top) * value / peak;
        }
    }

    private static final class FlowStrip extends JPanel {
        private final int[] bins;
        private final int max;

        FlowStrip(int[] bins) {
            setOpaque(false);
            this.bins = bins;
            int m = 1;
            for (int b : bins) {
                m = Math.max(m, b);
            }
            this.max = m;
            setPreferredSize(new Dimension(200, 46));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            int total = 0;
            for (int b : bins) {
                total += b;
            }
            getAccessibleContext().setAccessibleName(
                    "Flow: " + org.nmox.studio.core.util.Plural.of(total, "card") + " finished in the last "
                    + bins.length + " days");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int n = bins.length;
            int gap = 4;
            int bw = Math.max(4, (getWidth() - gap * (n - 1)) / n);
            int floor = getHeight() - 12;
            for (int i = 0; i < n; i++) {
                int x = i * (bw + gap);
                int h = bins[i] == 0 ? 2
                        : Math.max(4, (floor - 4) * bins[i] / max);
                g2.setColor(bins[i] == 0 ? EDGE : PHOSPHOR);
                g2.fillRoundRect(x, floor - h, bw, h, 3, 3);
                if (bins[i] > 0) {
                    g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
                    g2.setColor(DIM);
                    g2.drawString(String.valueOf(bins[i]), x, getHeight() - 2);
                }
            }
            g2.dispose();
        }
    }

    // components inside a BoxLayout keep their alignment honest
    @Override
    public Component add(Component comp) {
        if (comp instanceof JComponent jc) {
            jc.setAlignmentX(LEFT_ALIGNMENT);
        }
        return super.add(comp);
    }
}
