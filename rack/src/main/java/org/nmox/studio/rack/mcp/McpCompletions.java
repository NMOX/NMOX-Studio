package org.nmox.studio.rack.mcp;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.spi.SymbolIndex;

/**
 * The spec's fourth primitive, {@code completion/complete} (v2.84.0): an
 * agent filling a prompt's argument or a resource template's slot asks
 * for candidates and gets at most {@link #MAX_VALUES}, with the honest
 * count and whether more exist. Two sources, both already bounded and
 * both read-only: the symbol index for {@code where_is}'s name (the
 * same hits {@code find_symbol} answers) and the project's file list
 * for {@code nmox://outline/{file}} (the search walk's own cap and
 * skip-dirs — {@code node_modules} never completes). The search
 * template's literal is anything; it completes to nothing.
 */
final class McpCompletions {

    static final int MAX_VALUES = 100;

    private final SymbolIndex index;
    private final Supplier<File> root;

    McpCompletions(SymbolIndex index, Supplier<File> root) {
        this.index = index;
        this.root = root;
    }

    static McpCompletions production() {
        return new McpCompletions(SymbolIndex.find(), McpTools.defaultAim());
    }

    /**
     * The {@code completion/complete} result for {@code params}; an
     * {@link IllegalArgumentException} names a malformed or unknown
     * reference, which the protocol answers as -32602.
     */
    JSONObject complete(JSONObject params) {
        JSONObject ref = params == null ? null : params.optJSONObject("ref");
        JSONObject argument = params == null ? null : params.optJSONObject("argument");
        if (ref == null || argument == null) {
            throw new IllegalArgumentException("completion/complete needs params.ref and params.argument");
        }
        String argName = argument.optString("name", "");
        String value = argument.optString("value", "");
        String type = ref.optString("type", "");
        List<String> all;
        switch (type) {
            case "ref/prompt" -> {
                String name = ref.optString("name", "");
                if (!McpPrompts.hasArgument(name, argName)) {
                    throw new IllegalArgumentException("no prompt " + name + " with argument " + argName);
                }
                all = symbolNames(value);
            }
            case "ref/resource" -> {
                String uri = ref.optString("uri", "");
                String slot = McpResources.templateArgument(uri);
                if (slot == null || !slot.equals(argName)) {
                    throw new IllegalArgumentException("no resource template " + uri + " with argument " + argName);
                }
                all = "file".equals(slot) ? files(value) : List.of();
            }
            default -> throw new IllegalArgumentException("ref.type must be ref/prompt or ref/resource");
        }
        JSONArray values = new JSONArray();
        for (String v : all.subList(0, Math.min(MAX_VALUES, all.size()))) {
            values.put(v);
        }
        return new JSONObject().put("completion", new JSONObject()
                .put("values", values)
                .put("total", all.size())
                .put("hasMore", all.size() > MAX_VALUES));
    }

    /** Distinct symbol names, the index's own order (prefix hits first). */
    private List<String> symbolNames(String value) {
        File dir = root.get();
        if (index == null || dir == null || value.isBlank()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (SymbolIndex.Hit h : index.search(dir, value, MAX_VALUES + 1).hits()) {
            names.add(h.name());
        }
        return new ArrayList<>(names);
    }

    /** Project files whose relative path starts with, then contains, the value (case-folded). */
    private List<String> files(String value) {
        File dir = root.get();
        if (dir == null) {
            return List.of();
        }
        String needle = value.toLowerCase(Locale.ROOT);
        List<String> prefix = new ArrayList<>();
        List<String> inside = new ArrayList<>();
        for (String rel : TextSearch.relativeFiles(dir.toPath())) {
            String folded = rel.toLowerCase(Locale.ROOT);
            if (folded.startsWith(needle)) {
                prefix.add(rel);
            } else if (!needle.isEmpty() && folded.contains(needle)) {
                inside.add(rel);
            }
        }
        prefix.sort(null);
        inside.sort(null);
        prefix.addAll(inside);
        return prefix;
    }

    /** Test seam: a root path as the production supplier would hand it. */
    static Supplier<File> rootOf(Path p) {
        return p::toFile;
    }
}
