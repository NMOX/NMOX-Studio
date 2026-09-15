package org.nmox.studio.editor.languages;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.outline.OutlineKind;
import org.nmox.studio.editor.outline.OutlineModel;
import org.nmox.studio.editor.outline.OutlineModel.Item;
import org.nmox.studio.editor.polyglot.LanguageComments;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Vyper vertical (ledger 12): the grammar is pinned, the mime is
 * claimed for .vy and .vyi, the CSL kit exists, the comment toggle
 * speaks {@code #}, every MimeLookup surface carries the mime, and the
 * outline reads a real Vyper 0.4 ERC20 (the fixture compiled with
 * vyper 0.4.3 when it was written).
 */
class VyperVerticalTest {

    private static final String MIME = "text/x-vyper";

    private static String src(String rel) throws Exception {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String fixture() throws Exception {
        try (InputStream in = VyperVerticalTest.class.getResourceAsStream(
                "/org/nmox/studio/editor/outline/Token.vy")) {
            assertThat(in).as("the ERC20 fixture ships with the tests").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    @DisplayName("the vendored grammar matches its NOTICE pin, byte for byte, and is source.vyper")
    void grammarPinned() throws Exception {
        Path grammar = Path.of("src/main/resources/org/nmox/studio/editor/grammars/vyper.tmLanguage.json");
        byte[] bytes = Files.readAllBytes(grammar);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(src("src/main/java/org/nmox/studio/editor/grammars/NOTICE-grammars.md"))
                .contains("vyper.tmLanguage.json | sha256 " + sha)
                .contains("tintinweb/vscode-vyper")
                .contains("(MIT)");
        JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        assertThat(json.getString("scopeName")).isEqualTo("source.vyper");
    }

    @Test
    @DisplayName("the mime is claimed for contracts and interface files")
    void mimeResolution() throws Exception {
        String grammar = src("src/main/java/org/nmox/studio/editor/grammars/VyperGrammar.java");
        assertThat(grammar)
                .contains("grammar = \"vyper.tmLanguage.json\", mimeType = \"" + MIME + "\"")
                .contains("mimeType = \"" + MIME + "\", extension = {\"vy\", \"vyi\"}");
    }

    @Test
    @DisplayName("the CSL kit resolves through the guard and the comment toggle speaks #")
    void cslAndComments() throws Exception {
        assertThat(new VyperLanguage().getLineCommentPrefix()).isEqualTo("#");
        assertThat(new VyperLanguage().getDisplayName()).isEqualTo("Vyper");
        assertThat(LanguageComments.lineCommentFor(MIME)).isEqualTo("#");
        assertThat(src("src/main/java/org/nmox/studio/editor/languages/VyperLanguage.java"))
                .contains("@LanguageRegistration(mimeType = \"" + MIME + "\")")
                .contains("Lexers.find(\"" + MIME + "\")");
    }

    @Test
    @DisplayName("typing, deletion, spellcheck, completion and the Navigator all carry the mime")
    void rideAlongs() throws Exception {
        for (String rel : new String[]{
            "src/main/java/org/nmox/studio/editor/typing/JsTypedTextInterceptor.java",
            "src/main/java/org/nmox/studio/editor/typing/JsDeletedTextInterceptor.java",
            "src/main/java/org/nmox/studio/editor/spell/CodeSpellTokenListProvider.java",
            "src/main/java/org/nmox/studio/editor/polyglot/PolyglotCompletionProvider.java",
            "src/main/java/org/nmox/studio/editor/outline/StructureNavigatorPanel.java"}) {
            assertThat(src(rel)).as(rel + " registers " + MIME).contains("mimeType = \"" + MIME + "\"");
        }
    }

    @Test
    @DisplayName("keyword completion offers Vyper's words, types and @decorators")
    void keywordCompletion() throws Exception {
        var keywords = org.nmox.studio.editor.polyglot.PolyglotCompletionAccess.keywords(MIME);
        assertThat(keywords).contains("def", "event", "struct", "interface", "flag", "log", "extcall",
                "HashMap", "DynArray", "uint256", "address", "immutable", "public",
                "@external", "@internal", "@view", "@payable", "@nonreentrant", "@deploy");
        // the walk steps over @ on this mime, so the decorator is reachable as typed
        assertThat(org.nmox.studio.editor.polyglot.PolyglotCompletionAccess.prefixAt("    @ext", 8, MIME))
                .isEqualTo("@ext");
        assertThat(org.nmox.studio.editor.polyglot.PolyglotCompletionAccess.matching(MIME, "@ext"))
                .containsExactly("@external");
        assertThat(org.nmox.studio.editor.polyglot.PolyglotCompletionAccess.matching(MIME, "@n"))
                .containsExactly("@nonreentrant");
    }

    @Test
    @DisplayName("the outline reads a real Vyper 0.4 ERC20: events, struct, flag, interface, storage, decorated defs")
    void outlineOfRealContract() throws Exception {
        List<Item> items = OutlineModel.extract(MIME, fixture());
        assertThat(items).extracting(Item::name).containsExactly(
                "Transfer", "Approval", "Checkpoint", "Role", "Hook", "onTransfer", "paused",
                "MAX_SUPPLY", "name", "symbol", "decimals", "totalSupply", "balanceOf", "allowance",
                "roles", "owner",
                "__init__", "_mint", "transfer", "transferFrom", "approve", "mint", "owner_of_contract");
        Item transfer = byName(items, "Transfer");
        assertThat(transfer.kind()).isEqualTo(OutlineKind.FIELD);
        assertThat(transfer.detail()).isEqualTo("event");
        assertThat(byName(items, "Checkpoint").kind()).isEqualTo(OutlineKind.TYPE);
        assertThat(byName(items, "Role").kind()).isEqualTo(OutlineKind.ENUM);
        assertThat(byName(items, "Hook").kind()).isEqualTo(OutlineKind.INTERFACE);

        Item onTransfer = byName(items, "onTransfer");
        assertThat(onTransfer.kind()).isEqualTo(OutlineKind.METHOD);
        assertThat(onTransfer.depth()).as("interface members nest under the interface").isEqualTo(1);
        assertThat(onTransfer.detail()).isEqualTo("nonpayable");
        assertThat(byName(items, "paused").detail()).isEqualTo("view");

        assertThat(byName(items, "MAX_SUPPLY").detail()).isEqualTo("constant");
        assertThat(byName(items, "name").kind()).isEqualTo(OutlineKind.PROPERTY);
        assertThat(byName(items, "name").detail()).isEqualTo("public");
        assertThat(byName(items, "roles").detail()).isEqualTo("storage");
        assertThat(byName(items, "owner").detail()).isEqualTo("immutable");
        assertThat(byName(items, "name").depth()).as("storage after the interface is back at top").isZero();

        assertThat(byName(items, "__init__").detail()).isEqualTo("@deploy");
        assertThat(byName(items, "_mint").detail()).isEqualTo("@internal");
        assertThat(byName(items, "mint").detail()).isEqualTo("@external @nonreentrant");
        assertThat(byName(items, "owner_of_contract").detail()).isEqualTo("@external @view");
        assertThat(byName(items, "transfer").kind()).isEqualTo(OutlineKind.FUNCTION);
        assertThat(byName(items, "transfer").depth()).isZero();
        assertThat(byName(items, "transfer").line()).isEqualTo(lineOf(fixture(), "def transfer("));
    }

    @Test
    @DisplayName("docstrings, comments and directives are never structure")
    void proseIsNotStructure() throws Exception {
        List<Item> items = OutlineModel.extract(MIME, fixture());
        assertThat(items).extracting(Item::name)
                .doesNotContain("notAFunction", "NotAnEvent", "commentedOut", "implements", "IERC20");
        String unclosed = "\"\"\"\nnever closed\ndef hidden():\n    pass\n";
        assertThat(OutlineModel.extract(MIME, unclosed)).as("an unclosed docstring swallows, never guesses").isEmpty();
        String inline = "x: uint256 # \"\"\" not a docstring\ndef visible():\n    pass\n";
        assertThat(OutlineModel.extract(MIME, inline)).extracting(Item::name).containsExactly("x", "visible");
    }

    @Test
    @DisplayName("hostile input never throws and always terminates")
    void hostileInput() {
        Random random = new Random(20260914L);
        String alphabet = "def event struct:@#\"'()[]\n\t    _xX9 ‮";
        for (int round = 0; round < 200; round++) {
            StringBuilder sb = new StringBuilder();
            int len = random.nextInt(4000);
            for (int i = 0; i < len; i++) {
                sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            assertThat(OutlineModel.extract(MIME, sb.toString())).isNotNull();
        }
        assertThat(OutlineModel.extract(MIME, "@".repeat(100_000))).isEmpty();
        assertThat(OutlineModel.extract(MIME, "def " + "a".repeat(500) + "():")).hasSize(1);
    }

    private static Item byName(List<Item> items, String name) {
        return items.stream().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
    }

    private static int lineOf(String text, String needle) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i;
            }
        }
        return -1;
    }
}
