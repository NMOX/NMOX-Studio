package org.nmox.studio.tools.npm;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.WizardDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wizard step's controller contract, driven the way the platform
 * drives it: getComponent lazily builds (and then reuses) the Swing
 * form, readSettings pushes descriptor properties into the form,
 * storeSettings pulls name / project folder / project type back out,
 * and isValid answers from the form's own checks — all headless, since
 * constructing Swing components needs no display.
 */
class WebProjectWizardPanelTest {

    @SuppressWarnings("unchecked")
    private static WizardDescriptor descriptorOver(WebProjectWizardPanel panel) {
        return new WizardDescriptor(new WizardDescriptor.Panel[]{panel});
    }

    @Test
    @DisplayName("getComponent builds the visual once, names it from the Bundle, and reuses it")
    void componentIsLazyCachedAndNamed() {
        WebProjectWizardPanel panel = new WebProjectWizardPanel();

        java.awt.Component first = panel.getComponent();
        assertThat(first).isInstanceOf(WebProjectWizardPanelVisual.class);
        assertThat(first.getName()).isEqualTo("Create Project");
        assertThat(panel.getComponent()).isSameAs(first);
    }

    @Test
    @DisplayName("The single step is a Finish panel with a help context")
    void finishableWithHelp() {
        WebProjectWizardPanel panel = new WebProjectWizardPanel();
        assertThat(panel.isFinishPanel()).isTrue();
        assertThat(panel.getHelp()).isNotNull();
    }

    @Test
    @DisplayName("readSettings then storeSettings round-trips name, folder, and the default project type")
    void settingsRoundTrip(@TempDir Path dir) {
        WebProjectWizardPanel panel = new WebProjectWizardPanel();
        panel.getComponent(); // the machinery always materializes the form first
        WizardDescriptor wiz = descriptorOver(panel);
        wiz.putProperty("projdir", dir.resolve("MyApp").toFile());
        wiz.putProperty("name", "MyApp");

        panel.readSettings(wiz);
        panel.storeSettings(wiz);

        assertThat((String) wiz.getProperty("name")).isEqualTo("MyApp");
        assertThat((File) wiz.getProperty("projdir"))
                .isEqualTo(new File(dir.toFile(), "MyApp"));
        // the combo starts on Vanilla JavaScript
        assertThat((String) wiz.getProperty("projectType")).isEqualTo("vanilla");
    }

    @Test
    @DisplayName("A project name carrying path separators fails validation with an error message")
    void slashInNameIsInvalid(@TempDir Path dir) {
        WebProjectWizardPanel panel = new WebProjectWizardPanel();
        panel.getComponent();
        WizardDescriptor wiz = descriptorOver(panel);
        wiz.putProperty("projdir", dir.resolve("bad").toFile());
        wiz.putProperty("name", "bad/name");
        panel.readSettings(wiz);

        assertThat(panel.isValid()).isFalse();
        assertThat((String) wiz.getProperty(WizardDescriptor.PROP_ERROR_MESSAGE))
                .contains("invalid characters");
    }

    @Test
    @DisplayName("An empty project name fails validation before any filesystem check")
    void emptyNameIsInvalid(@TempDir Path dir) {
        WebProjectWizardPanel panel = new WebProjectWizardPanel();
        panel.getComponent();
        WizardDescriptor wiz = descriptorOver(panel);
        wiz.putProperty("projdir", dir.resolve("x").toFile());
        wiz.putProperty("name", "   ");
        panel.readSettings(wiz);

        assertThat(panel.isValid()).isFalse();
    }

    @Test
    @DisplayName("validate() is a quiet no-op — deep validation lives in isValid")
    void validateDoesNotThrow(@TempDir Path dir) throws Exception {
        WebProjectWizardPanel panel = new WebProjectWizardPanel();
        panel.getComponent();
        WizardDescriptor wiz = descriptorOver(panel);
        wiz.putProperty("projdir", dir.resolve("ok").toFile());
        panel.readSettings(wiz);

        panel.validate(); // WizardValidationException would fail the test
    }
}
