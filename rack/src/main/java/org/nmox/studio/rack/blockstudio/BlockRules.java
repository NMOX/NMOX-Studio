package org.nmox.studio.rack.blockstudio;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The interlock law: which pieces snap inside which. Pure and total —
 * the canvas asks before every drop, the codegen may assume it holds,
 * and the contract test walks the whole matrix.
 *
 * <ul>
 *   <li>COMPONENT holds state declarations and its template
 *       (elements/text).</li>
 *   <li>ELEMENT holds template children plus its own attributes, style
 *       and event listeners.</li>
 *   <li>ON_EVENT and IF_STATE hold actions (IF nests, for else-less
 *       guard chains).</li>
 *   <li>Leaves (TEXT, SET_ATTR, STYLE, STATE, SET_STATE, TOGGLE_CLASS,
 *       LOG) hold nothing.</li>
 * </ul>
 */
public final class BlockRules {

    private static final Set<BlockKind> ACTIONS = EnumSet.of(
            BlockKind.SET_STATE, BlockKind.TOGGLE_CLASS, BlockKind.LOG, BlockKind.IF_STATE,
            BlockKind.DISPATCH);

    private static final Map<BlockKind, Set<BlockKind>> ACCEPTS = Map.of(
            BlockKind.COMPONENT, EnumSet.of(BlockKind.STATE, BlockKind.ELEMENT, BlockKind.TEXT,
                    BlockKind.SLOT, BlockKind.TIMER),
            BlockKind.ELEMENT, EnumSet.of(BlockKind.ELEMENT, BlockKind.TEXT,
                    BlockKind.SET_ATTR, BlockKind.STYLE, BlockKind.ON_EVENT, BlockKind.SLOT),
            BlockKind.ON_EVENT, ACTIONS,
            BlockKind.IF_STATE, ACTIONS,
            // a timer has no listening element; TOGGLE_CLASS under it (or an
            // IF_STATE under it) toggles on the host component itself
            BlockKind.TIMER, ACTIONS);

    private BlockRules() {
    }

    /** True when a piece of {@code child} kind snaps inside {@code parent}. */
    public static boolean accepts(BlockKind parent, BlockKind child) {
        return ACCEPTS.getOrDefault(parent, Set.of()).contains(child);
    }

    /** True for kinds that can hold children at all (paint a C-slot). */
    public static boolean container(BlockKind kind) {
        return !ACCEPTS.getOrDefault(kind, Set.of()).isEmpty();
    }
}
