package org.nmox.studio.ui.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Board Overview's numbers, computed pure so every claim the
 * dashboard makes is a plain unit test (v2.4.0). Definitions, stated
 * once so the tiles never argue with each other:
 *
 * <ul>
 *   <li><b>WIP now</b> — cards in neither the FIRST nor the LAST
 *       column. On a classic To Do / Doing / Done board that is exactly
 *       "Doing"; a one- or two-column board has no middle, so WIP is 0
 *       and the tile says so honestly.</li>
 *   <li><b>Done today / this week</b> — cards whose done stamp falls on
 *       the current calendar day / within the last 7 calendar days
 *       (today inclusive), in the given zone. Calendar days, not
 *       rolling 24h windows: "today" should mean what the user's clock
 *       says.</li>
 *   <li><b>Flow</b> — one bin per calendar day, oldest first, counting
 *       done stamps. A card moved OUT of Done lost its stamp, so the
 *       history never counts work that was un-finished.</li>
 *   <li><b>Oldest active</b> — cards outside the last column, oldest
 *       created first. Age is whole days. Cards with no created stamp
 *       (a hand-edited file) sort last rather than faking an age.</li>
 * </ul>
 */
final class BoardStats {

    /** One column's overview row. */
    record ColumnStat(String name, int count, int wipLimit, boolean overLimit) {
    }

    /** One aging card on the overview's attention list. */
    record AgingCard(String title, String column, long ageDays) {
    }

    /** One row of the blocker register (v2.5.0). */
    record Blocker(String title, String owner, String action, long sinceDays) {
    }

    /** One epic label and how many cards wear it. */
    record LabelCount(String label, int count) {
    }

    /** One row of the time report (v2.6.0): tracked work per card. */
    record TimeEntry(String title, long todayMs, long weekMs, boolean running) {
    }

    private final int totalCards;
    private final int wipNow;
    private final int doneToday;
    private final int doneThisWeek;
    private final List<ColumnStat> columnStats;
    private final int[] flow;
    private final List<AgingCard> oldestActive;
    private final List<Blocker> blockers;
    private final List<LabelCount> labels;
    private final List<TimeEntry> timeEntries;
    private final long trackedTodayMs;
    private final long trackedWeekMs;

    private BoardStats(int totalCards, int wipNow, int doneToday,
            int doneThisWeek, List<ColumnStat> columnStats, int[] flow,
            List<AgingCard> oldestActive, List<Blocker> blockers,
            List<LabelCount> labels, List<TimeEntry> timeEntries,
            long trackedTodayMs, long trackedWeekMs) {
        this.totalCards = totalCards;
        this.wipNow = wipNow;
        this.doneToday = doneToday;
        this.doneThisWeek = doneThisWeek;
        this.columnStats = columnStats;
        this.flow = flow;
        this.oldestActive = oldestActive;
        this.blockers = blockers;
        this.labels = labels;
        this.timeEntries = timeEntries;
        this.trackedTodayMs = trackedTodayMs;
        this.trackedWeekMs = trackedWeekMs;
    }

    int totalCards() {
        return totalCards;
    }

    int wipNow() {
        return wipNow;
    }

    int doneToday() {
        return doneToday;
    }

    int doneThisWeek() {
        return doneThisWeek;
    }

    List<ColumnStat> columnStats() {
        return columnStats;
    }

    /** Done-per-day bins, oldest day first, {@code days} entries. */
    int[] flow() {
        return flow.clone();
    }

    List<AgingCard> oldestActive() {
        return oldestActive;
    }

    /** Blocked cards outside the last column, longest-stuck first. A
     *  blocked card that reached Done left the register: finishing IS
     *  the unblock the register was tracking. */
    List<Blocker> blockers() {
        return blockers;
    }

    int blockedCount() {
        return blockers.size();
    }

    /** Every label in use with its card count, busiest first, ties by
     *  name — the overview's derived epic legend. */
    List<LabelCount> labels() {
        return labels;
    }

    /** Cards with tracked time in the last 7 days, most-today first,
     *  ties by week total. A running session counts up to NOW, and a
     *  session spanning midnight is CLIPPED per day — today's number is
     *  today's work, not the whole session. */
    List<TimeEntry> timeEntries() {
        return timeEntries;
    }

    long trackedTodayMs() {
        return trackedTodayMs;
    }

    long trackedWeekMs() {
        return trackedWeekMs;
    }

    /** "1h 05m" / "45m" / "<1m" — the report's one duration spelling. */
    static String duration(long ms) {
        long minutes = ms / 60_000L;
        if (minutes < 1) {
            return "<1m";
        }
        long h = minutes / 60;
        long m = minutes % 60;
        return h > 0 ? String.format("%dh %02dm", h, m) : m + "m";
    }

