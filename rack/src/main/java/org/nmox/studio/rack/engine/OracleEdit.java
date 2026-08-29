package org.nmox.studio.rack.engine;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * The pure core of Edit with ORACLE — the flow that turns a selection
 * plus an instruction into a PROPOSED replacement the user approves in a
 * diff preview before a byte changes. Everything with a rule lives here
 * so plain tests reach it: the prompt, the fenced-reply parse, and the
 * stale-buffer apply guard.
 *
 * <p>Two laws differ from Ask ORACLE on purpose. First, an over-cap
 * selection is REFUSED, never truncated — {@code CodeQuestion} may
 * truncate because a shortened question still explains, but a truncated
 * EDIT would replace the whole selection with a rewrite of its head and
 * silently destroy the tail (the money-law shape, v2.44.0). Second, the
 * reply must be exactly one fenced code block; anything else is refused
 * as {@code NOT_CODE} — a guessed extraction that pastes prose into a
 * source file is corruption, so a wrong shape changes nothing.
 */
public final class OracleEdit {

    private OracleEdit() {
    }

    /** Same ceiling as Ask ORACLE's disclosure — but enforced by refusal. */
    public static final int MAX_CODE_CHARS = OracleClient.CodeQuestion.MAX_CODE_CHARS;
    static final int MAX_INSTRUCTION_CHARS = 500;

    /**
     * The edit disclosure: exactly the data classes the CODE consent
     * names — the selection, the file's name, its language, and one line
     * of user text. The cap lives in this constructor so every
     * construction site enforces it (the v2.47.1 lesson): an over-cap
     * selection or a blank instruction cannot exist as an EditRequest.
     */
    public record EditRequest(String fileName, String language, String code,
            String instruction) {

        public EditRequest {
            if (code == null || code.isEmpty()) {
                throw new IllegalArgumentException("An edit needs a selection.");
            }
            if (code.length() > MAX_CODE_CHARS) {
                // refuse, never truncate: a rewrite of the head would
                // replace (and so delete) the un-sent tail on Apply
                throw new IllegalArgumentException("Selection too large for an "
                        + "ORACLE edit (" + code.length() + " chars, cap "
                        + MAX_CODE_CHARS + ").");
            }
            if (instruction == null || instruction.isBlank()) {
                throw new IllegalArgumentException("An edit needs an instruction.");
            }
            if (instruction.length() > MAX_INSTRUCTION_CHARS) {
                throw new IllegalArgumentException("Instruction too long ("
                        + instruction.length() + " chars, cap "
                        + MAX_INSTRUCTION_CHARS + ").");
            }
        }
    }

    /**
     * The edit prompt, a pure function of the request so a test asserts
     * it verbatim. Demands the one reply shape {@link #extractFencedCode}
     * accepts, and states the boundary to the model the same way the
     * consent dialog states it to the user.
     */
    public static String assembleEditPrompt(EditRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are editing code inside a developer's IDE. You are given ")
                .append("ONLY the selection below — not the rest of the file, not ")
                .append("the project. Apply the instruction to the selection and ")
                .append("reply with the COMPLETE revised selection in exactly one ")
                .append("fenced code block (```), and nothing outside the fence. ")
                .append("Change only what the instruction asks; keep everything ")
                .append("else byte-for-byte. If the instruction cannot be applied ")
                .append("to this selection alone, reply in prose (no fence) saying ")
                .append("why.\n\n");
        sb.append("File: ").append(r.fileName() == null || r.fileName().isBlank()
                ? "(unknown)" : r.fileName()).append('\n');
        sb.append("Language: ").append(r.language() == null || r.language().isBlank()
                ? "(unknown)" : r.language()).append('\n');
        sb.append("Instruction: ").append(r.instruction()).append('\n');
        sb.append("Selected code:\n").append(r.code()).append('\n');
        return sb.toString();
    }

    /**
     * Pulls the replacement out of the model's reply. Accepts exactly one
     * fenced block: exactly two lines starting with {@code ```} (the
     * opener may carry a language tag), content = the bytes between the
     * opener's newline and the newline before the closer, verbatim — so
     * a selection without a trailing newline round-trips, because the
     * newline before the closing fence belongs to the fence syntax, not
     * the code. Zero fences (the model answered in prose — its honest
     * "can't do this" channel) or more than one block returns null; the
     * caller refuses out loud. A reply containing a literal ``` line
     * inside the code is indistinguishable from two blocks and is
     * refused too — the honest ceiling, stated here.
     */
    public static String extractFencedCode(String reply) {
        if (reply == null) {
            return null;
        }
        String[] lines = reply.split("\n", -1);
        int open = -1;
        int close = -1;
        int fenceLines = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("```")) {
                fenceLines++;
                if (open < 0) {
                    open = i;
                } else if (close < 0) {
                    close = i;
                }
            }
        }
        if (fenceLines != 2) {
            return null;
        }
        // an empty block (closer right after opener) is a deliberate
        // "delete the selection" — legal; the preview shows it plainly
        StringBuilder sb = new StringBuilder();
        for (int i = open + 1; i < close; i++) {
            if (i > open + 1) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /**
     * An edit never changes whether the selection ends in a newline.
     * The fence syntax forces the model's block to end WITHOUT one (the
     * newline before the closing fence belongs to the fence), so a
     * newline-terminated selection would otherwise lose its terminator
     * on Apply and GLUE the following line onto the replacement's tail
     * — the v2.48.0 walk's own preview reported it ("12 → 11 lines")
     * and nobody read the honest counter. Symmetric on purpose: a
     * model-invented trailing newline the selection never had is
     * dropped the same way, so the preview always shows the truth.
     */
    public static String matchTrailingNewline(String original, String replacement) {
        boolean had = original.endsWith("\n");
        boolean has = replacement.endsWith("\n");
        if (had && !has) {
            return replacement + "\n";
        }
        if (!had && has) {
            return replacement.substring(0, replacement.length() - 1);
        }
        return replacement;
    }

    /**
     * The stale-buffer apply guard: replaces {@code [start, start+original.length)}
     * with {@code replacement} ONLY if the document still holds exactly
     * {@code original} there — the file may have changed while ORACLE was
     * thinking (the v1.229.0 stale-document law), and applying against a
     * moved buffer would corrupt unrelated text. Returns false with the
     * document untouched when the guard refuses; the caller says so out
     * loud. The caller owns atomicity (wrap in runAtomicAsUser) and the
     * EDT.
     */
    public static boolean replaceIfUnchanged(Document doc, int start,
            String original, String replacement) throws BadLocationException {
        if (doc == null || start < 0
                || start + original.length() > doc.getLength()) {
            return false;
        }
        String current = doc.getText(start, original.length());
        if (!current.equals(original)) {
            return false;
        }
        doc.remove(start, original.length());
        doc.insertString(start, replacement, null);
        return true;
    }
}
