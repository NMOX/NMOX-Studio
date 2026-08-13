package org.nmox.studio.ui.irc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * WeeChat's hotlist jump, distilled: given the buffers in display
 * order, where should "jump to activity" go? Mentions always outrank
 * plain unread — someone saying your name is the thing you came back
 * for — and within a tier the first buffer in tree order wins, so
 * repeated jumps sweep the tree top to bottom.
 */
final class Hotlist {

    private Hotlist() {
    }

    static Optional<String> pick(List<String> orderedKeys,
            Map<String, Integer> mentions, Set<String> unread) {
        for (String k : orderedKeys) {
            if (mentions.getOrDefault(k, 0) > 0) {
                return Optional.of(k);
            }
        }
        for (String k : orderedKeys) {
            if (unread.contains(k)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }
}
