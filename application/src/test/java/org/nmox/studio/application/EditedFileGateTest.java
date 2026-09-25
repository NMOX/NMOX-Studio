package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Code that names the file being edited asks {@code core.util.EditedFile}
 * (a document) or {@code rack.service.EditorTabs} (a tab), never a
 * DataObject's primary file, which names the wrong file for a DataObject
 * of several files (3.2 fourth review, read from the properties editor's
 * bytecode; this platform's loader forms no such group, so the gate holds
 * a rule rather than a defect a user met — see {@code EditedFile}).
 *
 * <p>The population is derived, across every module, from what a file
 * reads rather than from folders listed by hand (fifth review: the first
 * cut listed three editor folders; sixth review: it read only the editor,
 * missed {@code NbEditorUtilities.getFileObject} — which IS the primary
 * file — and never looked at code that starts from a TAB). A file is in
 * the population when it reads a document's stream
 * ({@code StreamDescriptionProperty}) or asks the window registry for tabs;
 * it offends when it also reads {@code getPrimaryFile(} or
 * {@code NbEditorUtilities.getFileObject(} and is not blessed below with
 * the reason its primary file is the right one.
 */
class EditedFileGateTest {

    /** Files whose primary-file read is right, and why. */
    private static final Map<String, String> BLESSED = Map.ofEntries(
            Map.entry("RenameClassAction.java", "stylesheets and markup: one file per DataObject"),
            Map.entry("NgTemplateCompletionProvider.java", "Angular templates: one file per DataObject"),
            Map.entry("NgSwitchActions.java", "Angular component files: one file per DataObject"),
            Map.entry("RunFocusedTestAction.java", "test sources: one file per DataObject"),
            Map.entry("DapDebugAction.java", "JS/TS/Python/Go sources: one file per DataObject"),
            Map.entry("MainWindow.java", "asks only whether a tab holds a data file, which a group's primary answers"),
            Map.entry("NpmExplorerTopComponent.java", "reads a selected NODE's DataObject, not an editor tab's"),
            Map.entry("ProjectExplorerTopComponent.java", "the primary decides only folder-or-file, which a group shares"),
            Map.entry("RunScriptAction.java", "package.json: one file per DataObject"),
            Map.entry("CopyTsTypesAction.java", "JSON files: one file per DataObject"),
            Map.entry("TestInApiStudioAction.java", "JS/TS route sources: one file per DataObject"),
            Map.entry("GitStatusLine.java", "reads a DataObject's primary only to tell a group member from it (groupMember); the file it annotates comes from EditorTabs"),
            Map.entry("EditedFile.java", "the rule itself: a DataObject of one file is that file"));

    @Test
    @DisplayName("code that names the file being edited never reads a DataObject's primary file")
    void noPrimaryFileWhereTheEditedFileIsMeant() throws Exception {
        List<String> offenders = new ArrayList<>();
        List<String> population = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Path.of(".."))) {
            for (Path p : walk.filter(x -> {
                String s = x.toString().replace('\\', '/');
                return s.endsWith(".java") && s.contains("/src/main/java/") && !s.contains("/.claude/")
                        && !s.contains("/examples/");
            }).toList()) {
                String src = Files.readString(p);
                boolean reader = src.contains("StreamDescriptionProperty")
                        || src.contains("NbEditorUtilities.getFileObject(")
                        || src.contains(".getRegistry()")
                        || src.contains("isOpenedEditorTopComponent(")
                        || src.contains("getSelectedTopComponent(");
                if (!reader) {
                    continue;
                }
                String name = p.getFileName().toString();
                population.add(name);
                boolean primary = src.contains("getPrimaryFile(")
                        || src.contains("NbEditorUtilities.getFileObject(");
                if (primary && !BLESSED.containsKey(name)) {
                    offenders.add(name);
                }
            }
        }
        assertThat(population).as("the census found the document and tab readers").hasSizeGreaterThanOrEqualTo(25);
        assertThat(population).as("every blessing names a file the census still reads")
                .containsAll(BLESSED.keySet().stream().filter(n -> !"EditedFile.java".equals(n)).toList());
        assertThat(offenders).as("read EditedFile.of(document) or EditorTabs.fileOf(tab) instead").isEmpty();
    }
}
