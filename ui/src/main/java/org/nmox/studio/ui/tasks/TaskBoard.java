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
         *  clock runs, each with its OWNER (3.4). At most ONE session per
         *  owner on the whole board is open — clocking in anywhere clocks
         *  out whatever that same person had running, and nobody else. */
        private final List<Session> sessions = new ArrayList<>();
        /**
         * The owners array exactly as read, when it did not line up with the
         * sessions (a merge grew one array and not the other), else null
         * (3.4). The first {@link #readSessions} sessions are then UNOWNED —
         * nobody's, never the reader's — and this array is written back as
         * it was, followed by the owners of any session clocked since, so a
         * save never throws away labels a person can still repair by hand.
         */
        private List<String> unmatchedOwners;
        /** How many sessions the file held when {@link #unmatchedOwners} was read. */
        private int readSessions;

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

        /**
         * The CURRENT USER's work sessions as [startMillis, endMillis]
         * pairs (end 0 = running) — what the TIME report and the Standup
         * count (3.4). A teammate's sessions on the same card are theirs:
         * see {@link #sessions(String)} and {@link #allSessions()}.
         */
        public List<long[]> sessions() {
            return sessions(currentUser());
        }

        /** {@code owner}'s sessions on this card, in the order they were clocked. */
        public List<long[]> sessions(String owner) {
            List<long[]> out = new ArrayList<>(sessions.size());
            for (Session sn : sessions) {
                if (sn.owner.equals(owner)) {
                    out.add(new long[]{sn.start, sn.end});
                }
            }
            return out;
        }

        /** Every person's sessions on this card — the team's time (the sprint report). */
        public List<long[]> allSessions() {
            List<long[]> out = new ArrayList<>(sessions.size());
            for (Session sn : sessions) {
                out.add(new long[]{sn.start, sn.end});
            }
            return out;
        }

        /** Whether the current user's clock runs on this card. */
        public boolean clockedIn() {
            return clockedIn(currentUser());
        }

        /** Whether {@code owner}'s clock runs on this card: their LAST session here is open. */
        public boolean clockedIn(String owner) {
            Session last = lastOf(owner);
            return last != null && last.end == 0L;
        }

        /** {@code owner}'s most recent session on this card, or null. */
        private Session lastOf(String owner) {
            for (int i = sessions.size() - 1; i >= 0; i--) {
                if (sessions.get(i).owner.equals(owner)) {
                    return sessions.get(i);
                }
            }
            return null;
        }
    }

    /**
     * One clocked session and whose it is (3.4). Until 3.4 a session was a
     * bare [start, end] pair and the board allowed ONE running clock in the
     * whole file — so on a board a team checks in, Bob clocking in clocked
     * Alice out, and two clocks merged from two machines healed by closing
     * one of them at zero credit. The owner is the operating system's user
     * name ({@code user.name}): it needs no setup, it is the name the shell
     * already shows, and it is stable across a person's sessions on one
     * machine. It is a label, not an identity — nothing is secured by it,
     * and two people sharing one login share one clock, which is what a
     * shared login means.
     *
     * <p>An owner of {@code ""} is UNOWNED: the file carried labels for the
     * card's sessions that did not line up with them, so no one can say
     * whose each one is. Such a session is counted as the team's time and
     * nobody's own, no gesture reaches it, and the parse-time heal leaves it
     * alone — it is somebody's clock, only not one this reading can name.
     *
     * <p>{@code legacy} marks a session read from a file written before
     * owners existed. Such a session belongs to whoever is reading the
     * board (the only honest reading of an unlabelled record), and it is
     * written back WITHOUT an owner, so opening an old board on Alice's
     * machine does not quietly sign Bob's past hours as hers.
     */
    static final class Session {
        final long start;
        long end;
        final String owner;
        final boolean legacy;

        Session(long start, long end, String owner, boolean legacy) {
            this.start = start;
            this.end = end;
            this.owner = owner;
            this.legacy = legacy;
        }
    }

    /**
     * Who the current user is, for the clock: the OS login ({@code user.name}),
     * or {@code "me"} where the JVM reports none — a board must still clock.
     */
    public static String currentUser() {
        String name = System.getProperty("user.name", "").strip();
        return name.isEmpty() ? "me" : name;
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

    // ---- the sprint (v2.37.0, the scrum-master pass) ---------------------

    /**
     * The current sprint: a NAMED time window the ceremonies hang off —
     * the burndown, the sprint report, velocity. Valid only as a whole:
     * a nonblank name AND {@code 0 < start <= end}; anything else reads
     * as "no sprint" (the parse-time heal for a hand-edited window).
     */
    private String sprintName = "";
    private long sprintStart;
    private long sprintEnd;

    /** A finished sprint, archived by {@link #closeSprint} — velocity's raw data. */
    public record ClosedSprint(String name, long start, long end, int done, String retro) {
    }

    /** Newest last; capped at {@link #SPRINT_HISTORY_CAP} by closeSprint. */
    private final List<ClosedSprint> sprintHistory = new ArrayList<>();

    static final int SPRINT_HISTORY_CAP = 24;

    public boolean hasSprint() {
        return !sprintName.isBlank() && sprintStart > 0 && sprintEnd >= sprintStart;
    }

    public String sprintName() {
        return sprintName;
    }

    public long sprintStart() {
        return sprintStart;
    }

    public long sprintEnd() {
        return sprintEnd;
    }

    public List<ClosedSprint> sprintHistory() {
        return java.util.Collections.unmodifiableList(sprintHistory);
    }

    /** Sets the sprint; an invalid window clears it (callers refuse first). */
    public void setSprint(String name, long start, long end) {
        if (name == null || name.isBlank() || start <= 0 || end < start) {
            sprintName = "";
            sprintStart = 0;
            sprintEnd = 0;
            return;
        }
        sprintName = name.strip();
        sprintStart = start;
        sprintEnd = end;
    }

    /**
     * Closes the current sprint: archives {name, window, done-in-window,
     * retro} into the history (oldest dropped past the cap), then clears
     * the sprint AND the retro notes — the retro belongs to the sprint
     * it reviewed, and it is ARCHIVED, never deleted. Cards stay exactly
     * where they are: closing a sprint is bookkeeping, not cleanup.
     * Returns the archived record, or null when no sprint is set.
     */
    public ClosedSprint closeSprint() {
        if (!hasSprint()) {
            return null;
        }
        int done = 0;
        long windowEnd = sprintEnd + 24L * 60 * 60 * 1000; // through end-of-day
        for (Column c : columns) {
            for (Card card : c.cards) {
                if (card.done > 0 && card.done >= sprintStart && card.done < windowEnd) {
                    done++;
                }
            }
        }
        ClosedSprint closed = new ClosedSprint(sprintName, sprintStart, sprintEnd, done, retro);
        sprintHistory.add(closed);
        while (sprintHistory.size() > SPRINT_HISTORY_CAP) {
            sprintHistory.remove(0);
        }
        sprintName = "";
        sprintStart = 0;
        sprintEnd = 0;
        retro = "";
        return closed;
    }

    /**
     * The three-column starter every fresh project begins with, named by its
     * caller.
     *
     * <p>The names are PARAMETERS because a fresh board is chrome until the
     * moment the user edits it, and a model has no business holding chrome:
     * a German walk found this board's headers reading To Do / Doing / Done
     * in a fully translated build. The core returns data, the consumer
     * renders it (the v2.101.0 rule) — {@code TasksIO.starterBoard()} is the
     * consumer that reads the bundle.
     *
     * <p>Nothing matches a column by its name, so translating them is safe;
     * and a board already written to {@code .nmoxtasks.json} keeps whatever
     * it was named, because by then it is the user's.
     */
    public static TaskBoard starter(String todo, String doing, String done) {
        TaskBoard b = new TaskBoard();
        b.addColumn(todo, 0);
        b.addColumn(doing, 0);
        b.addColumn(done, 0);
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

    // ---- the time clock (v2.6.0; one clock PER OWNER since 3.4) ----------

    /** The card whose clock the current user has running, or null. */
    public Card runningCard() {
        return runningCard(currentUser());
    }

    /** The card whose clock {@code owner} has running, or null. */
    public Card runningCard(String owner) {
        for (Column col : columns) {
            for (Card c : col.cards) {
                if (c.clockedIn(owner)) {
                    return c;
                }
            }
        }
        return null;
    }

    /** Starts the current user's clock on {@code id}; see {@link #clockIn(String, long, String)}. */
    public boolean clockIn(String id, long now) {
        return clockIn(id, now, currentUser());
    }

    /**
     * Starts {@code owner}'s clock on {@code id} at {@code now}. One clock
     * runs PER PERSON — you are only ever working on one thing — so
     * clocking in here first clocks out whatever that same owner had
     * running; a teammate's clock is never touched (3.4). Refused when
     * the card is unknown or ALREADY running for this owner (a double
     * clock-in would silently fork time).
     */
    public boolean clockIn(String id, long now, String owner) {
        Card c = find(id);
        if (c == null || owner == null || owner.isBlank() || c.clockedIn(owner)) {
            return false;
        }
        Card running = runningCard(owner);
        if (running != null) {
            clockOut(running.id(), now, owner);
        }
        c.sessions.add(new Session(now, 0L, owner, false));
        return true;
    }

    /** A session shorter than this is dropped whole by {@link #clockOut}
     *  — an accidental in/out is noise, not work. Public so the UI can
     *  SAY a session was dropped instead of deleting it silently. */
    public static final long BLIP_MS = 60_000L;

    /** Stops the current user's clock on {@code id}; see {@link #clockOut(String, long, String)}. */
    public boolean clockOut(String id, long now) {
        return clockOut(id, now, currentUser());
    }

    /**
     * Stops {@code owner}'s running clock on {@code id} at {@code now};
     * refused when that owner's clock is not running there. A session
     * shorter than {@link #BLIP_MS} is DROPPED whole.
     */
    public boolean clockOut(String id, long now, String owner) {
        Card c = find(id);
        if (c == null || owner == null || !c.clockedIn(owner)) {
            return false;
        }
        Session open = c.lastOf(owner);
        if (now - open.start < BLIP_MS) {
            c.sessions.remove(open);
        } else {
            open.end = now;
        }
        return true;
    }

    // ---- persistence -----------------------------------------------------

    public String toJson() {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT);
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
                    JSONArray owners = new JSONArray();
                    boolean anyOwned = false;
                    for (Session sn : c.sessions) {
                        JSONArray pair = new JSONArray();
                        pair.put(sn.start);
                        pair.put(sn.end);
                        sess.put(pair);
                        owners.put(sn.legacy ? "" : sn.owner);
                        anyOwned |= !sn.legacy;
                    }
                    j.put("sessions", sess);
                    // owners ride a PARALLEL array rather than a third
                    // element in each pair: a 3.3 reader keeps only
                    // two-element pairs, so [start, end, owner] would make
                    // an older NMOX Studio drop the sessions themselves on
                    // its next save — this way it drops only the labels
                    if (c.unmatchedOwners != null) {
                        // labels this reading could not line up are written
                        // back as they were read, never dropped (3.4, the
                        // review): the next save used to erase them, and
                        // every later reader then took the sessions as its own
                        JSONArray kept = new JSONArray();
                        c.unmatchedOwners.forEach(kept::put);
                        for (int n = c.readSessions; n < c.sessions.size(); n++) {
                            Session sn = c.sessions.get(n);
                            kept.put(sn.legacy ? "" : sn.owner);
                        }
                        j.put(SESSION_OWNERS, kept);
                    } else if (anyOwned) {
                        j.put(SESSION_OWNERS, owners);
                    }
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
        if (hasSprint()) {
            JSONObject sj = new JSONObject();
            sj.put("name", sprintName);
            sj.put("start", sprintStart);
            sj.put("end", sprintEnd);
            root.put("sprint", sj);
        }
        if (!sprintHistory.isEmpty()) {
            JSONArray hist = new JSONArray();
            for (ClosedSprint cs : sprintHistory) {
                JSONObject hj = new JSONObject();
                hj.put("name", cs.name());
                hj.put("start", cs.start());
                hj.put("end", cs.end());
                hj.put("done", cs.done());
                if (!cs.retro().isEmpty()) {
                    hj.put("retro", cs.retro());
                }
                hist.put(hj);
            }
            root.put("sprints", hist);
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
        return fromJson(json, currentUser());
    }

    /**
     * Parses a board as {@code viewer} reads it: sessions written without
     * an owner (every board before 3.4) are {@code viewer}'s. Package-private
     * so tests can read one file as two people.
     */
    static TaskBoard fromJson(String json, String viewer) {
        JSONObject root = new JSONObject(json);
        TaskBoard b = new TaskBoard();
        b.readFormat = formatOf(root);
        b.retro = root.optString("retro", "");
        JSONObject sj = root.optJSONObject("sprint");
        if (sj != null) {
            // setSprint IS the heal: a hand-edited or merge-mangled window
            // (blank name, end before start) loads as "no sprint" rather
            // than poisoning every ceremony downstream (the v2.9.0 law)
            b.setSprint(sj.optString("name", ""),
                    sj.optLong("start", 0), sj.optLong("end", 0));
        }
        JSONArray hist = root.optJSONArray("sprints");
        if (hist != null) {
            for (int i = 0; i < hist.length() && b.sprintHistory.size() < SPRINT_HISTORY_CAP; i++) {
                JSONObject hj = hist.optJSONObject(i);
                if (hj == null) {
                    continue;
                }
                String name = hj.optString("name", "");
                long start = hj.optLong("start", 0);
                long end = hj.optLong("end", 0);
                if (name.isBlank() || start <= 0 || end < start) {
                    continue; // a malformed archive row remembers nothing
                }
                b.sprintHistory.add(new ClosedSprint(name, start, end,
                        Math.max(0, hj.optInt("done", 0)), hj.optString("retro", "")));
            }
        }
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
                    // parse-time heal (v2.38.7): every gesture strips and
                    // refuses blank titles, but a hand-edited or
                    // merge-damaged file can carry newlines (which break
                    // the Standup/Sprint reports' line-per-item markdown)
                    // or a blank — heal, never drop: a keep-both merge's
                    // card must survive visibly (the v2.9.0 law's family)
                    String rawTitle = j.optString("title", "")
                            .replaceAll("\\s+", " ").strip();
                    Card card = new Card(
                            id,
                            rawTitle.isEmpty() ? "(untitled)" : rawTitle,
                            j.optString("notes", ""),
                            j.optLong("created", 0L));
                    card.done = j.optLong("done", 0L);
                    card.label = j.optString("label", "");
                    card.blockOwner = j.optString("blockOwner", "");
                    card.blockAction = j.optString("blockAction", "");
                    card.blockedSince = j.optLong("blockedSince", 0L);
                    JSONArray sess = j.optJSONArray("sessions");
                    if (sess != null) {
                        // the owners array is trusted only when it lines up
                        // with the sessions one-for-one: a merge that grew
                        // one array and not the other cannot say which
                        // label belongs to which session, so every session
                        // is read as UNOWNED — nobody's. Until the review
                        // they were read as unlabelled, which hands every
                        // one of them to the READER: Carol opened the
                        // board and owned Alice's and Bob's running clocks,
                        // the heal closed one of them at zero credit, and
                        // the next save dropped the labels for good.
                        JSONArray owners = j.optJSONArray(SESSION_OWNERS);
                        boolean unmatched = owners != null && owners.length() != sess.length();
                        if (unmatched) {
                            card.unmatchedOwners = new ArrayList<>(owners.length());
                            for (int m = 0; m < owners.length(); m++) {
                                card.unmatchedOwners.add(owners.optString(m, ""));
                            }
                        }
                        for (int m = 0; m < sess.length(); m++) {
                            JSONArray pair = sess.optJSONArray(m);
                            if (pair != null && pair.length() == 2
                                    && pair.optLong(0, 0L) > 0L) {
                                // end < start closes at start here; stray
                                // OPEN pairs (end 0) are healed after the
                                // whole board parses — healOpenSessions
                                long start = pair.optLong(0, 0L);
                                long end = pair.optLong(1, 0L);
                                long closed = end != 0L && end < start ? start : end;
                                if (unmatched) {
                                    card.sessions.add(new Session(start, closed, UNOWNED, false));
                                    continue;
                                }
                                String owner = owners == null ? "" : owners.optString(m, "").strip();
                                boolean legacy = owner.isEmpty();
                                card.sessions.add(new Session(start, closed,
                                        legacy ? viewer : owner, legacy));
                            }
                        }
                    }
                    card.readSessions = card.sessions.size();
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

    /** The card key holding each session's owner, aligned with {@code sessions} (3.4). */
    static final String SESSION_OWNERS = "sessionOwners";

    /** The format the file this board was read from says it is in; {@link #FORMAT} for a board made here. */
    private int readFormat = FORMAT;

    /** @see #readFormat */
    int readFormat() {
        return readFormat;
    }

    /** The owner of a session no reading can attribute (see {@link Session}). */
    static final String UNOWNED = "";

    /**
     * The board format this build writes (3.4). A board whose
     * {@code version} is higher came from a newer NMOX Studio: it is shown
     * read-only, because a save here would drop whatever that build added.
     */
    static final int FORMAT = 1;

    /** The format a board document says it is in; a missing or odd value reads as {@link #FORMAT}. */
    static int formatOf(JSONObject root) {
        return root.opt("version") instanceof Number n ? n.intValue() : FORMAT;
    }

    /**
     * Enforces the two open-session invariants the runtime keeps by
     * construction but a checked-in file cannot promise (v2.9.0, the
     * arc review), PER OWNER since 3.4: a keep-both merge or hand edit
     * can leave an open pair (end 0) that is NOT its owner's last
     * session on the card, or leave one owner running on TWO cards.
     * {@link Card#clockedIn(String)} and {@link #clockOut} only ever see
     * an owner's LAST session on a card, so a stray open pair is
     * unreachable by any gesture while the TIME report and the standup
     * count it up to NOW forever — a phantom session that silently
     * inflates every number. The heal closes each stray at its OWN start
     * (zero credit — any other end would be invented time); when one
     * owner is open on several cards, the LATEST start keeps the clock,
     * because it is the one still plausibly running.
     *
     * <p>Two DIFFERENT people each running a clock is not a stray and is
     * left alone. Until 3.4 it was healed like one: every merge of two
     * machines' boards closed one teammate's running session at zero
     * credit.
     */
    private static void healOpenSessions(TaskBoard b) {
        java.util.Map<String, Session> latestOpen = new java.util.HashMap<>();
        for (Column col : b.columns) {
            for (Card c : col.cards) {
                for (Session sn : c.sessions) {
                    if (sn.end != 0L || UNOWNED.equals(sn.owner)) {
                        continue; // an unowned clock is somebody's: never healed shut
                    }
                    if (c.lastOf(sn.owner) != sn) {
                        sn.end = sn.start; // open, but not its owner's last here
                        continue;
                    }
                    Session best = latestOpen.get(sn.owner);
                    if (best == null || sn.start > best.start) {
                        latestOpen.put(sn.owner, sn);
                    }
                }
            }
        }
        for (Column col : b.columns) {
            for (Card c : col.cards) {
                for (Session sn : c.sessions) {
                    if (sn.end == 0L && !UNOWNED.equals(sn.owner) && latestOpen.get(sn.owner) != sn) {
                        sn.end = sn.start;
                    }
                }
            }
        }
    }
}
