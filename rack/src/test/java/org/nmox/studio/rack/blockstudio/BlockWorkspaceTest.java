package org.nmox.studio.rack.blockstudio;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The multi-component workspace core (v4): v1 single-doc files migrate
 * as a one-component workspace verbatim, v2 round-trips losslessly,
 * added components get unique tags, and the workspace is never empty.
 */
class BlockWorkspaceTest {

    private static BlockDoc docWithTag(String tag) {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", tag);
        return doc;
    }

    @Test
    @DisplayName("A v1 single-doc file loads as a one-component workspace, doc verbatim")
    void v1FileMigrates() {
        BlockDoc old = docWithTag("legacy-widget");
        Block state = old.create(BlockKind.STATE);
        state.setParam("name", "count");
        old.insert(old.root(), state, 0);

        BlockWorkspace ws = BlockWorkspace.fromJson(old.toJson());
        assertThat(ws.components()).hasSize(1);
        assertThat(ws.active()).isZero();
        assertThat(ws.activeDoc().toJson().toString())
                .as("the wrapped doc is byte-for-byte the v1 doc")
                .isEqualTo(old.toJson().toString());
    }

    @Test
    @DisplayName("v2 round-trips: components, order, active index")
    void v2RoundTrip() {
        BlockWorkspace ws = new BlockWorkspace();
        ws.activeDoc().root().setParam("tag", "first-one");
        ws.add().root().setParam("tag", "second-one");
        ws.add().root().setParam("tag", "third-one");
        ws.setActive(1);

        JSONObject json = ws.toJson();
        assertThat(json.getInt("version")).isEqualTo(2);
        BlockWorkspace back = BlockWorkspace.fromJson(json);
        assertThat(back.tags()).containsExactly("first-one", "second-one", "third-one");
        assertThat(back.active()).isEqualTo(1);
        assertThat(back.toJson().toString()).isEqualTo(json.toString());
    }

    @Test
    @DisplayName("An empty components array refuses to load")
    void emptyComponentsThrows() {
        JSONObject o = new JSONObject().put("version", 2)
                .put("components", new org.json.JSONArray());
        assertThatThrownBy(() -> BlockWorkspace.fromJson(o))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("add() generates unique tags and activates the new component")
    void addUniqueTags() {
        BlockWorkspace ws = new BlockWorkspace(); // fresh doc's tag: my-widget
        BlockDoc second = ws.add();
        BlockDoc third = ws.add();
        assertThat(second.root().param("tag")).isEqualTo("my-widget-2");
        assertThat(third.root().param("tag")).isEqualTo("my-widget-3");
        assertThat(ws.active()).isEqualTo(2);
        assertThat(ws.activeDoc()).isSameAs(third);
    }

    @Test
    @DisplayName("remove() never leaves the workspace empty and keeps active valid")
    void removeKeepsInvariants() {
        BlockWorkspace ws = new BlockWorkspace();
        ws.add(); // -2
        ws.add(); // -3, active = 2

        assertThat(ws.remove(0)).isTrue();
        assertThat(ws.active()).as("active shifts left with a removal before it").isEqualTo(1);
        assertThat(ws.activeDoc().root().param("tag")).isEqualTo("my-widget-3");

        assertThat(ws.remove(1)).isTrue(); // removed the active tail
        assertThat(ws.active()).isZero();
        assertThat(ws.components()).hasSize(1);

        assertThat(ws.remove(0)).as("removing the last component is allowed").isTrue();
        assertThat(ws.components()).as("...but a fresh doc takes its place").hasSize(1);
        assertThat(ws.remove(5)).as("out of range refuses").isFalse();
    }

    @Test
    @DisplayName("setActive refuses out-of-range; replaceActive swaps the slot")
    void activeAndReplace() {
        BlockWorkspace ws = new BlockWorkspace();
        assertThat(ws.setActive(3)).isFalse();
        assertThat(ws.setActive(-1)).isFalse();
        BlockDoc other = docWithTag("swapped-in");
        ws.replaceActive(other);
        assertThat(ws.activeDoc()).isSameAs(other);
        assertThat(ws.indexOfTag("swapped-in")).isZero();
        assertThat(ws.indexOfTag("absent-tag")).isEqualTo(-1);
    }
}
