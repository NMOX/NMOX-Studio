package org.nmox.studio.editor.debug;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.nmox.studio.rack.service.ServingRegistry;
import org.nmox.studio.rack.service.ServingRegistry.Kind;
import org.nmox.studio.rack.service.ServingRegistry.Serving;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * URL selection for "Debug in Chrome": a live rack serving for the project
 * beats everything, a containing serving covers monorepo lanes, an .html
 * file with no server falls back to file://, and a bare script with no
 * server is an honest null — there is no page that would load it.
 */
class BrowserDebugActionTest {

    @Test
    @DisplayName("a serving registered for the project root wins")
    void shouldPreferExactProjectServing(@TempDir File root) {
        File file = new File(root, "src/app.js");
        Serving mine = new Serving("dev-1", "IGNITION", "http://127.0.0.1:3000/", Kind.WEB, root);
        Serving other = new Serving("dev-2", "NPM", "http://127.0.0.1:9999/", Kind.WEB,
                new File(root.getParentFile(), "elsewhere"));

        assertThat(BrowserDebugAction.pickUrl(root, file, List.of(other, mine)))
                .isEqualTo("http://127.0.0.1:3000/");
    }

    @Test
    @DisplayName("CHAIN servings (ANVIL etc.) are never a page to debug")
    void shouldIgnoreChainServings(@TempDir File root) {
        File file = new File(root, "app.js");
        Serving chain = new Serving("dev-1", "ANVIL", "http://127.0.0.1:8545/", Kind.CHAIN, root);

        assertThat(BrowserDebugAction.pickUrl(root, file, List.of(chain))).isNull();
    }

    @Test
    @DisplayName("monorepo lane: a serving whose project dir contains the file matches")
    void shouldMatchContainingServing(@TempDir File repo) {
        File lane = new File(repo, "packages/web");
        File file = new File(lane, "src/main.js");
        Serving serving = new Serving("dev-1", "IGNITION", "http://127.0.0.1:5173/", Kind.WEB, lane);

        // the walked root differs (repo-level manifest), the serving still applies
        assertThat(BrowserDebugAction.pickUrl(repo, file, List.of(serving)))
                .isEqualTo("http://127.0.0.1:5173/");
    }

    @Test
    @DisplayName("sibling project's serving does not leak in via prefix confusion")
    void shouldNotMatchSiblingByPrefix(@TempDir File parent) {
        // /parent/web-site must NOT match a serving for /parent/web
        File servedDir = new File(parent, "web");
        File root = new File(parent, "web-site");
        File file = new File(root, "app.js");
        Serving serving = new Serving("dev-1", "IGNITION", "http://127.0.0.1:5173/", Kind.WEB, servedDir);

        assertThat(BrowserDebugAction.pickUrl(root, file, List.of(serving))).isNull();
    }

    @Test
    @DisplayName("no server + .html file: file:// URL of the file itself")
    void shouldFallBackToFileUrlForHtml(@TempDir File root) {
        File page = new File(root, "index.html");

        String url = BrowserDebugAction.pickUrl(root, page, List.of());

        assertThat(url).startsWith("file:").endsWith("index.html");
    }

    @Test
    @DisplayName("no server + bare script: null — no page would load it, say so")
    void shouldReturnNullForScriptWithoutServer(@TempDir File root) {
        assertThat(BrowserDebugAction.pickUrl(root, new File(root, "app.js"), List.of()))
                .isNull();
    }
}
