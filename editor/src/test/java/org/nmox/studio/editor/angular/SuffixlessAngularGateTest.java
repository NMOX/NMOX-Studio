package org.nmox.studio.editor.angular;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 73's two-proof wiring (v1.321.0 law): {@link NgTemplatesTest}
 * proves the sniff diverges; this gate proves the call sites exist —
 * the resolver rides the sniff, the LAYER carries the .instance in the
 * ordered folder (the ONLY registration channel that beats the
 * platform's html claim, proven live twice in v1.217.0 and again,
 * positively this time, on 2026-08-11), and the four-file switcher
 * handles a suffixless set.
 */
class SuffixlessAngularGateTest {

    @Test
    @DisplayName("the resolver delegates to the sniff and the layer registers it at 260")
    void resolverWired() throws Exception {
        String resolver = Files.readString(new File(
                "src/main/java/org/nmox/studio/editor/grammars/NgTemplateContentResolver.java").toPath());
        assertThat(resolver).contains("NgTemplates.isAngularTemplate(");
        String layer = Files.readString(new File(
                "src/main/resources/org/nmox/studio/editor/layer.xml").toPath());
        assertThat(layer)
                .as("the .instance must sit in the ordered Services/MIMEResolver"
                        + " folder — a ServiceProvider registration loses to the"
                        + " platform's html claim (v1.217.0, proven live)")
                .contains("org-nmox-studio-editor-grammars-NgTemplateContentResolver.instance");
        int at = layer.indexOf("NgTemplateContentResolver.instance");
        assertThat(layer.indexOf("intvalue=\"260\"", at))
                .as("position 260: after our name-keyed 250, before html at 300")
                .isBetween(at, at + 400);
    }

    @Test
    @DisplayName("the four-file switcher already speaks suffixless — pinned, not assumed")
    void switcherHandlesSuffixless(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("hero.ts"), """
                import { Component } from '@angular/core';
                @Component({ selector: 'app-hero', templateUrl: './hero.html' })
                export class Hero {}
                """);
        Files.writeString(dir.resolve("hero.html"), "<h1>{{title}}</h1>");
        Files.writeString(dir.resolve("hero.css"), ".hero {}");
        Files.writeString(dir.resolve("hero.spec.ts"), "describe('hero', () => {});");

        File component = dir.resolve("hero.ts").toFile();
        String source = Files.readString(dir.resolve("hero.ts"));
        assertThat(NgSwitch.templateFor(component, source))
                .isEqualTo(dir.resolve("hero.html").toFile());
        assertThat(NgSwitch.stylesFor(component, source))
                .isEqualTo(dir.resolve("hero.css").toFile());
        assertThat(NgSwitch.specFor(component))
                .isEqualTo(dir.resolve("hero.spec.ts").toFile());
        assertThat(NgSwitch.componentForSibling(dir.resolve("hero.html").toFile()))
                .isEqualTo(component);
        assertThat(NgSwitch.componentForSibling(dir.resolve("hero.spec.ts").toFile()))
                .isEqualTo(component);
    }
}
