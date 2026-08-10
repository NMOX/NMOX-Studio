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
        Card c = new Card(UUID.randomUUID().toString(), title.strip(),
                notes == null ? "" : notes, System.currentTimeMillis());
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
                cards.put(j);
            }
            jc.put("cards", cards);
            cols.put(jc);
        }
        root.put("columns", cols);
        return root.toString(2);
    }

    /**
     * Parses a board. Throws on malformed JSON (the IO layer .baks and
     * falls back to {@link #starter()}); tolerates missing OPTIONAL
     * keys, because a hand-edited file that dropped "notes" should not
     * cost the user their board. A card without an id gets a fresh one
     * — ids are internal identity, not user data.
     */
    public static TaskBoard fromJson(String json) {
        JSONObject root = new JSONObject(json);
        TaskBoard b = new TaskBoard();
        JSONArray cols = root.getJSONArray("columns");
        for (int i = 0; i < cols.length(); i++) {
            JSONObject jc = cols.getJSONObject(i);
            Column col = new Column(jc.getString("name"),
                    Math.max(0, jc.optInt("wip", 0)));
            JSONArray cards = jc.optJSONArray("cards");
            if (cards != null) {
                for (int k = 0; k < cards.length(); k++) {
                    JSONObject j = cards.getJSONObject(k);
                    col.cards.add(new Card(
                            j.optString("id", UUID.randomUUID().toString()),
                            j.getString("title"),
                            j.optString("notes", ""),
                            j.optLong("created", 0L)));
                }
            }
            b.columns.add(col);
        }
        if (b.columns.isEmpty()) {
            throw new IllegalArgumentException("a board needs at least one column");
        }
        return b;
    }
}
