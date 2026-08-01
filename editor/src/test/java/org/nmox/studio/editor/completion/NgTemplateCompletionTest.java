package org.nmox.studio.editor.completion;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure rules behind Angular template completion (v1.217.0): the
 * {@code @}-block and {@code *}-directive triggers, and the workspace
 * gate that keeps plain HTML quiet.
 */
class NgTemplateCompletionTest {

    private static List<String> inserts(String lineToCaret) {
        return NgTemplateCompletion.items(lineToCaret).stream()
                .map(NgTemplateCompletion.Item::insert).toList();
    }

    @Test
    @DisplayName("@ at markup level offers the control-flow blocks")
    void atOffersBlocks() {
        assertThat(inserts("  @")).contains("@if", "@for", "@switch", "@defer", "@let");
        assertThat(inserts("  @i")).containsExactly("@if");
        assertThat(inserts("  @de")).containsExactly("@default", "@defer");
        assertThat(inserts("</div> @e")).contains("@else", "@else if", "@empty", "@error");
    }

    @Test
    @DisplayName("@ inside a word is not a block start")
    void atInsideWordIsSilent() {
        // an email address in text, a decorator in a code sample
        assertThat(inserts("contact me at david@")).isEmpty();
        assertThat(inserts("user@e")).isEmpty();
    }

    @Test
    @DisplayName("* inside a tag offers the structural directives")
    void starOffersDirectives() {
        assertThat(inserts("<div *")).contains("*ngIf", "*ngFor", "*ngSwitch");
        assertThat(inserts("<div *ngi")).containsExactly("*ngIf");
        assertThat(inserts("<li *ngF")).containsExactly("*ngFor");
    }

    @Test
    @DisplayName("* outside a tag is markup text, not a directive")
    void starOutsideTagIsSilent() {
        assertThat(inserts("5 * 3 = 15 *")).isEmpty();
        assertThat(inserts("<b>bold</b> *emph*")).isEmpty();
    }

    @Test
    @DisplayName("no trigger, no items")
    void noTriggerNoItems() {
        assertThat(inserts("<div class=\"row\">")).isEmpty();
        assertThat(inserts("")).isEmpty();
        assertThat(inserts(null)).isEmpty();
    }

    @Test
    @DisplayName("the workspace gate: angular.json above the file, bounded walk")
    void workspaceGate(@TempDir File root) throws Exception {
        File app = new File(root, "src/app");
        assertThat(app.mkdirs()).isTrue();
        File template = new File(app, "hello.component.html");
        Files.writeString(template.toPath(), "<p></p>");

        assertThat(NgTemplateCompletion.inAngularWorkspace(template))
                .as("no angular.json anywhere").isFalse();

        Files.writeString(new File(root, "angular.json").toPath(), "{}");
        assertThat(NgTemplateCompletion.inAngularWorkspace(template))
                .as("angular.json two levels up").isTrue();
    }

    @Test
    @DisplayName("the insert replaces from the trigger character")
    void triggerOffsetPointsAtTheTrigger() {
        assertThat(NgTemplateCompletion.triggerOffset("  @i")).isEqualTo(2);
        assertThat(NgTemplateCompletion.triggerOffset("<div *ng")).isEqualTo(5);
    }
}
