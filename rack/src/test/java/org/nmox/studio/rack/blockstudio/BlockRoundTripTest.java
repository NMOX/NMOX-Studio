package org.nmox.studio.rack.blockstudio;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The round-trip law (v1.81.0): {@code generate(parse(code)) == code}
 * byte for byte, across the whole piece vocabulary — the property that
 * makes Block Studio a real two-way controller. Corpus docs are built
 * through the checked {@link BlockDoc} API so every case is a tree the
 * canvas could genuinely produce.
 */
class BlockRoundTripTest {

    // ---- corpus ----

    static List<NamedDoc> corpus() {
        return List.of(
                new NamedDoc("minimal", minimal()),
                new NamedDoc("counter", counter()),
                new NamedDoc("everyPiece", everyPiece()),
                new NamedDoc("escaping", escaping()),
                new NamedDoc("deepNesting", deepNesting()),
                new NamedDoc("multiListenerMultiTimer", multiListenerMultiTimer()));
    }

    record NamedDoc(String name, BlockDoc doc) {

        @Override
        public String toString() {
            return name;
        }
    }

    private static Block add(BlockDoc doc, Block parent, BlockKind kind, String... kv) {
        Block b = doc.create(kind);
        for (int i = 0; i < kv.length; i += 2) {
            b.setParam(kv[i], kv[i + 1]);
        }
        assertThat(doc.insert(parent, b, parent.children().size()))
                .as("insert " + kind + " into " + parent.kind()).isTrue();
        return b;
    }

    static BlockDoc minimal() {
        return new BlockDoc();
    }

    static BlockDoc counter() {
        BlockDoc doc = new BlockDoc();
        add(doc, doc.root(), BlockKind.STATE, "name", "count", "initial", "0");
        Block div = add(doc, doc.root(), BlockKind.ELEMENT, "tag", "div");
        add(doc, div, BlockKind.TEXT, "text", "clicks: {count}");
        Block on = add(doc, div, BlockKind.ON_EVENT, "event", "click");
        add(doc, on, BlockKind.SET_STATE, "name", "count", "expr", "{count} + 1");
        return doc;
    }

