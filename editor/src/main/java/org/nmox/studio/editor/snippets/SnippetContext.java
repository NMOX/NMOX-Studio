package org.nmox.studio.editor.snippets;

import java.time.ZonedDateTime;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Everything a snippet's variables and indentation are answered from,
 * gathered once at the moment of insertion: the file, the line under
 * the caret, the clock, the editor's indentation. A plain value, so the
 * translation over it ({@link SnippetTemplates}) is a pure function and
 * a test can state a whole editor in six lines.
 *
 * <p>A null field is a variable that is not set; VS Code then inserts
 * the variable's default, or nothing.
 *
 * @param filePath the edited file's full path, or null for a document with no file
 * @param workspaceFolder the project's folder, or null
 * @param selectedText the selection, or null when there is none
 * @param currentLine the text of the caret's line
 * @param currentWord the word at the caret, or null
 * @param lineIndex the caret's line, counted from zero
 * @param clipboard asked only when a body names {@code CLIPBOARD}; may answer null
 * @param lineComment the language's line-comment mark, or null
 * @param blockCommentStart the language's block-comment opener, or null
 * @param blockCommentEnd the language's block-comment closer, or null
 * @param now the moment of insertion, in the reader's zone
 * @param random where {@code RANDOM}, {@code RANDOM_HEX} and {@code UUID} come from
 * @param lineIndent the caret line's leading whitespace, which every later line of the body takes
 * @param indentUnit what one leading tab of a body becomes: a tab, or the editor's spaces
 */
public record SnippetContext(
        String filePath,
        String workspaceFolder,
        String selectedText,
        String currentLine,
        String currentWord,
        int lineIndex,
        Supplier<String> clipboard,
        String lineComment,
        String blockCommentStart,
        String blockCommentEnd,
        ZonedDateTime now,
        Random random,
        String lineIndent,
        String indentUnit) {

    /** A context with nothing set but the clock, the dice and a four-space indent; the starting point of every other. */
    public static SnippetContext at(ZonedDateTime now, Random random) {
        return new SnippetContext(null, null, null, "", null, 0, () -> null,
                null, null, null, now, random, "", "    ");
    }

    /** This context for another file inside another project folder. */
    public SnippetContext withFile(String path, String workspace) {
        return new SnippetContext(path, workspace, selectedText, currentLine, currentWord, lineIndex,
                clipboard, lineComment, blockCommentStart, blockCommentEnd, now, random, lineIndent, indentUnit);
    }

    /** This context with another caret line. */
    public SnippetContext withLine(String line, int index, String word, String selection) {
        return new SnippetContext(filePath, workspaceFolder, selection, line, word, index,
                clipboard, lineComment, blockCommentStart, blockCommentEnd, now, random, lineIndent, indentUnit);
    }

    /** This context with another indentation. */
    public SnippetContext withIndent(String ofLine, String unit) {
        return new SnippetContext(filePath, workspaceFolder, selectedText, currentLine, currentWord, lineIndex,
                clipboard, lineComment, blockCommentStart, blockCommentEnd, now, random, ofLine, unit);
    }

    /** This context with another language's comment marks. */
    public SnippetContext withComments(String line, String blockStart, String blockEnd) {
        return new SnippetContext(filePath, workspaceFolder, selectedText, currentLine, currentWord, lineIndex,
                clipboard, line, blockStart, blockEnd, now, random, lineIndent, indentUnit);
    }

    /** This context with another clipboard. */
    public SnippetContext withClipboard(Supplier<String> source) {
        return new SnippetContext(filePath, workspaceFolder, selectedText, currentLine, currentWord, lineIndex,
                source, lineComment, blockCommentStart, blockCommentEnd, now, random, lineIndent, indentUnit);
    }
}
