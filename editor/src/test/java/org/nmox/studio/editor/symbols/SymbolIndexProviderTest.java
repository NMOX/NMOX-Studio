package org.nmox.studio.editor.symbols;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.SymbolIndex;

import static org.assertj.core.api.Assertions.assertThat;

/** The core seam over the Go to Symbol index (v2.78.0): prefix first, then substring; blank answers nothing; 1-based lines; relative files. */
class SymbolIndexProviderTest {

    @TempDir
    Path root;

    private SymbolIndexProvider provider() {
        return new SymbolIndexProvider(p -> p.toString().endsWith(".js") ? "text/javascript" : null);
    }

    @Test
    @DisplayName("prefix hits lead substring hits, folded like the bridge, with relative file and 1-based line")
    void ranksAndLocates() throws Exception {
        Files.writeString(root.resolve("a.js"), "// header\nfunction checkout() {}\nfunction precheckoutHook() {}\n");
        Path sub = Files.createDirectories(root.resolve("src"));
        Files.writeString(sub.resolve("b.js"), "function unrelated() {}\nfunction CheckoutTotal() {}\n");
        SymbolIndex.Answer a = provider().search(root.toFile(), "check", 10);
        assertThat(a.hits()).extracting(SymbolIndex.Hit::name)
                .containsExactly("checkout", "CheckoutTotal", "precheckoutHook");
        SymbolIndex.Hit first = a.hits().get(0);
        assertThat(first.file()).isEqualTo("a.js");
        assertThat(first.line()).isEqualTo(2);
        assertThat(first.kind()).isNotBlank();
        assertThat(a.truncated()).isFalse();
        assertThat(a.hits()).extracting(SymbolIndex.Hit::file).contains("src/b.js");
    }

    @Test
    @DisplayName("outline: the Navigator's items for one file, with every refusal spoken (v2.79.0)")
    void outlines() throws Exception {
        Files.writeString(root.resolve("a.js"), "// top\nclass Cart {\n  total() {}\n}\nfunction checkout() {}\n");
        Files.writeString(root.resolve("notes.bin"), "x");
        SymbolIndex.Outline o = provider().outline(root.toFile(), "a.js");
        assertThat(o.refusal()).isNull();
        assertThat(o.nodes()).extracting(SymbolIndex.Node::name).contains("Cart", "checkout");
        assertThat(o.nodes().get(0).line()).isEqualTo(2);
        assertThat(provider().outline(root.toFile(), root.resolve("a.js").toString()).nodes()).isNotEmpty();
        assertThat(provider().outline(root.toFile(), "../" + root.getFileName() + "/../" + root.getFileName() + "/../../etc/passwd").refusal())
                .startsWith("no such file").isNotNull();
        java.nio.file.Path outside = Files.writeString(root.getParent().resolve("outside-" + root.getFileName() + ".js"), "function x() {}\n");
        try {
            assertThat(provider().outline(root.toFile(), outside.toString()).refusal()).startsWith("outside the aimed project");
        } finally {
            Files.deleteIfExists(outside);
        }
        assertThat(provider().outline(root.toFile(), "nonesuch.js").refusal()).startsWith("no such file");
        assertThat(provider().outline(root.toFile(), "notes.bin").refusal()).startsWith("no outline for this file type");
        assertThat(provider().outline(root.toFile(), "").refusal()).isEqualTo("no file named");
    }

    @Test
    @DisplayName("the limit bounds the answer; a blank query answers nothing")
    void boundsAndRefuses() throws Exception {
        Files.writeString(root.resolve("a.js"), "function one() {}\nfunction two() {}\nfunction three() {}\n");
        assertThat(provider().search(root.toFile(), "t", 1).hits()).hasSize(1);
        assertThat(provider().search(root.toFile(), "  ", 10).hits()).isEmpty();
        assertThat(provider().search(root.toFile(), "one", 0).hits()).isEmpty();
        assertThat(provider().search(null, "one", 10).hits()).isEmpty();
    }
}
