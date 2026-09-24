package org.nmox.studio.editor.standards;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a project says about how one of its files is written: its
 * {@code .vscode/settings.json} ({@link VsCodeSettings}), with its
 * {@code .editorconfig} ({@link EditorConfig}) winning wherever both
 * speak - as in VS Code with the EditorConfig extension, and because an
 * {@code .editorconfig} is the file written for every editor (3.1.0).
 * For indentation "where both speak" is the whole of it: an
 * {@code .editorconfig} naming any of style, size or tab width decides all
 * three. The indentation overlay and the save-time rules both read this.
 */
public final class ProjectFormatting {

    /** The properties that together say how a file is indented. */
    static final List<String> INDENTATION = List.of("indent_style", "indent_size", "tab_width");

    private ProjectFormatting() {
    }

    /** The merged properties, as EditorConfig keys and values. */
    public static Map<String, String> propertiesFor(File file) {
        Map<String, String> vscode = VsCodeSettings.propertiesFor(file);
        Map<String, String> editorconfig = EditorConfig.propertiesFor(file);
        if (vscode.isEmpty()) {
            return editorconfig;
        }
        Map<String, String> merged = new LinkedHashMap<>(vscode);
        if (INDENTATION.stream().anyMatch(editorconfig::containsKey)) {
            // indentation is one source, not a mix: EditorConfig derives
            // tab_width from indent_size when the file does not name it, and
            // a settings.json tab width left under an .editorconfig indent of
            // 4 made each level two tabs (the 3.1.0 review, probed)
            INDENTATION.forEach(merged::remove);
        }
        merged.putAll(editorconfig);
        return merged;
    }
}
