package org.nmox.studio.rack.blockstudio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.service.RackService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What Block Studio does with a workspace two people merged (3.4).
 *
 * <p>Measured before 3.4: a {@code .nmoxblocks.json} holding git's conflict
 * markers, or a piece kind from a newer NMOX Studio, failed to parse, was
 * copied to {@code .bak} with {@code REPLACE_EXISTING}, and the studio bound
 * a fresh empty workspace that the next ordinary edit saved over the file;
 * two pieces both named {@code b3} by a keep-both merge loaded with no heal;
 * and which component was open lived in the committed file, rewritten on
 * every switch.
 */
class BlockMergeTest {

    private Locale before;

    @BeforeAll
    static void noRealPreferences() {
        BlockActiveMemory.useMemoryStoreForTests();
    }

    @BeforeEach
    void english() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    static String conflicted() {
        BlockWorkspace ours = new BlockWorkspace();
        String json = ours.toJson().toString(2);
        return "<".repeat(7) + " HEAD\n" + json + "\n" + "=".repeat(7) + "\n" + json + "\n"
                + ">".repeat(7) + " theirs\n";
    }

    @Test
    @DisplayName("a conflicted workspace is left byte-for-byte, with no workspace bound and the reason said")
    void conflictedIsLeftAlone(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(BlockIO.WORKSPACE_FILE);
        String bytes = conflicted();
        Files.writeString(f, bytes, StandardCharsets.UTF_8);
        BlockIO.Loaded loaded = BlockIO.loadForStudio(dir.toFile());
        assertThat(loaded.workspace()).as("nothing to edit, so nothing to save over the merge").isNull();
        assertThat(loaded.lockedReason()).contains(BlockIO.WORKSPACE_FILE).contains("merge conflicts").contains("git");
        assertThat(f).hasContent(bytes);
        assertThat(dir.resolve(BlockIO.WORKSPACE_FILE + ".bak")).doesNotExist();
    }

    @Test
    @DisplayName("a piece kind from a newer NMOX Studio: left untouched, and said so by name")
    void newerFormatIsLeftAlone(@TempDir Path dir) throws Exception {
        BlockWorkspace ws = new BlockWorkspace();
        JSONObject json = ws.toJson();
        JSONObject root = json.getJSONArray("components").getJSONObject(0).getJSONObject("root");
        root.getJSONArray("children").put(new JSONObject().put("id", "b9").put("kind", "HOLOGRAM")
                .put("params", new JSONObject()).put("children", new JSONArray()));
        Path f = dir.resolve(BlockIO.WORKSPACE_FILE);
        String bytes = json.toString(2);
        Files.writeString(f, bytes);
        BlockIO.Loaded loaded = BlockIO.loadForStudio(dir.toFile());
        assertThat(loaded.workspace()).isNull();
        assertThat(loaded.lockedReason()).contains("newer NMOX Studio").contains("HOLOGRAM");
        assertThat(f).hasContent(bytes);
        assertThat(dir.resolve(BlockIO.WORKSPACE_FILE + ".bak")).doesNotExist();
    }

