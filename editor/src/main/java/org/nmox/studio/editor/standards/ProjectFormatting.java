package org.nmox.studio.editor.standards;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a project says about how one of its files is written: its
 * {@code .vscode/settings.json} ({@link VsCodeSettings}), with its
 * {@code .editorconfig} ({@link EditorConfig}) winning wherever both
 * speak - as in VS Code with the EditorConfig extension, and because an
 * {@code .editorconfig} is the file written for every editor (3.1.0).
 * The indentation overlay and the save-time rules both read this.
 */
public final class ProjectFormatting {

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
        merged.putAll(editorconfig);
        return merged;
    }
}
