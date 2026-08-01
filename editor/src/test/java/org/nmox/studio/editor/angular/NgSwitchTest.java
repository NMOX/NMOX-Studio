package org.nmox.studio.editor.angular;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The component ↔ template switcher's pure rules (v1.219.0):
 * templateUrl wins, the sibling convention backstops, and misses stay
 * honest nulls.
 */
class NgSwitchTest {

    private static File write(File dir, String name, String text) throws IOException {
        File f = new File(dir, name);
        Files.writeString(f.toPath(), text);
        return f;
    }

    @Test
    @DisplayName("templateUrl in the decorator wins, resolved against the component's dir")
    void templateUrlWins(@TempDir File dir) throws Exception {
        File custom = write(dir, "view.html", "<p></p>");
        File ts = write(dir, "hello.component.ts",
                "@Component({ templateUrl: './view.html' }) export class X {}");
        assertThat(NgSwitch.templateFor(ts, NgSwitchActions.readQuietly(ts)))
                .isEqualTo(custom);
    }

    @Test
    @DisplayName("no templateUrl: the same-basename .html sibling")
    void siblingFallback(@TempDir File dir) throws Exception {
        File html = write(dir, "hello.component.html", "<p></p>");
        File ts = write(dir, "hello.component.ts",
                "@Component({ template: `<b>inline</b>` }) export class X {}");
        assertThat(NgSwitch.templateFor(ts, NgSwitchActions.readQuietly(ts)))
                .isEqualTo(html);
    }

    @Test
    @DisplayName("inline template and no sibling: honest null")
    void inlineOnlyIsNull(@TempDir File dir) throws Exception {
        File ts = write(dir, "hello.component.ts",
                "@Component({ template: `<b>inline</b>` }) export class X {}");
        assertThat(NgSwitch.templateFor(ts, NgSwitchActions.readQuietly(ts))).isNull();
    }

    @Test
    @DisplayName("template to component: same-basename .ts sibling")
    void componentSibling(@TempDir File dir) throws Exception {
        File ts = write(dir, "hello.component.ts", "export class X {}");
        File html = write(dir, "hello.component.html", "<p></p>");
        assertThat(NgSwitch.componentFor(html, NgSwitchActions::readQuietly))
                .isEqualTo(ts);
    }

    @Test
    @DisplayName("no sibling: the .ts whose templateUrl names this file — specs excluded")
    void componentByTemplateUrlScan(@TempDir File dir) throws Exception {
        write(dir, "owner.spec.ts", "templateUrl: './view.html'");
        File owner = write(dir, "owner.ts",
                "@Component({ templateUrl: './view.html' }) export class X {}");
        write(dir, "other.ts", "@Component({ templateUrl: './else.html' })");
        File html = write(dir, "view.html", "<p></p>");
        assertThat(NgSwitch.componentFor(html, NgSwitchActions::readQuietly))
                .isEqualTo(owner);
    }

    @Test
    @DisplayName("no owner anywhere: honest null")
    void noOwnerIsNull(@TempDir File dir) throws Exception {
        write(dir, "unrelated.ts", "export const x = 1;");
        File html = write(dir, "view.html", "<p></p>");
        assertThat(NgSwitch.componentFor(html, NgSwitchActions::readQuietly)).isNull();
    }

    @Test
    @DisplayName("templateUrl matching compares by file name, path prefixes ignored")
    void referencesByName() {
        assertThat(NgSwitch.referencesTemplate(
                "templateUrl: '../shared/view.html'", "view.html")).isTrue();
        assertThat(NgSwitch.referencesTemplate(
                "templateUrl: \"./preview.html\"", "view.html")).isFalse();
    }

    @Test
    @DisplayName("the template goto chord stays wired: mime keybindings bind Cmd+B to our action")
    void gotoChordPinned() throws Exception {
        String xml = Files.readString(new File(
                "src/main/resources/org/nmox/studio/editor/ng-template-keybindings.xml").toPath());
        assertThat(xml)
                .as("D-B must dispatch our delegating action on templates — the "
                        + "platform's global goto-declaration never consults the "
                        + "hyperlink chain on CSL panes (v1.219.0, measured live)")
                .contains("<bind actionName=\"ng-goto-declaration\" key=\"D-B\"/>");
        String layer = Files.readString(new File(
                "src/main/resources/org/nmox/studio/editor/layer.xml").toPath());
        assertThat(layer).contains("ng-template-keybindings.xml");
    }
}
