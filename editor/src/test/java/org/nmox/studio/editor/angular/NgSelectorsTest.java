package org.nmox.studio.editor.angular;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The selector index (Angular-top arc): {@code <app-hero>} must jump
 * to its component WITHOUT the language service — and only to real
 * decorator selectors, never to a {@code selector:} key in arbitrary
 * TypeScript.
 */
class NgSelectorsTest {

    @TempDir
    Path root;

    @BeforeEach
    void fresh() {
        NgSelectors.clearCacheForTest();
    }

    private void ts(String rel, String body) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent() == null ? root : p.getParent());
        Files.writeString(p, body);
    }

    @Test
    @DisplayName("finds the component by element selector, suffixless or not")
    void findsByTag() throws Exception {
        ts("src/app/hero.ts", """
                import { Component } from '@angular/core';
                @Component({ selector: 'app-hero', templateUrl: './hero.html' })
                export class Hero {}
                """);
        NgSelectors.Decl d = NgSelectors.find(root.toFile(), "app-hero");
        assertThat(d).isNotNull();
        assertThat(d.file().getName()).isEqualTo("hero.ts");
        assertThat(d.selector()).isEqualTo("app-hero");
        assertThat(NgSelectors.find(root.toFile(), "app-missing")).isNull();
    }

    @Test
    @DisplayName("a comma-list selector matches any trimmed part; directives count too")
    void commaListAndDirectives() throws Exception {
        ts("src/app/multi.ts", """
                import { Component } from '@angular/core';
                @Component({ selector: 'app-a, app-b', template: '<b></b>' })
                export class Multi {}
                """);
        ts("src/app/dir.ts", """
                import { Directive } from '@angular/core';
                @Directive({ selector: '[appTrack]' })
                export class Track {}
                """);
        assertThat(NgSelectors.find(root.toFile(), "app-b")).isNotNull();
        assertThat(NgSelectors.find(root.toFile(), "[appTrack]")).isNotNull();
    }

    @Test
    @DisplayName("a selector: key WITHOUT a decorator is not a jump target")
    void decoratorGated() throws Exception {
        ts("src/config.ts", """
                export const options = { selector: 'app-fake' };
                """);
        ts("src/app/hero.spec.ts", """
                import { Component } from '@angular/core';
                @Component({ selector: 'app-spec-only', template: '' })
                class Fixture {}
                """);
        assertThat(NgSelectors.find(root.toFile(), "app-fake"))
                .as("no @Component/@Directive in the file — a config literal"
                        + " must not become a navigation target").isNull();
        assertThat(NgSelectors.find(root.toFile(), "app-spec-only"))
                .as("spec files are excluded from the walk").isNull();
    }

    @Test
    @DisplayName("the index follows edits (mtime+size cache) and skips node_modules")
    void cacheAndSkips() throws Exception {
        ts("node_modules/lib/fake.ts", """
                import { Component } from '@angular/core';
                @Component({ selector: 'app-vendored', template: '' })
                export class Vendored {}
                """);
        assertThat(NgSelectors.find(root.toFile(), "app-vendored")).isNull();

        ts("src/app/late.ts", "export class Late {}");
        assertThat(NgSelectors.find(root.toFile(), "app-late")).isNull();
        ts("src/app/late.ts", """
                import { Component } from '@angular/core';
                @Component({ selector: 'app-late', template: '<i></i>' })
                export class Late {}
                """);
        assertThat(NgSelectors.find(root.toFile(), "app-late")).isNotNull();
    }

    @Test
    @DisplayName("the hyperlink span is a dashed tag directly after < or </, nothing else")
    void tagSpan() {
        String t = "<div><app-hero data-x=\"1\"></app-hero></div>";
        int inOpen = t.indexOf("app-hero") + 2;
        int inClose = t.indexOf("app-hero", t.indexOf("</")) + 2;
        assertThat(NgSelectorHyperlink.tagSpanAt(t, inOpen))
                .containsExactly(t.indexOf("app-hero"), t.indexOf("app-hero") + 8);
        assertThat(NgSelectorHyperlink.tagSpanAt(t, inClose)).isNotNull();
        // <div> is a platform tag; data-x is a dashed ATTRIBUTE — neither links
        assertThat(NgSelectorHyperlink.tagSpanAt(t, 1)).isNull();
        assertThat(NgSelectorHyperlink.tagSpanAt(t, t.indexOf("data-x") + 2)).isNull();
    }
}
