package org.nmox.studio.editor.angular;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The inline-template region parser (Angular-top arc): Emmet on a
 * TypeScript pane may expand ONLY inside {@code template: `...`} — a
 * wrong verdict either mangles code (false positive) or kills the
 * feature (false negative), so both directions are pinned here, and a
 * wiring gate keeps the TS registration + keybinding from silently
 * un-shipping (the v1.321.0 two-proof seam law).
 */
class NgInlineTemplateTest {

    private static final String COMPONENT = """
            import { Component } from '@angular/core';
            @Component({
              selector: 'app-hero',
              template: `
                <div class="hero"></div>
              `,
              styles: [`
                .hero { color: red; }
              `],
            })
            export class Hero {
              greet() { return `hi ${this.name}`; }
            }
            """;

    @Test
    @DisplayName("caret inside template backticks is in-span; code, styles and other literals are not")
    void spanVerdicts() {
        int inTemplate = COMPONENT.indexOf("hero\"");
        int[] span = NgInlineTemplate.spanAt(COMPONENT, inTemplate);
        assertThat(span).isNotNull();
        assertThat(COMPONENT.substring(span[0], span[1])).contains("<div class=\"hero\">");

        // a styles literal is CSS, not markup
        int inStyles = COMPONENT.indexOf("color: red");
        assertThat(NgInlineTemplate.spanAt(COMPONENT, inStyles)).isNull();
        // a plain template literal in a method is code
        int inGreet = COMPONENT.indexOf("hi ${");
        assertThat(NgInlineTemplate.spanAt(COMPONENT, inGreet)).isNull();
        // TypeScript outside any literal
        assertThat(NgInlineTemplate.spanAt(COMPONENT, COMPONENT.indexOf("export"))).isNull();
    }

    @Test
    @DisplayName("no @Component in the file → never a template; unterminated literal refuses")
    void gates() {
        String plain = "const t = { template: `not a component` };";
        assertThat(NgInlineTemplate.spanAt(plain, plain.indexOf("not")))
                .as("the template: key without a decorator is arbitrary TS").isNull();
        String unterminated = "@Component({ template: `<div>";
        assertThat(NgInlineTemplate.spanAt(unterminated, unterminated.length() - 2))
                .as("an unterminated literal must refuse, never guess a span").isNull();
    }

    @Test
    @DisplayName("escaped backticks stay inside; a second component's template resolves too")
    void escapesAndMultiple() {
        String two = "@Component({ template: `a \\` b` }) class A {}\n"
                + "@Component({ template: `<app-x></app-x>` }) class B {}\n";
        int inFirst = two.indexOf("a \\") + 1;
        int[] first = NgInlineTemplate.spanAt(two, inFirst);
        assertThat(first).isNotNull();
        assertThat(two.substring(first[0], first[1])).isEqualTo("a \\` b");
        int inSecond = two.indexOf("app-x");
        int[] second = NgInlineTemplate.spanAt(two, inSecond);
        assertThat(second).isNotNull();
        assertThat(two.substring(second[0], second[1])).isEqualTo("<app-x></app-x>");
    }

    @Test
    @DisplayName("wiring gate: the TS mime carries the Emmet action AND its chord")
    void wiringGate() throws Exception {
        Path action = Path.of("src/main/java/org/nmox/studio/editor/emmet/"
                + "ExpandAbbreviationAction.java");
        String src = Files.readString(action);
        assertThat(src)
                .as("the Emmet action must be registered for text/typescript")
                .contains("mimeType = \"text/typescript\"");
        assertThat(src)
                .as("the TS branch must gate on the inline-template span")
                .contains("NgInlineTemplate");
        String layer = Files.readString(Path.of(
                "src/main/resources/org/nmox/studio/editor/layer.xml"));
        int tsFolder = layer.indexOf("<folder name=\"typescript\">");
        assertThat(tsFolder)
                .as("layer.xml must carry an Editors/text/typescript folder").isPositive();
        assertThat(layer.indexOf("emmet-keybindings.xml", tsFolder))
                .as("the typescript folder must register the Emmet chord")
                .isPositive();
    }
}
