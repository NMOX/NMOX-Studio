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
 * Ledger 73 (David's call, 2026-08-11): suffixless Angular templates
 * are recognized by CONTENT — a same-basename {@code .ts} sibling
 * carrying {@code @Component}. The discriminator must be exactly that
 * narrow: a web-components {@code foo.ts} without the decorator, or an
 * {@code index.html} with no twin at all, stays plain HTML.
 */
class NgTemplatesTest {

    @TempDir
    Path dir;

    @BeforeEach
    void freshCache() {
        NgTemplates.clearCacheForTest();
    }

    private File pair(String base, String tsBody) throws Exception {
        Files.writeString(dir.resolve(base + ".ts"), tsBody);
        Path html = dir.resolve(base + ".html");
        Files.writeString(html, "<h1>{{title}}</h1>");
        return html.toFile();
    }

    @Test
    @DisplayName("a suffixless pair with @Component is a template")
    void suffixlessComponent() throws Exception {
        File html = pair("widget", """
                import { Component } from '@angular/core';
                @Component({ selector: 'app-widget', templateUrl: './widget.html' })
                export class Widget {}
                """);
        assertThat(NgTemplates.isAngularTemplate(html)).isTrue();
    }

    @Test
    @DisplayName("a sibling WITHOUT the decorator is not evidence — plain html stays plain")
    void plainSibling() throws Exception {
        File html = pair("demo", """
                // a lit web component, not Angular
                export class Demo extends HTMLElement {}
                customElements.define('x-demo', Demo);
                """);
        assertThat(NgTemplates.isAngularTemplate(html)).isFalse();
    }

    @Test
    @DisplayName("no twin, no claim: index.html and lone pages stay html")
    void noSibling() throws Exception {
        Path html = dir.resolve("index.html");
        Files.writeString(html, "<!doctype html><title>app</title>");
        assertThat(NgTemplates.isAngularTemplate(html.toFile())).isFalse();
        assertThat(NgTemplates.isAngularTemplate(null)).isFalse();
        assertThat(NgTemplates.isAngularTemplate(new File(dir.toFile(), "style.css"))).isFalse();
    }

    @Test
    @DisplayName("the verdict follows the sibling's edits (mtime+size cache key)")
    void cacheFollowsEdits() throws Exception {
        File html = pair("hero", "export class Hero {}");
        assertThat(NgTemplates.isAngularTemplate(html)).isFalse();
        // the file BECOMES a component — same path, new content; pad the
        // body so size diverges even on coarse-mtime filesystems
        Files.writeString(dir.resolve("hero.ts"), """
                import { Component } from '@angular/core';
                @Component({ selector: 'app-hero', templateUrl: './hero.html' })
                export class Hero {}
                """);
        assertThat(NgTemplates.isAngularTemplate(html)).isTrue();
    }

    @Test
    @DisplayName("the decorator past the sniff cap is honestly missed — the cap is the contract")
    void capIsHonest() throws Exception {
        String padding = "// license header\n".repeat(NgTemplates.SNIFF_BYTES / 16);
        File html = pair("late", padding + "\n@Component({})\nexport class Late {}");
        // a real component file declares its decorator early; one that
        // buries it past 8k of prose is out of contract — pinned so the
        // cap can't silently grow into an unbounded read
        assertThat(NgTemplates.isAngularTemplate(html)).isFalse();
    }
}
