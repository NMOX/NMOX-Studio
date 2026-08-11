package org.nmox.studio.editor.angular;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Suffixless Angular sets (ledger 73 → 82). The four-file switcher
 * speaks suffixless by its own file logic; the MIME half is a
 * TOMBSTONE: v1.346.0 shipped a programmatic content resolver as an
 * {@code .instance} in {@code Services/MIMEResolver}, and the ledger-77
 * bisect (2026-08-11) proved it structurally inert — decompiled,
 * {@code MIMESupport$CachedFileObject.getResolvers()} builds the chain
 * as declarative-XML resolvers FIRST (only {@code .xml} children of the
 * folder), then appends Lookup-provided instances, so the platform's
 * declarative ext=html claim answers before any {@code .instance}
 * regardless of position. The resolver was deleted; this gate keeps it
 * from coming back through the door that cannot work.
 */
class SuffixlessAngularGateTest {

    @Test
    @DisplayName("tombstone: no .instance MIMEResolver registration — that channel is structurally dead")
    void noInstanceResolverRegistration() throws Exception {
        String layer = Files.readString(new File(
                "src/main/resources/org/nmox/studio/editor/layer.xml").toPath());
        assertThat(layer)
                .as("an .instance in Services/MIMEResolver never precedes the"
                        + " declarative ext=html claim (decompiled getResolvers)"
                        + " — re-adding one ships dead code")
                .doesNotContain("MIMEResolver\">");
        assertThat(new File(
                "src/main/java/org/nmox/studio/editor/grammars/NgTemplateContentResolver.java"))
                .as("the inert resolver stays deleted; see ledger 82 for the"
                        + " mechanisms a real fix could use")
                .doesNotExist();
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
