package org.nmox.studio.editor.symbols.search;

import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.editor.outline.OutlineModel;

/**
 * Which of a file's outline items answer what was typed into Quick
 * Search: VS Code's Go to Symbol in Editor, as a rule.
 *
 * <p>The matching is the product's one matcher, {@link SearchTerms}, over
 * each item's name and detail, so {@code user id} finds {@code getUserById}
 * and {@code hero} finds {@code .hero-banner} the way every other search
 * box here would. A symbol whose words match whole lists before one that
 * matches by prefix or in the middle of a word; within each group the
 * file's own order is kept, because that is the order the reader knows.
 *
 * <p>{@code @} is VS Code's prefix for this search and is accepted: it is
 * dropped before matching, and {@code @} with nothing after it lists the
 * file's symbols from the top. Without the prefix a query of one
 * character lists nothing, so the category stays quiet while somebody is
 * typing a command name.
 */
public final class FileSymbols {

    /** The most rows one search lists. */
    public static final int MAX_RESULTS = 30;

    private FileSymbols() {
    }

    /**
     * Whether {@code typed} could find anything at all: {@code @} with or
     * without a name, or two characters or more. The search's own gate,
     * asked before the file is read, since a query that cannot match is
     * answered without its outline.
     */
    public static boolean asks(String typed) {
        if (typed == null) {
            return false;
        }
        String query = typed.strip();
        return query.startsWith("@") || query.length() >= 2;
    }

    /** The items {@code typed} finds, best first, at most {@link #MAX_RESULTS}. */
    public static List<OutlineModel.Item> matching(String typed, List<OutlineModel.Item> items) {
        if (!asks(typed)) {
            return List.of();
        }
        String query = typed.strip();
        boolean asked = query.startsWith("@");
        if (asked) {
            query = query.substring(1).strip();
        }
        if (query.isEmpty()) {
            return asked ? List.copyOf(items.subList(0, Math.min(MAX_RESULTS, items.size()))) : List.of();
        }
        if (!asked && query.length() < 2) {
            return List.of();
        }
        List<OutlineModel.Item> whole = new ArrayList<>();
        List<OutlineModel.Item> partial = new ArrayList<>();
        for (OutlineModel.Item item : items) {
            int score = SearchTerms.score(query, item.name(), item.detail());
            if (score == SearchTerms.EXACT) {
                whole.add(item);
            } else if (score == SearchTerms.LOOSE) {
                partial.add(item);
            }
        }
        whole.addAll(partial);
        return whole.size() > MAX_RESULTS ? List.copyOf(whole.subList(0, MAX_RESULTS)) : whole;
    }
}