    /** Overlap of [aStart,aEnd) with [bStart,bEnd), never negative. */
    private static long overlap(long aStart, long aEnd, long bStart, long bEnd) {
        return Math.max(0L, Math.min(aEnd, bEnd) - Math.max(aStart, bStart));
    }

    /**
     * Computes the overview for {@code board} as of {@code nowMillis} in
     * {@code zone}, with a {@code flowDays}-day history and at most
     * {@code agingLimit} rows on the attention list.
     */
    static BoardStats of(TaskBoard board, long nowMillis, ZoneId zone,
            int flowDays, int agingLimit) {
        List<TaskBoard.Column> cols = board.columns();
        int last = cols.size() - 1;
        LocalDate today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone);

        int total = 0;
        int wip = 0;
        int dToday = 0;
        int dWeek = 0;
        int[] bins = new int[Math.max(1, flowDays)];
        List<ColumnStat> stats = new ArrayList<>();
        List<AgingCard> aging = new ArrayList<>();
        List<Blocker> blockers = new ArrayList<>();
        java.util.Map<String, Integer> labelCounts = new java.util.TreeMap<>();
        List<TimeEntry> time = new ArrayList<>();
        long todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long weekStart = today.minusDays(6).atStartOfDay(zone)
                .toInstant().toEpochMilli();
        long trackedToday = 0;
        long trackedWeek = 0;

        for (int i = 0; i < cols.size(); i++) {
            TaskBoard.Column col = cols.get(i);
            List<TaskBoard.Card> cards = col.cards();
            total += cards.size();
            stats.add(new ColumnStat(col.name(), cards.size(),
                    col.wipLimit(), col.overLimit()));
            if (i > 0 && i < last) {
                wip += cards.size();
            }
            for (TaskBoard.Card c : cards) {
                if (!c.label().isEmpty()) {
                    labelCounts.merge(c.label(), 1, Integer::sum);
                }
                long cardToday = 0;
                long cardWeek = 0;
                for (long[] sn : c.sessions()) {
                    long end = sn[1] == 0L ? nowMillis : sn[1];
                    cardToday += overlap(sn[0], end, todayStart, nowMillis);
                    cardWeek += overlap(sn[0], end, weekStart, nowMillis);
                }
                if (cardWeek > 0 || c.clockedIn()) {
                    time.add(new TimeEntry(c.title(), cardToday, cardWeek,
                            c.clockedIn()));
                    trackedToday += cardToday;
                    trackedWeek += cardWeek;
                }
                if (c.blocked() && i < last) {
                    blockers.add(new Blocker(c.title(), c.blockOwner(),
                            c.blockAction(),
                            c.blockedSince() > 0L
                                    ? Math.max(0, (nowMillis - c.blockedSince())
                                            / 86_400_000L)
                                    : 0));
                }
                if (c.done() > 0L) {
                    LocalDate doneDay = LocalDate.ofInstant(
                            Instant.ofEpochMilli(c.done()), zone);
                    long back = today.toEpochDay() - doneDay.toEpochDay();
                    if (back == 0) {
                        dToday++;
                    }
                    if (back >= 0 && back < 7) {
                        dWeek++;
                    }
                    if (back >= 0 && back < bins.length) {
                        bins[bins.length - 1 - (int) back]++;
                    }
                } else if (i < last) {
                    aging.add(new AgingCard(c.title(), col.name(),
                            c.created() > 0L
                                    ? Math.max(0, (nowMillis - c.created())
                                            / 86_400_000L)
                                    : -1));
                }
            }
        }
        // oldest first; stamp-less cards (age -1) go LAST — no faked ages
        aging.sort(Comparator.comparingLong(a -> a.ageDays() < 0
                ? Long.MAX_VALUE : -a.ageDays()));
        if (aging.size() > agingLimit) {
            aging = new ArrayList<>(aging.subList(0, agingLimit));
        }
        blockers.sort(Comparator.comparingLong(b -> -b.sinceDays()));
        List<LabelCount> labels = new ArrayList<>();
        labelCounts.forEach((l, n) -> labels.add(new LabelCount(l, n)));
        labels.sort(Comparator.comparingInt((LabelCount l) -> -l.count())
                .thenComparing(LabelCount::label));
        time.sort(Comparator.comparingLong((TimeEntry t) -> -t.todayMs())
                .thenComparingLong(t -> -t.weekMs()));
        return new BoardStats(total, wip, dToday, dWeek,
                List.copyOf(stats), bins, List.copyOf(aging),
                List.copyOf(blockers), List.copyOf(labels),
                List.copyOf(time), trackedToday, trackedWeek);
    }
}
