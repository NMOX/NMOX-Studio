package org.nmox.studio.rack.blockstudio;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Block Studio v2 (v1.80.0): the three new pieces — SLOT, TIMER,
 * DISPATCH — and the in-memory live-preview server. Interlock law,
 * generated lifecycle code, event dispatch, validation messages, range
 * mapping, and the server's live-supplier semantics are all pinned.
 */
class BlocksV2Test {

    // ---- interlock law ----

    @Test
    @DisplayName("Interlocks: slots nest in structure, timers in the component, dispatch in logic")
    void v2Interlocks() {
        assertThat(BlockRules.accepts(BlockKind.COMPONENT, BlockKind.SLOT)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.ELEMENT, BlockKind.SLOT)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.COMPONENT, BlockKind.TIMER)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.ON_EVENT, BlockKind.DISPATCH)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.IF_STATE, BlockKind.DISPATCH)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.TIMER, BlockKind.SET_STATE)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.TIMER, BlockKind.DISPATCH)).isTrue();
        // and the refusals that keep the canvas honest
        assertThat(BlockRules.accepts(BlockKind.ELEMENT, BlockKind.TIMER)).isFalse();
        assertThat(BlockRules.accepts(BlockKind.SLOT, BlockKind.TEXT)).isFalse();
        assertThat(BlockRules.accepts(BlockKind.COMPONENT, BlockKind.DISPATCH)).isFalse();
        assertThat(BlockRules.accepts(BlockKind.TIMER, BlockKind.ELEMENT)).isFalse();
    }

    // ---- codegen ----

    private static BlockDoc counterWith(BlockKind extra, String... params) {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "my-counter");
        Block state = doc.create(BlockKind.STATE);
        state.setParam("name", "count");
        doc.insert(doc.root(), state, 0);
        Block b = doc.create(extra);
        for (int i = 0; i < params.length; i += 2) {
            b.setParam(params[i], params[i + 1]);
        }
        doc.insert(doc.root(), b, 1);
        return doc;
    }

    private static Block addChild(BlockDoc doc, Block parent, BlockKind kind, String... params) {
        Block b = doc.create(kind);
        for (int i = 0; i < params.length; i += 2) {
            b.setParam(params[i], params[i + 1]);
        }
        doc.insert(parent, b, parent.children().size());
        return b;
    }

    @Test
    @DisplayName("Slots render named and unnamed; both carry ranges")
    void slotCodegen() {
        BlockDoc doc = counterWith(BlockKind.SLOT);
        var r = BlockCodegen.generate(doc);
        assertThat(r.code()).contains("<slot></slot>");

        BlockDoc named = counterWith(BlockKind.SLOT, "name", "header");
        var r2 = BlockCodegen.generate(named);
        assertThat(r2.code()).contains("<slot name=\"header\"></slot>");
        String slotId = named.root().children().get(1).id();
        int[] range = r2.ranges().get(slotId);
        assertThat(range).isNotNull();
        assertThat(r2.code().substring(range[0], range[1])).contains("<slot");
    }

    @Test
    @DisplayName("Timers generate the connected/disconnected lifecycle with cleanup and re-render")
    void timerCodegen() {
        BlockDoc doc = counterWith(BlockKind.TIMER, "ms", "250");
        Block timer = doc.root().children().get(1);
        addChild(doc, timer, BlockKind.SET_STATE, "name", "count", "expr", "{count} + 1");

        String code = BlockCodegen.generate(doc).code();
        assertThat(code).contains("connectedCallback()");
        assertThat(code).contains("this._t0 = setInterval(() => {");
        assertThat(code).contains("}, 250);");
        assertThat(code).contains("this.render();"); // SET_STATE ⇒ re-render in the tick
        assertThat(code).contains("disconnectedCallback()");
        assertThat(code).contains("clearInterval(this._t0);");
    }

    @Test
    @DisplayName("Toggle-class under a timer acts on the host component itself")
    void timerToggleTargetsHost() {
        BlockDoc doc = counterWith(BlockKind.TIMER, "ms", "500");
        Block timer = doc.root().children().get(1);
        addChild(doc, timer, BlockKind.TOGGLE_CLASS);
        String code = BlockCodegen.generate(doc).code();
        assertThat(code).contains("this.classList.toggle('active');");
    }

    @Test
    @DisplayName("Dispatch emits a bubbling composed CustomEvent; detail interpolates state and escapes")
    void dispatchCodegen() {
        BlockDoc doc = counterWith(BlockKind.TIMER, "ms", "1000");
        Block timer = doc.root().children().get(1);
        addChild(doc, timer, BlockKind.DISPATCH,
                "event", "count-changed", "detail", "count is {count} ` ${evil}");

        String code = BlockCodegen.generate(doc).code();
        assertThat(code).contains(
                "this.dispatchEvent(new CustomEvent('count-changed', { bubbles: true, composed: true, detail: `");
        assertThat(code).contains("${this.#count}");   // {count} interpolated
        assertThat(code).contains("\\`");               // backtick escaped
        assertThat(code).contains("\\${evil}");         // raw ${ escaped
    }

    @Test
    @DisplayName("Validation: bad timer interval, bad dispatch event, bad slot name — human sentences")
    void v2Validation() {
        assertThat(BlockCodegen.validate(counterWith(BlockKind.TIMER, "ms", "abc")))
                .anyMatch(p -> p.contains("Timer interval"));
        assertThat(BlockCodegen.validate(counterWith(BlockKind.TIMER, "ms", "10")))
                .anyMatch(p -> p.contains("min 50"));
        // a dispatch with a bad event name, under its legal timer parent —
        // (inserting under COMPONENT is refused by the interlock law itself)
        BlockDoc bad = counterWith(BlockKind.TIMER, "ms", "1000");
        addChild(bad, bad.root().children().get(1), BlockKind.DISPATCH, "event", "Bad Event!");
        assertThat(BlockCodegen.validate(bad)).anyMatch(p -> p.contains("Dispatch event"));
        BlockDoc doc = counterWith(BlockKind.SLOT, "name", "no spaces");
        assertThat(BlockCodegen.validate(doc)).anyMatch(p -> p.contains("Slot name"));
    }

    // ---- the live-preview server ----

    @Test
    @DisplayName("Preview server: harness mounts the tag, /component.js is live, stop kills it")
    void previewServer() throws Exception {
        AtomicReference<String> code = new AtomicReference<>("// v1");
        BlockPreviewServer server = new BlockPreviewServer(() -> "my-widget", code::get);
        String url = server.start();
        assertThat(url).startsWith("http://127.0.0.1:");
        try {
            HttpClient http = HttpClient.newHttpClient();
            String harness = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            assertThat(harness).contains("<my-widget></my-widget>");
            assertThat(harness).contains("/component.js");
            assertThat(harness).contains("slotted from the page"); // slot showcase
            assertThat(harness).contains("events dispatched");     // dispatch showcase

            String js = http.send(HttpRequest.newBuilder(URI.create(url + "component.js")).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            assertThat(js).isEqualTo("// v1");

            // the supplier is read per request — edits are live on refresh
            code.set("// v2");
            String js2 = http.send(HttpRequest.newBuilder(URI.create(url + "component.js")).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            assertThat(js2).isEqualTo("// v2");
        } finally {
            server.stop();
        }
        assertThat(server.running()).isFalse();
        HttpClient http = HttpClient.newHttpClient();
        assertThatThrownBy(() -> http.send(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    @DisplayName("Preview server: start is idempotent; harness escapes the tag in text positions")
    void previewServerIdempotentAndEscaped() throws Exception {
        BlockPreviewServer server = new BlockPreviewServer(() -> "my-widget", () -> "//");
        try {
            String url = server.start();
            assertThat(server.start()).isEqualTo(url);
            String harness = BlockPreviewServer.harness("my-widget");
            assertThat(harness).contains("&lt;my-widget&gt;"); // heading escaped
        } finally {
            server.stop();
        }
    }
}
