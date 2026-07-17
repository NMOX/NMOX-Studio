package org.nmox.studio.rack.blockstudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One interlocking piece: a kind, its parameter values, and the pieces
 * nested inside it. Plain mutable tree — {@link BlockDoc} owns identity
 * (ids) and structure edits, {@link BlockRules} owns what may nest where.
 */
public final class Block {

    private final String id;
    private final BlockKind kind;
    private final Map<String, String> params = new LinkedHashMap<>();
    private final List<Block> children = new ArrayList<>();

    Block(String id, BlockKind kind) {
        this.id = id;
        this.kind = kind;
        for (BlockKind.Param p : kind.params()) {
            params.put(p.key(), p.defaultValue());
        }
    }

    public String id() {
        return id;
    }

    public BlockKind kind() {
        return kind;
    }

    public String param(String key) {
        return params.getOrDefault(key, "");
    }

    public void setParam(String key, String value) {
        if (params.containsKey(key)) {
            params.put(key, value == null ? "" : value);
        }
    }

    public Map<String, String> params() {
        return params;
    }

    /** Live list — BlockDoc mutates it; everyone else should not. */
    public List<Block> children() {
        return children;
    }

    /** A one-line face for the canvas: kind plus its first param value. */
    public String face() {
        if (kind.params().isEmpty()) {
            return kind.display();
        }
        String first = param(kind.params().get(0).key());
        return switch (kind) {
            case COMPONENT -> "<" + first + ">";
            case ELEMENT -> first;
            case TEXT -> "“" + first + "”";
            case SET_ATTR -> param("name") + "=\"" + param("value") + "\"";
            case STYLE -> param("css");
            case STATE -> param("name") + " = " + param("initial");
            case ON_EVENT -> "on " + first;
            case SET_STATE -> param("name") + " ← " + param("expr");
            case TOGGLE_CLASS -> "toggle ." + first;
            case LOG -> "log " + "“" + first + "”";
            case IF_STATE -> "if " + param("name") + " " + param("op") + " " + param("value");
        };
    }
}