    @Test
    @DisplayName("a directory wearing the workspace's name is unreadable: nothing is bound over it")
    void directoryIsUnreadable(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve(BlockIO.WORKSPACE_FILE));
        BlockIO.Loaded loaded = BlockIO.loadForStudio(dir.toFile());
        assertThat(loaded.workspace()).isNull();
        assertThat(loaded.lockedReason()).contains("could not be read");
    }

    @Test
    @DisplayName("a broken workspace is copied aside and starts fresh; a second one never overwrites the first rescue")
    void brokenWorkspacesAreRescuedInTurn(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(BlockIO.WORKSPACE_FILE);
        Files.writeString(f, "{ first");
        BlockIO.Loaded one = BlockIO.loadForStudio(dir.toFile());
        assertThat(one.workspace()).isNotNull();
        assertThat(one.note()).contains(BlockIO.WORKSPACE_FILE + ".bak");
        BlockIO.loadForStudio(dir.toFile()); // the same bytes again reuse their copy
        Files.writeString(f, "{ second");
        BlockIO.Loaded two = BlockIO.loadForStudio(dir.toFile());
        assertThat(two.note()).contains(BlockIO.WORKSPACE_FILE + ".bak.1");
        assertThat(dir.resolve(BlockIO.WORKSPACE_FILE + ".bak")).hasContent("{ first");
        assertThat(dir.resolve(BlockIO.WORKSPACE_FILE + ".bak.1")).hasContent("{ second");
        assertThat(dir.resolve(BlockIO.WORKSPACE_FILE + ".bak.2")).doesNotExist();
    }

    @Test
    @DisplayName("a broken workspace no copy can be kept of binds read-only: the file is the only copy")
    void unrescuedIsReadOnly(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(BlockIO.WORKSPACE_FILE);
        Files.writeString(f, "{ broken");
        try {
            Files.setPosixFilePermissions(dir, Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException noPosix) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "POSIX permissions needed");
        }
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isWritable(dir), "running as root");
            BlockIO.Loaded loaded = BlockIO.loadForStudio(dir.toFile());
            assertThat(loaded.workspace()).isNull();
            assertThat(loaded.lockedReason()).contains("no copy");
        } finally {
            Files.setPosixFilePermissions(dir, java.util.EnumSet.allOf(
                    java.nio.file.attribute.PosixFilePermission.class));
        }
    }

    @Test
    @DisplayName("two pieces both named b3 after a keep-both merge: the first keeps b3, the second gets a fresh id")
    void duplicateIdsHealAtParse() {
        BlockDoc doc = new BlockDoc();
        JSONObject json = doc.toJson();
        JSONArray kids = json.getJSONObject("root").getJSONArray("children");
        kids.put(piece("b3", "TEXT", "text", "from Alice"));
        kids.put(piece("b3", "TEXT", "text", "from Bob"));
        kids.put(piece("b4", "STATE", "name", "count"));
        json.put("nextId", 4); // both people's counters said 4

        BlockDoc healed = BlockDoc.fromJson(json);
        List<Block> blocks = healed.preorder();
        Set<String> ids = new HashSet<>();
        blocks.forEach(b -> ids.add(b.id()));
        assertThat(ids).as("every piece has its own id").hasSize(blocks.size());
        assertThat(healed.find("b3").param("text")).as("the first occurrence keeps its id").isEqualTo("from Alice");
        assertThat(blocks.stream().filter(b -> "from Bob".equals(b.param("text"))).findFirst().orElseThrow().id())
                .isNotEqualTo("b3").isNotEqualTo("b4");
        Block next = healed.create(BlockKind.TEXT);
        assertThat(ids).as("the counter starts past every id in the file").doesNotContain(next.id());
        assertThat(BlockCodegen.generate(healed)).isNotNull();
    }

    private static JSONObject piece(String id, String kind, String key, String value) {
        return new JSONObject().put("id", id).put("kind", kind)
                .put("params", new JSONObject().put(key, value)).put("children", new JSONArray());
    }

    @Test
    @DisplayName("the committed file stops recording which component is open; a legacy 'active' is still read once")
    void activeIsPersonalNotCommitted(@TempDir Path dir) {
        BlockWorkspace ws = new BlockWorkspace();
        ws.add().root().setParam("tag", "second-one");
        assertThat(ws.toJson().has("active")).as("no per-person state in the checked-in file").isFalse();

        JSONObject legacy = ws.toJson().put("active", 1);
        BlockWorkspace read = BlockWorkspace.fromJson(legacy);
        assertThat(read.active()).isEqualTo(1);
        BlockActiveMemory.apply(dir.toFile(), read);
        assertThat(BlockActiveMemory.recall(dir.toFile()))
                .as("the legacy index migrates once, as the component's tag").isEqualTo("second-one");

        // a teammate's pull adds a component in FRONT: the remembered tag still
        // names the component this person had open, where an index would not
        BlockWorkspace pulled = new BlockWorkspace();
        pulled.activeDoc().root().setParam("tag", "inserted-first");
        pulled.add().root().setParam("tag", "my-widget");
        pulled.add().root().setParam("tag", "second-one");
        pulled.setActive(0);
        BlockActiveMemory.apply(dir.toFile(), pulled);
        assertThat(pulled.activeDoc().root().param("tag")).isEqualTo("second-one");
    }

    @Test
    @DisplayName("the studio bound to a conflicted file writes nothing, and reloads when the merge is resolved")
    void studioRefusesThenReloads(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(BlockIO.WORKSPACE_FILE);
        String bytes = conflicted();
        Files.writeString(f, bytes);
        RackService.getDefault().getRack().setProjectDir(dir.toFile());
        BlockStudioTopComponent[] tc = new BlockStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new BlockStudioTopComponent());
        tc[0].openBrowser = false;
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        drain();
        try {
            SwingUtilities.invokeAndWait(() -> {
                assertThat(tc[0].lockedReason()).contains("merge conflicts");
                assertThat(tc[0].currentWorkspace()).isNull();
                tc[0].persist(); // every save path refuses
                tc[0].addComponent();
            });
            drain();
            assertThat(f).as("not a byte written over the merge").hasContent(bytes);

            // resolved in git: the file pulse's foreign edit reloads the studio
            BlockWorkspace resolved = new BlockWorkspace();
            resolved.activeDoc().root().setParam("tag", "after-merge");
            Files.writeString(f, resolved.toJson().toString(2) + "\n");
            tc[0].onWorkspaceFileChanged(f.toFile().lastModified(), Files.size(f));
            drain();
            SwingUtilities.invokeAndWait(() -> {
                assertThat(tc[0].lockedReason()).isNull();
                assertThat(tc[0].currentWorkspace().tags()).containsExactly("after-merge");
            });
        } finally {
            SwingUtilities.invokeAndWait(tc[0]::componentClosed);
            BlockStudioTopComponent.drainIoLane();
        }
    }

    private static void drain() throws Exception {
        BlockStudioTopComponent.drainIoLane();
        SwingUtilities.invokeAndWait(() -> { });
        BlockStudioTopComponent.drainIoLane();
        SwingUtilities.invokeAndWait(() -> { });
    }
}