    static BlockDoc everyPiece() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "kitchen-sink");
        add(doc, doc.root(), BlockKind.STATE, "name", "count", "initial", "0");
        add(doc, doc.root(), BlockKind.STATE, "name", "label", "initial", "ready");
        add(doc, doc.root(), BlockKind.PROP, "name", "title-text", "default", "untitled");
        add(doc, doc.root(), BlockKind.PROP, "name", "mode", "default", "");
        add(doc, doc.root(), BlockKind.TEXT, "text", "prop says {@title-text}");
        Block div = add(doc, doc.root(), BlockKind.ELEMENT, "tag", "section");
        add(doc, div, BlockKind.SET_ATTR, "name", "class", "value", "card {@mode}");
        add(doc, div, BlockKind.STYLE, "css", "padding: 4px");
        add(doc, div, BlockKind.TEXT, "text", "state {count} and {@title-text}");
        add(doc, div, BlockKind.SLOT, "name", "");
        add(doc, div, BlockKind.SLOT, "name", "footer");
        Block on = add(doc, div, BlockKind.ON_EVENT, "event", "click");
        add(doc, on, BlockKind.SET_STATE, "name", "count", "expr", "{count} + 1");
        add(doc, on, BlockKind.TOGGLE_CLASS, "class", "active");
        add(doc, on, BlockKind.LOG, "message", "count is {count}, title {@title-text}");
        Block iff = add(doc, on, BlockKind.IF_STATE, "name", "count", "op", ">", "value", "3");
        add(doc, iff, BlockKind.DISPATCH, "event", "count-high", "detail", "at {count}");
        Block timer = add(doc, doc.root(), BlockKind.TIMER, "ms", "500");
        add(doc, timer, BlockKind.TOGGLE_CLASS, "class", "blink");
        return doc;
    }

    static BlockDoc escaping() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "tricky-text");
        add(doc, doc.root(), BlockKind.STATE, "name", "x", "initial", "it's");
        Block div = add(doc, doc.root(), BlockKind.ELEMENT, "tag", "div");
        add(doc, div, BlockKind.TEXT, "text", "back`tick ${not-a-ref} slash\\ {x} {undeclared}");
        add(doc, div, BlockKind.SET_ATTR, "name", "data-note", "value", "quote\" and `tick`");
        Block on = add(doc, div, BlockKind.ON_EVENT, "event", "focus");
        add(doc, on, BlockKind.LOG, "message", "raw ` and \\ and ${ here");
        return doc;
    }

    static BlockDoc deepNesting() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "deep-tree");
        add(doc, doc.root(), BlockKind.STATE, "name", "n", "initial", "1");
        Block outer = add(doc, doc.root(), BlockKind.ELEMENT, "tag", "div");
        Block mid = add(doc, outer, BlockKind.ELEMENT, "tag", "ul");
        Block li = add(doc, mid, BlockKind.ELEMENT, "tag", "li");
        add(doc, li, BlockKind.TEXT, "text", "level three {n}");
        Block on = add(doc, li, BlockKind.ON_EVENT, "event", "click");
        Block if1 = add(doc, on, BlockKind.IF_STATE, "name", "n", "op", "<", "value", "10");
        Block if2 = add(doc, if1, BlockKind.IF_STATE, "name", "n", "op", "!=", "value", "5");
        add(doc, if2, BlockKind.SET_STATE, "name", "n", "expr", "{n} * 2");
        return doc;
    }

    static BlockDoc multiListenerMultiTimer() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "busy-widget");
        add(doc, doc.root(), BlockKind.STATE, "name", "a", "initial", "0");
        Block one = add(doc, doc.root(), BlockKind.ELEMENT, "tag", "button");
        add(doc, one, BlockKind.TEXT, "text", "first");
        Block onOne = add(doc, one, BlockKind.ON_EVENT, "event", "click");
        add(doc, onOne, BlockKind.SET_STATE, "name", "a", "expr", "{a} + 1");
        Block two = add(doc, doc.root(), BlockKind.ELEMENT, "tag", "button");
        add(doc, two, BlockKind.TEXT, "text", "second");
        Block onTwo = add(doc, two, BlockKind.ON_EVENT, "event", "dblclick");
        add(doc, onTwo, BlockKind.LOG, "message", "second pressed");
        Block t1 = add(doc, doc.root(), BlockKind.TIMER, "ms", "100");
        add(doc, t1, BlockKind.LOG, "message", "tick");
        Block t2 = add(doc, doc.root(), BlockKind.TIMER, "ms", "2000");
        add(doc, t2, BlockKind.SET_STATE, "name", "a", "expr", "0");
        return doc;
    }

    // ---- the law ----

    @ParameterizedTest
    @MethodSource("corpus")
    @DisplayName("generate(parse(generate(doc))) reproduces the code byte for byte")
    void codeFixpoint(NamedDoc named) {
        String code = BlockCodegen.generate(named.doc()).code();
        BlockDoc reparsed = BlockParser.parse(code);
        assertThat(BlockCodegen.generate(reparsed).code())
                .as(named.name() + " round trip").isEqualTo(code);
    }

    @ParameterizedTest
    @MethodSource("corpus")
    @DisplayName("parse recovers the structural tree: kinds and params, in order")
    void structureSurvives(NamedDoc named) {
        String code = BlockCodegen.generate(named.doc()).code();
        BlockDoc reparsed = BlockParser.parse(code);
        assertThat(shape(reparsed.root())).isEqualTo(shape(canonical(named.doc()).root()));
    }

    /** kind(params)[children...] — id-free structural fingerprint. */
    private static String shape(Block b) {
        StringBuilder sb = new StringBuilder(b.kind().name());
        sb.append(b.params().toString());
        sb.append("[");
        for (Block c : b.children()) {
            sb.append(shape(c));
        }
        return sb.append("]").toString();
    }

    /**
     * The parser reconstructs root children in canonical order (states,
     * props, template, timers) — generate reads by kind so the code is
     * identical either way. Reorder the original the same way before
     * comparing shapes.
     */
    private static BlockDoc canonical(BlockDoc doc) {
        BlockDoc out = BlockDoc.fromJson(doc.toJson());
        canonicalize(out.root());
        return out;
    }

    /** Root: states, props, template, timers. Elements: attrs/styles
     *  (they live in the open tag), template children, listeners. The
     *  generated CODE is identical either way — generate() reads each
     *  group by kind — so this is purely the comparator's ordering. */
    private static void canonicalize(Block b) {
        if (b.kind() == BlockKind.COMPONENT || b.kind() == BlockKind.ELEMENT) {
            b.children().sort((x, y) -> Integer.compare(group(b, x), group(b, y)));
        }
        for (Block c : b.children()) {
            canonicalize(c);
        }
    }

    private static int group(Block parent, Block b) {
        if (parent.kind() == BlockKind.ELEMENT) {
            return switch (b.kind()) {
                case SET_ATTR, STYLE -> 0;
                case ON_EVENT -> 2;
                default -> 1;
            };
        }
        return switch (b.kind()) {
            case STATE -> 0;
            case PROP -> 1;
            case TIMER -> 3;
            default -> 2;
        };
    }

    @Test
    @DisplayName("data-b anchors and listener variables keep their ids across the trip")
    void anchoredIdsSurvive(){
        BlockDoc doc = counter();
        String code = BlockCodegen.generate(doc).code();
        BlockDoc reparsed = BlockParser.parse(code);
        // the ids that appear in code are exactly the element-with-listener + ON_EVENT ids
        List<Block> original = doc.preorder().stream()
                .filter(b -> b.kind() == BlockKind.ON_EVENT).toList();
        for (Block on : original) {
            assertThat(reparsed.find(on.id())).as("listener id " + on.id()).isNotNull();
            assertThat(reparsed.find(doc.parentOf(on.id()).id()))
                    .as("host element id").isNotNull();
        }
        assertThat(BlockCodegen.generate(reparsed).code()).isEqualTo(code);
    }

    @Test
    @DisplayName("prop defaults ride the accessors — recovered without any interpolation site")
    void propDefaultsSurvive() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "prop-only");
        Block p = doc.create(BlockKind.PROP);
        p.setParam("name", "data-role");
        p.setParam("default", "guest user");
        assertThat(doc.insert(doc.root(), p, 0)).isTrue();
        BlockDoc reparsed = BlockParser.parse(BlockCodegen.generate(doc).code());
        Block prop = reparsed.preorder().stream()
                .filter(b -> b.kind() == BlockKind.PROP).findFirst().orElseThrow();
        assertThat(prop.param("name")).isEqualTo("data-role");
        assertThat(prop.param("default")).isEqualTo("guest user");
    }

    // ---- refusals ----

    @Test
    @DisplayName("a file without the marker is refused at line 1")
    void noMarkerRefused() {
        assertThatThrownBy(() -> BlockParser.parse("class X extends HTMLElement {}\n"))
                .isInstanceOf(BlockParser.ParseException.class)
                .hasMessageContaining("line 1").hasMessageContaining("marker");
    }

    @Test
    @DisplayName("a tampered skeleton line is refused with its line number")
    void tamperedSkeletonRefused() {
        String code = BlockCodegen.generate(counter()).code()
                .replace("this.attachShadow({ mode: 'open' });",
                         "this.attachShadow({ mode: 'closed' });");
        assertThatThrownBy(() -> BlockParser.parse(code))
                .isInstanceOf(BlockParser.ParseException.class)
                .hasMessageContaining("attachShadow");
    }

    @Test
    @DisplayName("a re-render line without any Set-state is refused (consistency, not trust)")
    void renderWithoutSetStateRefused() {
        String code = BlockCodegen.generate(counter()).code()
                .replace("      this.#count = this.#count + 1;\n", "");
        assertThatThrownBy(() -> BlockParser.parse(code))
                .isInstanceOf(BlockParser.ParseException.class)
                .hasMessageContaining("re-render");
    }

    @Test
    @DisplayName("a class name that does not match the tag is refused")
    void classTagMismatchRefused() {
        String code = BlockCodegen.generate(counter()).code()
                .replace("customElements.define('my-widget', MyWidget);",
                         "customElements.define('other-widget', MyWidget);");
        assertThatThrownBy(() -> BlockParser.parse(code))
                .isInstanceOf(BlockParser.ParseException.class)
                .hasMessageContaining("does not match tag");
    }

    @Test
    @DisplayName("an in-dialect hand edit imports fine — the honest two-way slice")
    void inDialectHandEditImports() {
        String code = BlockCodegen.generate(counter()).code()
                .replace("clicks: ${this.#count}", "presses: ${this.#count}");
        BlockDoc doc = BlockParser.parse(code);
        Block text = doc.preorder().stream()
                .filter(b -> b.kind() == BlockKind.TEXT).findFirst().orElseThrow();
        assertThat(text.param("text")).isEqualTo("presses: {count}");
        // and the edit round-trips from here on
        assertThat(BlockCodegen.generate(doc).code()).isEqualTo(code);
    }

    @Test
    @DisplayName("PROP emits accessors, observedAttributes and the re-render callback")
    void propCodegenShape() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "prop-shape");
        Block p = doc.create(BlockKind.PROP);
        p.setParam("name", "label");
        p.setParam("default", "hi");
        assertThat(doc.insert(doc.root(), p, 0)).isTrue();
        Block t = doc.create(BlockKind.TEXT);
        t.setParam("text", "says {@label}");
        assertThat(doc.insert(doc.root(), t, 1)).isTrue();
        String code = BlockCodegen.generate(doc).code();
        assertThat(code).contains("#prop_label() { return this.getAttribute('label') ?? 'hi'; }");
        assertThat(code).contains("static get observedAttributes() { return ['label']; }");
        assertThat(code).contains("attributeChangedCallback() {");
        assertThat(code).contains("says ${this.#prop_label()}");
    }

    @Test
    @DisplayName("an undeclared {@prop} stays literal text, like undeclared {state}")
    void undeclaredPropStaysLiteral() {
        BlockDoc doc = new BlockDoc();
        Block t = doc.create(BlockKind.TEXT);
        t.setParam("text", "not a ref {@nope}");
        assertThat(doc.insert(doc.root(), t, 0)).isTrue();
        String code = BlockCodegen.generate(doc).code();
        assertThat(code).contains("not a ref {@nope}");
        assertThat(BlockCodegen.generate(BlockParser.parse(code)).code()).isEqualTo(code);
    }
}
