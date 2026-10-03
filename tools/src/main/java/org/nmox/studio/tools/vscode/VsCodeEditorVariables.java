package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code ${…}} variables VS Code fills from its ACTIVE EDITOR, filled
 * here from the file the caller says is being looked at: {@code ${file}},
 * {@code ${fileBasename}}, {@code ${fileBasenameNoExtension}}, {@code
 * ${fileExtname}}, {@code ${fileDirname}}, {@code ${fileDirnameBasename}},
 * {@code ${relativeFile}} and {@code ${relativeFileDirname}}. Pure: the editor's state
 * arrives as one value, a path or null, so every rule here is a unit test
 * and nothing here knows what an editor is.
 *
 * <p><b>Why they are worth honouring.</b> {@code "program": "${file}"} —
 * "debug the file I am looking at" — is the configuration VS Code writes
 * most often, and before this class it was refused as a value only VS Code
 * could supply. It was never VS Code's alone: the IDE knows which file its
 * own editor shows.
 *
 * <p><b>No file, no guess.</b> With no file open the variable has no
 * value, and VS Code itself stops there ("cannot be resolved, please open
 * an editor"). {@link #first} names the variable so the caller can refuse
 * by name; {@link #substitute} is never reached with a null file and says
 * so by throwing rather than by filling in a blank.
 *
 * <p><b>One pass.</b> A file may be called anything, {@code ${env:HOME}.js}
 * included, so a value that came FROM the editor is never read again for
 * variables: each {@code ${…}} of the text as written is replaced once,
 * the editor's by this class and every other by {@link
 * VsCodeTasks#substitute}.
 *
 * <p>The definitions are VS Code's (its variables reference): {@code
 * ${relativeFile}} is the file relative to the workspace folder — which is
 * a path starting {@code ..} for a file outside it, and the absolute path
 * when the two share no root (another drive) — and {@code
 * ${fileBasenameNoExtension}} drops the last extension only, and none from
 * a dotfile.
 */
final class VsCodeEditorVariables {

    /**
     * The variables this class fills: the ones that name the file, and
     * the same set a task is given ({@link VsCodeTasks#FILE_VARIABLES}) —
     * one list, so a launch configuration and a task never disagree about
     * which variables an editor can answer.
     */
    static final Set<String> NAMES = VsCodeTasks.FILE_VARIABLES;

    /** {@code ${…}}: VS Code's variable syntax, the body everything up to the first '}'. */
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]*)\\}");

    private VsCodeEditorVariables() {
    }

    /** The first editor variable {@code s} uses, as written, or null when it uses none. */
    static String first(String s) {
        Matcher m = VARIABLE.matcher(s);
        while (m.find()) {
            if (NAMES.contains(m.group(1))) {
                return m.group();
            }
        }
        return null;
    }

    /**
     * The first {@code ${…}} in {@code s} that neither this class nor
     * {@link VsCodeTasks} can supply, as written, or null: the editor's
     * variables are taken out of the question and the rest is {@link
     * VsCodeTasks#unsupportedVariable}'s, so the two never disagree about
     * what a variable is.
     */
    static String unsupported(String s) {
        Matcher m = VARIABLE.matcher(s);
        StringBuilder rest = new StringBuilder();
        while (m.find()) {
            // a space, not nothing: what stood either side of an editor
            // variable must not close up into a variable nobody wrote
            m.appendReplacement(rest, NAMES.contains(m.group(1)) ? " " : Matcher.quoteReplacement(m.group()));
        }
        m.appendTail(rest);
        return VsCodeTasks.unsupportedVariable(rest.toString());
    }

    /**
     * {@code s} with every supported variable replaced, once.
     *
     * @param file the file the editor shows; may be null only when {@code s}
     *             uses no editor variable ({@link #first} is how a caller knows)
     * @throws IllegalArgumentException when an editor variable is used and
     *         there is no file: a blank there would name a different path
     */
    static String substitute(String s, File project, UnaryOperator<String> env, Path file) {
        Matcher m = VARIABLE.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value;
            if (NAMES.contains(name)) {
                if (file == null) {
                    throw new IllegalArgumentException(m.group());
                }
                value = value(name, project, file.toAbsolutePath().normalize());
            } else {
                value = VsCodeTasks.substitute(m.group(), project, env);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * The value, by the one definition of these variables ({@link
     * VsCodeTasks#editorValue}): a task's {@code ${fileBasenameNoExtension}}
     * and a launch configuration's are the same string.
     */
    private static String value(String name, File project, Path file) {
        String value = VsCodeTasks.editorValue(name, project, new VsCodeTasks.EditorContext(file, 0, 0, null));
        if (value == null) {
            throw new IllegalStateException(name);
        }
        return value;
    }
}
