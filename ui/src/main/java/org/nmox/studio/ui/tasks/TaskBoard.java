package org.nmox.studio.ui.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The Task Board's pure model (v1.323.0): an ordered list of columns,
 * each an ordered list of cards. UI-free on purpose — every rule a
 * kanban lives by (moves clamp to real positions, WIP limits are
 * advisory counts not hard blocks, a column deletion says what happens
 * to its cards) is a plain unit test here, and the window is only
 * rendering.
 *
 * <p>Persisted as {@code .nmoxtasks.json} beside the project — the
 * sixth per-project studio file, so the whole studio-law family
 * applies: atomic writes, self-write discrimination, .bak-before-
 * fallback on corrupt input, and re-aim follows the project. The board
 * belongs to the project the way {@code .nmoxrack.json} does: check it
 * in and the team shares it, ignore it and it stays personal.
 *
 * <p>Card titles and notes render through a PLAIN renderer in the
 * window (the v1.311.0 law): a checked-in tasks file arrives from a
 * cloned repository, which makes every string here external text — a
 * {@code <html><img src>} title must paint as characters, never fetch.
 */
public final class TaskBoard {

    /** One card: identity, what to do, and any longer notes. */
    public static final class Card {
        private final String id;
        private String title;
        private String notes;
        private final long created;
        /**
         * When the card ENTERED the board's last column (0 = it hasn't).
         * The stamp is the overview's flow history: set on a move or add
         * into the last column, cleared on a move back out. Column
         * reorders leave existing stamps alone — they record a moment
         * that really happened, not the board's current shape (v2.4.0).
         */
        private long done;
        /** Epic/category label, "" = none. Free text; the overview's
         *  legend derives itself from the distinct labels in use. */
        private String label = "";
        /** The blocker triple (v2.5.0): a card is blocked when
         *  {@code blockAction} is non-empty. The action says what
         *  unblocks it, the owner is who is on the hook, and since is
         *  auto-stamped at block time. Unblocking clears all three. */
        private String blockOwner = "";
        private String blockAction = "";
        private long blockedSince;
        /** Work sessions (v2.6.0): [start, end] pairs, end 0 while the
         *  clock runs. At most ONE session on the whole board is open —
         *  clocking in anywhere clocks out whatever was running. */
        private final List<long[]> sessions = new ArrayList<>();

        /**
         * Only the four REQUIRED fields ride the constructor; everything
         * optional defaults empty and is assigned by the enclosing class
         * (which sees these private fields directly). The v2.4–v2.6
         * releases each widened the old telescoping constructor by
         * another parameter — this shape ends that churn: the next
         * optional field is a declaration and an assignment, not an
         * arity change at every call site.
         */
        Card(String id, String title, String notes, long created) {
            this.id = id;
            this.title = title;
            this.notes = notes;
            this.created = created;
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public String notes() {
            return notes;
        }

        public long created() {
            return created;
        }

        public long done() {
            return done;
        }

        public String label() {
            return label;
        }

        public String blockOwner() {
            return blockOwner;
        }

        public String blockAction() {
            return blockAction;
        }

        public long blockedSince() {
            return blockedSince;
        }

        public boolean blocked() {
            return !blockAction.isEmpty();
        }

        /** Work sessions as [startMillis, endMillis] pairs (end 0 = running). */
        public List<long[]> sessions() {
            List<long[]> out = new ArrayList<>(sessions.size());
            for (long[] sn : sessions) {
                out.add(sn.clone());
            }
            return out;
        }

        public boolean clockedIn() {
            return !sessions.isEmpty()
                    && sessions.get(sessions.size() - 1)[1] == 0L;
        }
    }

    /** One column: a name, its cards in order, and an advisory WIP limit. */
    public static final class Column {
        private String name;
        /** 0 means "no limit" — the header then shows a bare count. */
        private int wipLimit;
        private final List<Card> cards = new ArrayList<>();

        Column(String name, int wipLimit) {
            this.name = name;
            this.wipLimit = wipLimit;
        }

        public String name() {
            return name;
        }

        public int wipLimit() {
            return wipLimit;
        }

        public List<Card> cards() {
            return List.copyOf(cards);
        }

        /** True when a limit is set and the column holds more than it. */
        public boolean overLimit() {
            return wipLimit > 0 && cards.size() > wipLimit;
        }
    }

    private final List<Column> columns = new ArrayList<>();
    /** Board-level retro notes (v2.5.0), free multiline text, "" = none. */
    private String retro = "";

