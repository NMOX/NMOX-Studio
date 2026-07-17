package org.nmox.studio.rack.blockstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Block Studio core, pinned: the interlock matrix, checked doc
 * edits, codegen output + the block→code range map, layout slots, and
 * IO round-trip with the never-clobber marker law.
 */
class BlockCoreTest {

    /** A counter component: state + button with a click handler. */
    private static BlockDoc counter() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "my-counter");

        Block state = doc.create(BlockKind.STATE);
        state.setParam("name", "count");
        state.setParam("initial", "0");
        assertThat(doc.insert(doc.root(), state, 0)).isTrue();

        Block div = doc.create(BlockKind.ELEMENT);
        div.setParam("tag", "div");
        assertThat(doc.insert(doc.root(), div, 1)).isTrue();

        Block label = doc.create(BlockKind.TEXT);
        label.setParam("text", "clicks: {count}");
        assertThat(doc.insert(div, label, 0)).isTrue();

        Block button = doc.create(BlockKind.ELEMENT);
        button.setParam("tag", "button");
        assertThat(doc.insert(div, button, 1)).isTrue();

        Block caption = doc.create(BlockKind.TEXT);
        caption.setParam("text", "+1");
        assertThat(doc.insert(button, caption, 0)).isTrue();

        Block onClick = doc.create(BlockKind.ON_EVENT);
        onClick.setParam("event", "click");
        assertThat(doc.insert(button, onClick, 1)).isTrue();

        Block bump = doc.create(BlockKind.SET_STATE);
        bump.setParam("name", "count");
        bump.setParam("expr", "{count} + 1");
        assertThat(doc.insert(onClick, bump, 0)).isTrue();

        return doc;
    }

    // ---- interlock law ----

    @Test
    @DisplayName("The interlock matrix: legal snaps accepted, everything else refused")
    void interlockMatrix() {
        assertThat(BlockRules.accepts(BlockKind.COMPONENT, BlockKind.STATE)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.COMPONENT, BlockKind.ELEMENT)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.ELEMENT, BlockKind.ON_EVENT)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.ON_EVENT, BlockKind.SET_STATE)).isTrue();
        assertThat(BlockRules.accepts(BlockKind.IF_STATE, BlockKind.LOG)).isTrue();

        // the refusals that keep the tree meaningful
        assertThat(BlockRules.accepts(BlockKind.COMPONENT, BlockKind.SET_STATE)).isFalse();
        assertThat(BlockRules.accepts(BlockKind.ELEMENT, BlockKind.STATE)).isFalse();
        assertThat(BlockRules.accepts(BlockKind.ON_EVENT, BlockKind.ELEMENT)).isFalse();
        assertThat(BlockRules.accepts(BlockKind.TEXT, BlockKind.TEXT)).isFalse();
        assertThat(BlockRules.accepts(BlockKind.STATE, BlockKind.STATE)).isFalse();

        // every kind is either a container or a leaf, no partial states
        for (BlockKind k : BlockKind.values()) {
            boolean anyChild = false;
            for (BlockKind c : BlockKind.values()) {
                anyChild |= BlockRules.accepts(k, c);
            }
            assertThat(BlockRules.container(k)).as(k.name()).isEqualTo(anyChild);
        }
    }

    @Test
    @DisplayName("Doc edits are checked: illegal insert refused, cycle refused, move is atomic")
    void docEdits() {
        BlockDoc doc = counter();
        Block div = doc.root().children().get(1);
        Block button = div.children().get(1);

        // illegal: STATE inside an element
        Block state = doc.create(BlockKind.STATE);
        assertThat(doc.insert(div, state, 0)).isFalse();
        assertThat(div.children()).hasSize(2);

        // cycle: div into its own child button
        assertThat(doc.move(div.id(), button, 0)).isFalse();
        assertThat(doc.parentOf(div.id())).isSameAs(doc.root());

        // legal move: label from div into button
        Block label = div.children().get(0);
        assertThat(doc.move(label.id(), button, 0)).isTrue();
        assertThat(button.children().get(0)).isSameAs(label);
        assertThat(div.children()).hasSize(1);

        // detach returns the block and severs it
        assertThat(doc.detach(label.id())).isSameAs(label);
        assertThat(doc.find(label.id())).isNull();
    }

    // ---- codegen ----

    @Test
    @DisplayName("The counter generates a real web component: marker, class, state, template, listener, define")
    void codegenCounter() {
        BlockCodegen.Result r = BlockCodegen.generate(counter());
        String code = r.code();

        assertThat(code).startsWith(BlockCodegen.MARKER + "\n");
        assertThat(code).contains("class MyCounter extends HTMLElement {");
        assertThat(code).contains("#count = 0;");
        assertThat(code).contains("this.attachShadow({ mode: 'open' });");
        assertThat(code).contains("clicks: ${this.#count}");
        assertThat(code).contains("addEventListener('click'");
        assertThat(code).contains("this.#count = this.#count + 1;");
        assertThat(code).contains("this.render();"); // set-state re-renders
        assertThat(code).contains("customElements.define('my-counter', MyCounter);");

        // the listener finds its host through the data-b anchor
        assertThat(code).containsPattern("<button data-b=\"b\\d+\"");
        assertThat(code).containsPattern("querySelector\\('\\[data-b=\"b\\d+\"]'\\)");
    }

    @Test
    @DisplayName("Every block owns a code range; ranges nest inside their parents'")
    void rangesCoverAndNest() {
        BlockDoc doc = counter();
        BlockCodegen.Result r = BlockCodegen.generate(doc);
        Map<String, int[]> ranges = r.ranges();

        for (Block b : doc.preorder()) {
            // SET_ATTR/STYLE render inside their parent's open tag, not
            // as their own lines — they inherit the parent range
            if (b.kind() == BlockKind.SET_ATTR || b.kind() == BlockKind.STYLE) {
                continue;
            }
            assertThat(ranges).as(b.kind() + " " + b.id()).containsKey(b.id());
            int[] range = ranges.get(b.id());
            assertThat(range[0]).isLessThan(range[1]);
            assertThat(range[1]).isLessThanOrEqualTo(r.code().length());

            // ON_EVENT emits in the wiring section AFTER the template, so
            // it deliberately escapes its host element's range; its own
            // action children still nest inside the listener's range.
            Block parent = doc.parentOf(b.id());
            if (b.kind() != BlockKind.ON_EVENT
                    && parent != null && ranges.containsKey(parent.id())
                    && parent.kind() != BlockKind.COMPONENT) {
                int[] p = ranges.get(parent.id());
                assertThat(range[0]).isGreaterThanOrEqualTo(p[0]);
                assertThat(range[1]).isLessThanOrEqualTo(p[1]);
            }
        }
        // the root range is the whole file
        int[] rootRange = ranges.get(doc.root().id());
        assertThat(rootRange[0]).isZero();
        assertThat(rootRange[1]).isEqualTo(r.code().length());
    }

    @Test
    @DisplayName("Attributes and style land in the open tag; toggle-class and if-state generate")
    void codegenAttributesAndLogic() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "fancy-card");

        Block state = doc.create(BlockKind.STATE);
        state.setParam("name", "open");
        state.setParam("initial", "false");
        doc.insert(doc.root(), state, 0);

        Block div = doc.create(BlockKind.ELEMENT);
        div.setParam("tag", "div");
        doc.insert(doc.root(), div, 1);

        Block attr = doc.create(BlockKind.SET_ATTR);
        attr.setParam("name", "role");
        attr.setParam("value", "region");
        doc.insert(div, attr, 0);

        Block style = doc.create(BlockKind.STYLE);
        style.setParam("css", "border: 1px solid");
        doc.insert(div, style, 1);

        Block on = doc.create(BlockKind.ON_EVENT);
        on.setParam("event", "click");
        doc.insert(div, on, 2);

        Block toggle = doc.create(BlockKind.TOGGLE_CLASS);
        toggle.setParam("class", "open");
        doc.insert(on, toggle, 0);

        Block iff = doc.create(BlockKind.IF_STATE);
        iff.setParam("name", "open");
        iff.setParam("op", "==");
        iff.setParam("value", "false");
        doc.insert(on, iff, 1);

        Block log = doc.create(BlockKind.LOG);
        log.setParam("message", "opened {open}");
        doc.insert(iff, log, 0);

        String code = BlockCodegen.generate(doc).code();
        assertThat(code).contains("<div data-b=\"" + div.id() + "\" role=\"region\" style=\"border: 1px solid\">");
        assertThat(code).contains(".classList.toggle('open');");
        assertThat(code).contains("if (this.#open == false) {");
        assertThat(code).contains("console.log(`opened ${this.#open}`);");
        // no SET_STATE anywhere → the listener must NOT re-render
        assertThat(code).doesNotContain("      this.render();");
    }

    @Test
    @DisplayName("Escaping: backticks, ${ and quotes in params cannot break the template")
    void codegenEscaping() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "safe-box");
        Block div = doc.create(BlockKind.ELEMENT);
        doc.insert(doc.root(), div, 0);
        Block text = doc.create(BlockKind.TEXT);
        text.setParam("text", "evil ` ${alert(1)} \\ text");
        doc.insert(div, text, 0);

        String code = BlockCodegen.generate(doc).code();
        assertThat(code).contains("evil \\` \\${alert(1)} \\\\ text");
        // {name} that names no declared state stays literal, never interpolates
        Block t2 = doc.create(BlockKind.TEXT);
        t2.setParam("text", "{ghost}");
        doc.insert(div, t2, 1);
        assertThat(BlockCodegen.generate(doc).code()).contains("{ghost}");
    }

    @Test
    @DisplayName("Validation names every problem: bad tag, undeclared state, bad operator")
    void validation() {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", "nohyphen");
        assertThat(BlockCodegen.validate(doc)).anyMatch(p -> p.contains("hyphen"));

        doc.root().setParam("tag", "my-widget");
        Block div = doc.create(BlockKind.ELEMENT);
        doc.insert(doc.root(), div, 0);
        Block on = doc.create(BlockKind.ON_EVENT);
        doc.insert(div, on, 0);
        Block set = doc.create(BlockKind.SET_STATE);
        set.setParam("name", "missing");
        doc.insert(on, set, 0);
        assertThat(BlockCodegen.validate(doc)).anyMatch(p -> p.contains("undeclared state"));

        assertThatThrownBy(() -> BlockCodegen.generate(doc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("className: my-cool-widget → MyCoolWidget")
    void classNames() {
        assertThat(BlockCodegen.className("my-cool-widget")).isEqualTo("MyCoolWidget");
        assertThat(BlockCodegen.className("x-a")).isEqualTo("XA");
    }

    @Test
    @DisplayName("Tag law: hyphen required, no empty segments, lowercase only")
    void tagLaw() {
        assertThat(BlockCodegen.validTag("my-widget")).isTrue();
        assertThat(BlockCodegen.validTag("x-a-b2")).isTrue();
        assertThat(BlockCodegen.validTag("nohyphen")).isFalse();
        assertThat(BlockCodegen.validTag("my--widget")).isFalse();
        assertThat(BlockCodegen.validTag("my-widget-")).isFalse();
        assertThat(BlockCodegen.validTag("My-Widget")).isFalse();
        assertThat(BlockCodegen.validTag("-widget")).isFalse();
    }

    // ---- layout ----

    @Test
    @DisplayName("Layout: one row per block, children indented, slots only where the law allows")
    void layoutRowsAndSlots() {
        BlockDoc doc = counter();
        BlockLayout layout = new BlockLayout(doc);

        assertThat(layout.rows()).hasSize(doc.preorder().size());
        // depth mirrors the tree: root 0, div 1, button 2, its text 3
        assertThat(layout.rows().get(0).depth()).isZero();
        Block div = doc.root().children().get(1);
        Block button = div.children().get(1);
        assertThat(layout.rows().stream()
                .filter(r -> r.block() == button).findFirst().orElseThrow().depth())
                .isEqualTo(2);

        // a STATE piece has legal slots only under COMPONENT
        for (int y = 0; y < layout.height(); y += 5) {
            BlockLayout.Slot s = layout.slotFor(y, BlockKind.STATE);
            if (s != null) {
                assertThat(s.parent().kind()).isEqualTo(BlockKind.COMPONENT);
            }
        }
        // moving div may not target a slot inside its own subtree
        BlockLayout.Slot moveSlot = layout.slotForMove(
                layout.rows().get(layout.rows().size() - 1).y(), div);
        assertThat(moveSlot).isNotNull();
        assertThat(moveSlot.parent()).isSameAs(doc.root());
    }

    // ---- persistence ----

    @Test
    @DisplayName("JSON round-trip is lossless; a hand-edited illegal nesting is refused on load")
    void jsonRoundTrip() {
        BlockDoc doc = counter();
        JSONObject json = doc.toJson();
        BlockDoc back = BlockDoc.fromJson(new JSONObject(json.toString()));

        assertThat(back.toJson().toString()).isEqualTo(json.toString());
        assertThat(back.preorder()).hasSameSizeAs(doc.preorder());
        // ids keep flowing after the highest loaded id
        Block fresh = back.create(BlockKind.TEXT);
        assertThat(doc.find(fresh.id())).isNull();

        // smuggle a STATE under an ELEMENT → refused wholesale
        JSONObject bad = new JSONObject(json.toString());
        JSONObject divJson = bad.getJSONObject("root").getJSONArray("children").getJSONObject(1);
        JSONObject smuggled = new JSONObject()
                .put("id", "b99").put("kind", "STATE")
                .put("params", new JSONObject().put("name", "x").put("initial", "1"));
        divJson.getJSONArray("children").put(smuggled);
        assertThatThrownBy(() -> BlockDoc.fromJson(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot nest");
    }

    @Test
    @DisplayName("Workspace save/load round-trips; the component write never clobbers a foreign file")
    void ioAndNeverClobber(@TempDir Path dir) throws Exception {
        File project = dir.toFile();
        BlockDoc doc = counter();
        BlockIO.save(project, doc);
        BlockDoc back = BlockIO.load(project);
        assertThat(back.toJson().toString()).isEqualTo(doc.toJson().toString());
        assertThat(BlockIO.load(Files.createDirectory(dir.resolve("empty")).toFile())).isNull();

        String code = BlockCodegen.generate(doc).code();
        assertThat(BlockIO.writeComponent(project, "my-counter", code)).isTrue();
        File out = BlockIO.componentFile(project, "my-counter");
        assertThat(out).exists();
        assertThat(Files.readString(out.toPath())).isEqualTo(code);

        // ours: overwriting again is fine
        assertThat(BlockIO.writeComponent(project, "my-counter", code)).isTrue();

        // foreign: hand-written file without the marker is refused untouched
        Files.writeString(out.toPath(), "// my precious hand edits\n");
        assertThat(BlockIO.writeComponent(project, "my-counter", code)).isFalse();
        assertThat(Files.readString(out.toPath())).isEqualTo("// my precious hand edits\n");
    }
}
