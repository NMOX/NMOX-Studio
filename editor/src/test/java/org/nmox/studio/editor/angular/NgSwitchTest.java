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
    @DisplayName("switcher resolution rides the RP, never the EDT (v1.220.0 review find)")
    void resolutionOffEdtGate() throws Exception {
        String src = Files.readString(new File(
                "src/main/java/org/nmox/studio/editor/angular/NgSwitchActions.java").toPath());
        assertThat(src)
                .as("resolution reads files (component source + sibling scans), so EVERY "
                        + "action must post to the named RP and hop back to the EDT only "
                        + "to open — a wedged mount must not freeze a context-menu click")
                .contains("RESOLVE_RP.post(")
                .contains("java.awt.EventQueue.invokeLater(");
        // v1.313.0: the family grew from 2 actions to 5 (styles, spec, and
        // styles→component joined template↔component). A frozen count would
        // just have to be bumped each time and would stop meaning anything —
        // tie it to the number of actions instead, so a NEW action that skips
        // the RP fails this gate by construction.
        int actions = src.split("public void actionPerformed\\(").length - 1;
        assertThat(src.split("RESOLVE_RP\\.post\\(").length - 1)
                .as("every action (%d of them) routes its file reads through the RP", actions)
                .isEqualTo(actions);
        assertThat(actions)
                .as("the switcher family has subjects")
                .isGreaterThanOrEqualTo(5);
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

    // ---- v1.313.0: the rest of the four-file set (styles, spec) ----

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("styleUrl (Angular 17+) and styleUrls (older) both resolve")
    void stylesFromEitherDecoratorSpelling(@org.junit.jupiter.api.io.TempDir
            java.nio.file.Path dir) throws Exception {
        File component = dir.resolve("hero.component.ts").toFile();
        Files.writeString(component.toPath(), "@Component({})");
        // The decorator points at a stylesheet that does NOT follow the
        // sibling convention. That is the divergent input: if the pattern
        // stops matching a spelling, the sibling fallback would quietly
        // return a DIFFERENT file, and a test using a convention-named URL
        // could never tell the two apart (the mutation-divergence lesson —
        // a same-name URL made an earlier version of this test pass against
        // a singular-only pattern).
        File shared = dir.resolve("theme-dark.css").toFile();
        Files.writeString(shared.toPath(), ".hero{}");
        File sibling = dir.resolve("hero.component.css").toFile();
        Files.writeString(sibling.toPath(), ".fallback{}");

        // Angular 17+ singular
        assertThat(NgSwitch.stylesFor(component,
                "@Component({ styleUrl: './theme-dark.css' })"))
                .isEqualTo(shared);
        // older plural array — the FIRST entry is what opens
        assertThat(NgSwitch.stylesFor(component,
                "@Component({ styleUrls: ['./theme-dark.css', './other.css'] })"))
                .as("the plural spelling must resolve too, not fall through to"
                        + " the same-name sibling")
                .isEqualTo(shared);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("styles fall back to the sibling in any ng-supported extension")
    void stylesFallBackAcrossExtensions(@org.junit.jupiter.api.io.TempDir
            java.nio.file.Path dir) throws Exception {
        File component = dir.resolve("hero.component.ts").toFile();
        Files.writeString(component.toPath(), "@Component({})");
        File scss = dir.resolve("hero.component.scss").toFile();
        Files.writeString(scss.toPath(), ".hero{}");
        // no decorator URL at all: the scss sibling is found (ng new --style=scss)
        assertThat(NgSwitch.stylesFor(component, "@Component({})")).isEqualTo(scss);
        // an inline-styles component with no sibling has none — say so honestly
        File styleless = dir.resolve("plain.component.ts").toFile();
        Files.writeString(styleless.toPath(), "@Component({ styles: ['h1{}'] })");
        assertThat(NgSwitch.stylesFor(styleless, "@Component({ styles: ['h1{}'] })")).isNull();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("spec resolves beside the component; a spec has no spec")
    void specBesideComponent(@org.junit.jupiter.api.io.TempDir
            java.nio.file.Path dir) throws Exception {
        File component = dir.resolve("hero.component.ts").toFile();
        Files.writeString(component.toPath(), "export class HeroComponent {}");
        File spec = dir.resolve("hero.component.spec.ts").toFile();
        Files.writeString(spec.toPath(), "describe('HeroComponent', () => {});");

        assertThat(NgSwitch.specFor(component)).isEqualTo(spec);
        // --skip-tests is a real choice: absence is reported, not invented
        File noSpec = dir.resolve("bare.component.ts").toFile();
        Files.writeString(noSpec.toPath(), "export class BareComponent {}");
        assertThat(NgSwitch.specFor(noSpec)).isNull();
        // a spec has no spec of its own — keeps the refusal message truthful
        assertThat(NgSwitch.specFor(spec)).isNull();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("styles→component is registered on scss and less, not just css")
    void stylesheetActionCoversPreprocessorMimes() throws Exception {
        // The v1.230.0 finding: css-prep resolves .scss/.less to their OWN
        // mimes before our resolvers see them, so a text/css-only popup
        // registration never reaches the stylesheets Angular projects
        // actually use. The v1.313.0 live walk caught exactly that — the
        // menu item was absent on a real .scss editor — so pin all three.
        String src = Files.readString(new File(
                "src/main/java/org/nmox/studio/editor/angular/NgSwitchActions.java")
                .toPath()).replace("\r\n", "\n");
        assertThat(src).contains("Editors/text/css/Popup");
        assertThat(src)
                .as("ng new --style=scss is the common choice; the action must"
                        + " reach .scss files")
                .contains("Editors/text/scss/Popup");
        assertThat(src).contains("Editors/text/less/Popup");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a stylesheet or spec resolves back to its component by convention")
    void componentForSiblingBothDirections(@org.junit.jupiter.api.io.TempDir
            java.nio.file.Path dir) throws Exception {
        File component = dir.resolve("hero.component.ts").toFile();
        Files.writeString(component.toPath(), "export class HeroComponent {}");
        File scss = dir.resolve("hero.component.scss").toFile();
        Files.writeString(scss.toPath(), ".hero{}");
        File spec = dir.resolve("hero.component.spec.ts").toFile();
        Files.writeString(spec.toPath(), "describe('x', () => {});");

        assertThat(NgSwitch.componentForSibling(scss)).isEqualTo(component);
        // the .spec.ts strip must beat the plain last-extension strip, or a
        // spec would resolve to "hero.component.spec.ts" -> "hero.component.ts"
        // only by accident — assert it explicitly
        assertThat(NgSwitch.componentForSibling(spec)).isEqualTo(component);
        // an orphan stylesheet has no component: null, not a guess
        File orphan = dir.resolve("theme.css").toFile();
        Files.writeString(orphan.toPath(), "body{}");
        assertThat(NgSwitch.componentForSibling(orphan)).isNull();
    }
}
