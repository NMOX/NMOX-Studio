package org.nmox.studio.rack.projectstudio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renaming a script onto an existing name is refused, not silently
 * absorbed (v1.284.0, the project-starter persona walk).
 *
 * <p>package.json scripts are a JSON object; the Scripts table let two
 * rows share a name and the save fold kept the LAST one — renaming
 * {@code test}→{@code dev} on a fresh Express API destroyed the RUNNING
 * dev script and the test script in one Save: three scripts in, two
 * out, no warning. The v1.268.0 Block Studio tag collision, in its
 * fourth home.
 */
class ScriptNameCollisionTest {

    private static DefaultTableModel model(String[][] rows) {
        DefaultTableModel m = new DefaultTableModel(new Object[]{"Script", "Command"}, 0);
        for (String[] r : rows) {
            m.addRow(r);
        }
        return m;
    }

    @Test
    @DisplayName("unique names pass; a collision is named")
    void detectsTheCollision() {
        assertThat(ProjectConfigDialog.duplicateScriptName(model(new String[][]{
            {"dev", "nodemon server.js"}, {"test", "node --test"}, {"start", "node server.js"}})))
                .as("distinct names are fine")
                .isNull();
        assertThat(ProjectConfigDialog.duplicateScriptName(model(new String[][]{
            {"dev", "nodemon server.js"}, {"dev", "node --test"}})))
                .as("the walk's exact wreck: test renamed onto dev")
                .isEqualTo("dev");
        assertThat(ProjectConfigDialog.duplicateScriptName(model(new String[][]{
            {" dev ", "nodemon server.js"}, {"dev", "node --test"}})))
                .as("saveAll trims names before folding, so the check must too")
                .isEqualTo("dev");
    }

    @Test
    @DisplayName("blank rows never count — saveAll drops them anyway")
    void blankRowsAreNotCollisions() {
        assertThat(ProjectConfigDialog.duplicateScriptName(model(new String[][]{
            {"new-script", ""}, {"new-script", ""}})))
                .as("two untouched Add Script rows must not block Save")
                .isNull();
        assertThat(ProjectConfigDialog.duplicateScriptName(model(new String[][]{
            {"", "echo x"}, {"", "echo y"}})))
                .isNull();
    }

    @Test
    @DisplayName("Save refuses the collision before anything is written")
    void saveRefusesBeforeWriting() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "projectstudio", "ProjectConfigDialog.java"),
                StandardCharsets.UTF_8);
        int save = src.indexOf("private boolean saveAll() {");
        assertThat(save).isPositive();
        String body = src.substring(save, src.indexOf("pkg.save();", save));

        int check = body.indexOf("duplicateScriptName(");
        int firstWrite = body.indexOf("pkg.setName(");
        assertThat(check)
                .as("the refusal must precede every pkg mutation — a check"
                        + " after setName would leave half-applied state")
                .isPositive()
                .isLessThan(firstWrite);
        assertThat(body)
                .as("a refusal keeps the dialog open: return false, no write")
                .contains("return false;");

        int commit = body.indexOf("stopCellEditing()");
        assertThat(commit)
                .as("the open cell editor commits BEFORE the collision check,"
                        + " else Save validates a stale table — and a"
                        + " half-typed edit was silently dropped besides")
                .isPositive()
                .isLessThan(check);
    }
}
