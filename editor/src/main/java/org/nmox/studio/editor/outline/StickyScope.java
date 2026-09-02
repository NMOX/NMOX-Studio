package org.nmox.studio.editor.outline;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sticky scroll's pure core: which outline items ENCLOSE a given line.
 *
 * <p>{@link OutlineModel} items carry a start line and a depth but no end,
 * so the end is derived here per family, honestly: brace families
 * ({@code brace}, {@code js}, {@code css}, {@code rust}, {@code go}) balance
 * the braces from the item's line with the outline's own code-blanking, so
 * a function that closed before the viewport is never pinned; indentation
 * families ({@code python}, {@code nim}, {@code yaml}) end at the last line
 * indented deeper than the item; every other family ends where the next
 * item at the same-or-shallower depth begins (the outline's nesting is the
 * only structure those families afford). Ranges are then a total function:
 * the enclosing chain of line {@code L} is every item whose
 * {@code [start, end]} contains {@code L} with strictly increasing depth,
 * outermost first, capped at the caller's row budget (the innermost rows
 * win, because they are the ones the reader has lost sight of).
 */
public final class StickyScope {

    private static final Set<String> BRACE_FAMILIES = Set.of("brace", "js", "css", "rust", "go");
    private static final Set<String> INDENT_FAMILIES = Set.of("python", "nim", "yaml");

    private StickyScope() {
    }

    /**
     * End line (inclusive, 0-based) of every item, aligned with
     * {@code items}; {@code lines} is the document split on \n.
     */
    public static int[] endLines(String family, List<String> lines, List<OutlineModel.Item> items) {
        int[] ends = new int[items.size()];
        int last = Math.max(0, lines.size() - 1);
        for (int i = 0; i < items.size(); i++) {
            OutlineModel.Item it = items.get(i);
            int end;
            if (BRACE_FAMILIES.contains(family)) {
                end = braceEnd(lines, it.line());
            } else if (INDENT_FAMILIES.contains(family)) {
                end = indentEnd(lines, it.line());
            } else {
                end = -1;
            }
            if (end < 0) {
                end = nextItemEnd(items, i, last);
            }
            ends[i] = Math.min(Math.max(end, it.line()), last);
        }
        return ends;
    }

    /**
     * The items enclosing {@code line}, outermost first, at most
     * {@code maxRows} — the innermost kept when the chain is deeper.
     */
    public static List<OutlineModel.Item> enclosing(List<OutlineModel.Item> items, int[] ends,
            int line, int maxRows) {
        List<OutlineModel.Item> chain = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            OutlineModel.Item it = items.get(i);
            if (it.line() > line) {
                break;
            }
            if (ends[i] < line) {
                continue;
            }
            // a deeper-or-equal item that also contains the line replaces the
            // chain tail only when it is strictly deeper than what we kept
            while (!chain.isEmpty() && chain.get(chain.size() - 1).depth() >= it.depth()) {
                chain.remove(chain.size() - 1);
            }
            chain.add(it);
        }
        if (maxRows >= 0 && chain.size() > maxRows) {
            return new ArrayList<>(chain.subList(chain.size() - maxRows, chain.size()));
        }
        return chain;
    }

    /**
     * Brace balance from {@code start}: the line on which the depth opened
     * at or after the item's line returns to zero; -1 when the item's line
     * opens no brace (a one-line declaration, or a brace on a later line
     * past the lookahead) so the caller falls back.
     */
    static int braceEnd(List<String> lines, int start) {
        boolean[] state = new boolean[2];
        int depth = 0;
        boolean opened = false;
        for (int i = start; i < lines.size(); i++) {
            String code = blankQuotes(OutlineModel.stripNonCode(lines.get(i), state));
            for (int c = 0; c < code.length(); c++) {
                char ch = code.charAt(c);
                if (ch == '{') {
                    depth++;
                    opened = true;
                } else if (ch == '}') {
                    depth--;
                    if (opened && depth <= 0) {
                        return i;
                    }
                }
            }
            if (!opened && i > start + 1) {
                return -1; // no brace within the declaration's first lines
            }
        }
        return opened ? lines.size() - 1 : -1;
    }

    /** Last line indented deeper than the item's line (blank lines ride along). */
    static int indentEnd(List<String> lines, int start) {
        int base = indentOf(lines.get(start));
        int end = start;
        for (int i = start + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.isBlank()) {
                continue;
            }
            if (indentOf(l) <= base) {
                break;
            }
            end = i;
        }
        return end;
    }

    private static int nextItemEnd(List<OutlineModel.Item> items, int i, int last) {
        int depth = items.get(i).depth();
        for (int j = i + 1; j < items.size(); j++) {
            if (items.get(j).depth() <= depth) {
                return items.get(j).line() - 1;
            }
        }
        return last;
    }

    /**
     * Blanks single- and double-quoted strings within a line (escape-aware)
     * — the outline's own blanking covers comments and template literals,
     * because its regexes never look inside quotes; the brace balance does.
     */
    static String blankQuotes(String code) {
        StringBuilder sb = new StringBuilder(code.length());
        char quote = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (quote == 0) {
                if (c == '\'' || c == '"') {
                    quote = c;
                    sb.append(' ');
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '\\' && i + 1 < code.length()) {
                    sb.append("  ");
                    i++;
                } else {
                    if (c == quote) {
                        quote = 0;
                    }
                    sb.append(' ');
                }
            }
        }
        return sb.toString();
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
            n++;
        }
        return n;
    }
}
