/**
 * Ghost text — Complete with ORACLE, on demand.
 *
 * <p><b>What lives here.</b>
 * {@link org.nmox.studio.editor.ghost.CompleteWithOracleAction} is the
 * gesture (⌥⌘G or the editor popup): it gathers the code around the caret,
 * earns the CODE consent, sends once through the rack's
 * {@code OracleCompleteEngine}, and hands the insertion to
 * {@link org.nmox.studio.editor.ghost.GhostText}, which shows it as virtual
 * gray text at the caret until Tab inserts it or an edit, caret move or
 * click dismisses it. The pure halves — the capped request, the prompt, the fenced-reply
 * parse, the prefix trim — are {@code org.nmox.studio.rack.engine.OracleComplete}
 * beside its Ask/Edit siblings.
 *
 * <p><b>Which RCP mechanism.</b> A {@code HighlightsLayerFactory} registered
 * for every mime (root MimeRegistration) whose highlight carries the
 * platform's {@code virtual-text-prepend} attribute — editor-lib2's
 * HighlightsViewFactory wraps that view in a PrependedTextView, which is how
 * the platform's own inline hints paint (decompiled, not folklore). The
 * document is never touched until Tab.
 *
 * <p><b>Reading order.</b> OracleComplete (rack, the rules) →
 * OracleCompleteEngine (rack, the two gates in front of one send) →
 * CompleteWithOracleAction (gather, consent, lane, deliver) → GhostText
 * (arm, accept, dismiss; listeners installed only while armed).
 */
package org.nmox.studio.editor.ghost;