    /** The three-column starter every fresh project begins with. */
    public static TaskBoard starter() {
        TaskBoard b = new TaskBoard();
        b.addColumn("To Do", 0);
        b.addColumn("Doing", 0);
        b.addColumn("Done", 0);
        return b;
    }

    public List<Column> columns() {
        return List.copyOf(columns);
    }

    public int columnCount() {
        return columns.size();
    }

    public Column column(int i) {
        return columns.get(i);
    }

    // ---- column operations ----------------------------------------------

    /** Adds a column at the end; blank names are refused with false. */
    public boolean addColumn(String name, int wipLimit) {
        if (name == null || name.strip().isEmpty()) {
            return false;
        }
        columns.add(new Column(name.strip(), Math.max(0, wipLimit)));
        return true;
    }

    /** Renames; blank refused. Duplicate names are ALLOWED — a board with
     *  two "Blocked" columns is odd but the user's to have; identity here
     *  is position, unlike Block Studio's tags where identity is the name. */
    public boolean renameColumn(int index, String name) {
        if (name == null || name.strip().isEmpty()
                || index < 0 || index >= columns.size()) {
            return false;
        }
        columns.get(index).name = name.strip();
        return true;
    }

    public boolean setWipLimit(int index, int limit) {
        if (index < 0 || index >= columns.size()) {
            return false;
        }
        columns.get(index).wipLimit = Math.max(0, limit);
        return true;
    }

    /**
     * Removes a column AND its cards. The caller confirms with the user
     * first (safe-default per v1.98.0) — the model just refuses to
     * delete the last column, because a board with nowhere to put a
     * card is not a board.
     */
    public boolean removeColumn(int index) {
        if (columns.size() <= 1 || index < 0 || index >= columns.size()) {
            return false;
        }
        columns.remove(index);
        return true;
    }

    /** Moves a whole column left/right; clamped, refuses no-ops. */
    public boolean moveColumn(int from, int to) {
        if (from < 0 || from >= columns.size() || to < 0
                || to >= columns.size() || from == to) {
            return false;
        }
        columns.add(to, columns.remove(from));
        return true;
    }

    // ---- card operations -------------------------------------------------

