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
 * ${fileDirname}} and {@code ${relativeFile}}. Pure: the editor's state
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

    /** The variables this class fills. */
    static final Set<String> NAMES = Set.of("file", "fileBasename", "fileBasenameNoExtension",
            "fileDirname", "relativeFile");

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

    private static String value(String name, File project, Path file) {
        Path leaf = file.getFileName();
        String base = leaf == null ? "" : leaf.toString();
        return switch (name) {
            case "file" -> file.toString();
            case "fileBasename" -> base;
            case "fileBasenameNoExtension" -> {
                int dot = base.lastIndexOf('.');
                yield dot > 0 ? base.substring(0, dot) : base;
            }
            case "fileDirname" -> file.getParent() == null ? file.toString() : file.getParent().toString();
            case "relativeFile" -> {
                Path root = project.getAbsoluteFile().toPath().normalize();
                try {
                    yield root.relativize(file).toString();
                } catch (IllegalArgumentException otherRoot) {
                    yield file.toString();
                }
            }
            default -> throw new IllegalStateException(name);
        };
    }
}