    /** Adds a card at the END of the column; blank titles refused. */
    public Card addCard(int column, String title, String notes) {
        if (title == null || title.strip().isEmpty()
                || column < 0 || column >= columns.size()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Card c = new Card(UUID.randomUUID().toString(), title.strip(),
                notes == null ? "" : notes, now);
        c.done = column == columns.size() - 1 ? now : 0L;
        columns.get(column).cards.add(c);
        return c;
    }

    public boolean editCard(String id, String title, String notes) {
        if (title == null || title.strip().isEmpty()) {
            return false;
        }
        Card c = find(id);
        if (c == null) {
            return false;
        }
        c.title = title.strip();
        c.notes = notes == null ? "" : notes;
        return true;
    }

    public boolean removeCard(String id) {
        for (Column col : columns) {
            if (col.cards.removeIf(c -> c.id.equals(id))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves a card to {@code toColumn} at {@code toIndex}. The index is
     * CLAMPED into the destination's real range rather than refused —
     * a drop below the last card means "at the end", which is what the
     * gesture meant. Returns false only when the card or column does
     * not exist.
     */
    public boolean moveCard(String id, int toColumn, int toIndex) {
        if (toColumn < 0 || toColumn >= columns.size()) {
            return false;
        }
        for (Column col : columns) {
            for (int i = 0; i < col.cards.size(); i++) {
                if (col.cards.get(i).id.equals(id)) {
                    Card c = col.cards.remove(i);
                    List<Card> dest = columns.get(toColumn).cards;
                    int at = Math.max(0, Math.min(toIndex, dest.size()));
                    dest.add(at, c);
                    // the done stamp follows the LAST column: entering it
                    // records the moment (the overview's flow history),
                    // leaving it clears — the card is work again. A move
                    // within the last column keeps its original stamp.
                    boolean intoLast = toColumn == columns.size() - 1;
                    if (intoLast && c.done == 0L) {
                        c.done = System.currentTimeMillis();
                    } else if (!intoLast) {
                        c.done = 0L;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** The column index currently holding {@code id}, or -1. */
    public int columnOf(String id) {
        for (int i = 0; i < columns.size(); i++) {
            for (Card c : columns.get(i).cards) {
                if (c.id.equals(id)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public Card find(String id) {
        for (Column col : columns) {
            for (Card c : col.cards) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
        }
        return null;
    }

    public int cardCount() {
        int n = 0;
        for (Column c : columns) {
            n += c.cards.size();
        }
        return n;
    }

    // ---- labels, blockers, retro (v2.5.0) --------------------------------

    public String retro() {
        return retro;
    }

    public void setRetro(String text) {
        this.retro = text == null ? "" : text;
    }

    /** Sets (or with "" clears) the card's epic label. */
    public boolean setLabel(String id, String label) {
        Card c = find(id);
        if (c == null) {
            return false;
        }
        c.label = label == null ? "" : label.strip();
        return true;
    }

    /**
     * Marks the card blocked. The unblock ACTION is what makes a
     * blocker actionable (the register's whole point), so a blank one
     * is refused; the owner may be blank. Re-blocking an already
     * blocked card updates owner and action but keeps the ORIGINAL
     * since stamp — the card has been stuck since it first stuck.
     */
    public boolean block(String id, String owner, String action) {
        if (action == null || action.strip().isEmpty()) {
            return false;
        }
        Card c = find(id);
        if (c == null) {
            return false;
        }
        c.blockOwner = owner == null ? "" : owner.strip();
        c.blockAction = action.strip();
        if (c.blockedSince == 0L) {
            c.blockedSince = System.currentTimeMillis();
        }
        return true;
    }

    /** Clears the whole blocker triple. */
    public boolean unblock(String id) {
        Card c = find(id);
        if (c == null) {
            return false;
        }
        c.blockOwner = "";
        c.blockAction = "";
        c.blockedSince = 0L;
        return true;
    }

    // ---- the time clock (v2.6.0) -----------------------------------------

    /** The card whose clock is running, or null. */
    public Card runningCard() {
        for (Column col : columns) {
            for (Card c : col.cards) {
                if (c.clockedIn()) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * Starts the clock on {@code id} at {@code now}. Only one clock runs
     * on the whole board — you are only ever working on one thing — so
     * clocking in here first clocks out whatever was running. Refused
     * when the card is unknown or ALREADY running (a double clock-in
     * would silently fork time).
     */
    public boolean clockIn(String id, long now) {
        Card c = find(id);
        if (c == null || c.clockedIn()) {
            return false;
        }
        Card running = runningCard();
        if (running != null) {
            clockOut(running.id(), now);
        }
        c.sessions.add(new long[]{now, 0L});
        return true;
    }

    /** A session shorter than this is dropped whole by {@link #clockOut}
     *  — an accidental in/out is noise, not work. Public so the UI can
     *  SAY a session was dropped instead of deleting it silently. */
    public static final long BLIP_MS = 60_000L;

    /**
     * Stops the running clock on {@code id} at {@code now}; refused when
     * that card's clock is not running. A session shorter than
     * {@link #BLIP_MS} is DROPPED whole.
     */
    public boolean clockOut(String id, long now) {
        Card c = find(id);
        if (c == null || !c.clockedIn()) {
            return false;
        }
        long[] open = c.sessions.get(c.sessions.size() - 1);
        if (now - open[0] < BLIP_MS) {
            c.sessions.remove(c.sessions.size() - 1);
        } else {
            open[1] = now;
        }
        return true;
    }

    // ---- persistence -----------------------------------------------------

    public String toJson() {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray cols = new JSONArray();
        for (Column col : columns) {
            JSONObject jc = new JSONObject();
            jc.put("name", col.name);
            if (col.wipLimit > 0) {
                jc.put("wip", col.wipLimit);
            }
            JSONArray cards = new JSONArray();
            for (Card c : col.cards) {
                JSONObject j = new JSONObject();
                j.put("id", c.id);
                j.put("title", c.title);
                if (!c.notes.isEmpty()) {
                    j.put("notes", c.notes);
                }
                j.put("created", c.created);
                if (c.done > 0L) {
                    j.put("done", c.done);
                }
                if (!c.label.isEmpty()) {
                    j.put("label", c.label);
                }
                if (c.blocked()) {
                    j.put("blockAction", c.blockAction);
                    if (!c.blockOwner.isEmpty()) {
                        j.put("blockOwner", c.blockOwner);
                    }
                    j.put("blockedSince", c.blockedSince);
                }
                if (!c.sessions.isEmpty()) {
                    JSONArray sess = new JSONArray();
                    for (long[] sn : c.sessions) {
                        JSONArray pair = new JSONArray();
                        pair.put(sn[0]);
                        pair.put(sn[1]);
                        sess.put(pair);
                    }
                    j.put("sessions", sess);
                }
                cards.put(j);
            }
            jc.put("cards", cards);
            cols.put(jc);
        }
        root.put("columns", cols);
        if (!retro.isEmpty()) {
            root.put("retro", retro);
        }
        return root.toString(2);
    }

    /**
     * Parses a board. Throws on malformed JSON (the IO layer .baks and
     * falls back to {@link #starter()}); tolerates missing OPTIONAL
     * keys, because a hand-edited file that dropped "notes" should not
     * cost the user their board. A card without an id — or one REPEATING
     * an id already seen — gets a fresh one: ids are internal identity,
     * not user data, and this file is checked in, so a merge resolved by
     * keeping both sides hands us two cards wearing one id. Left alone,
     * deleting either would delete both.
     */
    public static TaskBoard fromJson(String json) {
        JSONObject root = new JSONObject(json);
        TaskBoard b = new TaskBoard();
        b.retro = root.optString("retro", "");
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        JSONArray cols = root.getJSONArray("columns");
        for (int i = 0; i < cols.length(); i++) {
            JSONObject jc = cols.getJSONObject(i);
            Column col = new Column(jc.getString("name"),
                    Math.max(0, jc.optInt("wip", 0)));
            JSONArray cards = jc.optJSONArray("cards");
            if (cards != null) {
                for (int k = 0; k < cards.length(); k++) {
                    JSONObject j = cards.getJSONObject(k);
                    String id = j.optString("id", "").strip();
                    if (id.isEmpty() || !seenIds.add(id)) {
                        id = UUID.randomUUID().toString();
                        seenIds.add(id);
                    }
                    Card card = new Card(
                            id,
                            j.getString("title"),
                            j.optString("notes", ""),
                            j.optLong("created", 0L));
                    card.done = j.optLong("done", 0L);
                    card.label = j.optString("label", "");
                    card.blockOwner = j.optString("blockOwner", "");
                    card.blockAction = j.optString("blockAction", "");
                    card.blockedSince = j.optLong("blockedSince", 0L);
                    JSONArray sess = j.optJSONArray("sessions");
                    if (sess != null) {
                        for (int m = 0; m < sess.length(); m++) {
                            JSONArray pair = sess.optJSONArray(m);
                            if (pair != null && pair.length() == 2
                                    && pair.optLong(0, 0L) > 0L) {
                                // end < start closes at start here; stray
                                // OPEN pairs (end 0) are healed after the
                                // whole board parses — healOpenSessions
                                long start = pair.optLong(0, 0L);
                                long end = pair.optLong(1, 0L);
                                card.sessions.add(new long[]{start,
                                        end != 0L && end < start ? start : end});
                            }
                        }
                    }
                    col.cards.add(card);
                }
            }
            b.columns.add(col);
        }
        if (b.columns.isEmpty()) {
            throw new IllegalArgumentException("a board needs at least one column");
        }
        healOpenSessions(b);
        return b;
    }

    /**
     * Enforces the two open-session invariants the runtime keeps by
     * construction but a checked-in file cannot promise (v2.9.0, the
     * arc review): a keep-both merge or hand edit can leave an open
     * pair (end 0) that is NOT a card's last session, or leave TWO
     * cards both "running". {@link Card#clockedIn()} and
     * {@link #clockOut} only ever see the LAST pair, so a stray open
     * pair is unreachable by any gesture while the TIME report and the
     * standup count it up to NOW forever — a phantom session that
     * silently inflates every number. The heal closes each stray at
     * its OWN start (zero credit — any other end would be invented
     * time); when several cards are open, the LATEST start keeps the
     * clock, because it is the one still plausibly running.
     */
    private static void healOpenSessions(TaskBoard b) {
        Card latestOpen = null;
        for (Column col : b.columns) {
            for (Card c : col.cards) {
                for (int s = 0; s < c.sessions.size() - 1; s++) {
                    long[] sn = c.sessions.get(s);
                    if (sn[1] == 0L) {
                        sn[1] = sn[0];
                    }
                }
                if (c.clockedIn() && (latestOpen == null
                        || c.sessions.get(c.sessions.size() - 1)[0]
                        > latestOpen.sessions.get(latestOpen.sessions.size() - 1)[0])) {
                    latestOpen = c;
                }
            }
        }
        for (Column col : b.columns) {
            for (Card c : col.cards) {
                if (c.clockedIn() && c != latestOpen) {
                    long[] open = c.sessions.get(c.sessions.size() - 1);
                    open[1] = open[0];
                }
            }
        }
    }
}
