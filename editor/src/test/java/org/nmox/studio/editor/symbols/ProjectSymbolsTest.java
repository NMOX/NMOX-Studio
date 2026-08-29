package org.nmox.studio.editor.symbols;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.symbols.ProjectSymbols.Symbol;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The symbol index's laws: bounded walk (breadth cap SPOKEN via
 * wasTruncated, heavy dirs skipped, over-cap files contribute nothing),
 * mtime-keyed cache that re-reads only what changed, and extraction by
 * the one OutlineModel family the Navigator already uses.
 */
class ProjectSymbolsTest {

    private static final Function<Path, String> JS_MIME =
            p -> p.toString().endsWith(".js") ? "text/javascript" : null;

    private static List<Symbol> refresh(ProjectSymbols index, Path root) {
        return index.refresh(root, JS_MIME, v -> false);
    }

    @Test
    @DisplayName("Symbols come from every file the outline family can read")
    void indexesProjectSymbols(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.js"), "function alpha() {}\n");
        Path sub = Files.createDirectories(root.resolve("src"));
        Files.writeString(sub.resolve("b.js"), "function beta() {}\n");
        List<Symbol> all = refresh(new ProjectSymbols(), root);
        assertThat(all).extracting(Symbol::name).contains("alpha", "beta");
    }

    @Test
    @DisplayName("Heavy dirs are skipped — node_modules contributes nothing")
    void heavyDirsSkipped(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.js"), "function mine() {}\n");
        Path heavy = Files.createDirectories(root.resolve("node_modules").resolve("dep"));
        Files.writeString(heavy.resolve("dep.js"), "function vendorNoise() {}\n");
        List<Symbol> all = refresh(new ProjectSymbols(), root);
        assertThat(all).extracting(Symbol::name)
                .contains("mine").doesNotContain("vendorNoise");
    }

    @Test
    @DisplayName("An over-cap file contributes nothing, quietly")
    void overCapFileSkipped(@TempDir Path root) throws Exception {
        StringBuilder big = new StringBuilder("function huge() {}\n");
        big.append("// pad\n".repeat(ProjectSymbols.MAX_FILE_BYTES / 7 + 1));
        Files.writeString(root.resolve("big.js"), big.toString());
        Files.writeString(root.resolve("small.js"), "function small() {}\n");
        List<Symbol> all = refresh(new ProjectSymbols(), root);
        assertThat(all).extracting(Symbol::name)
                .contains("small").doesNotContain("huge");
    }

    @Test
    @DisplayName("The breadth cap trips wasTruncated — a partial index says so")
    void breadthCapSpeaks(@TempDir Path root) throws Exception {
        for (int i = 0; i < ProjectSymbols.MAX_FILES + 5; i++) {
            Files.writeString(root.resolve("f" + i + ".js"), "function f" + i + "() {}\n");
        }
        ProjectSymbols index = new ProjectSymbols();
        refresh(index, root);
        assertThat(index.wasTruncated()).isTrue();
    }

    @Test
    @DisplayName("Unchanged files ride the cache; a changed file re-reads")
    void cacheHonorsMtime(@TempDir Path root) throws Exception {
        Path f = root.resolve("a.js");
        Files.writeString(f, "function first() {}\n");
        ProjectSymbols index = new ProjectSymbols();
        assertThat(refresh(index, root)).extracting(Symbol::name).contains("first");
        Files.writeString(f, "function second() { return 1; }\n");
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() + 5_000));
        assertThat(refresh(index, root)).extracting(Symbol::name)
                .contains("second").doesNotContain("first");
    }

    @Test
    @DisplayName("A stylesheet sigil never defeats the match — type it bare")
    void sigilNeverDefeatsTheMatch() {
        // the walk's find: ⌘I "hero-banner" returned NOTHING because the
        // outline's honest name is ".hero-banner" (v1.215.0 class)
        org.netbeans.spi.jumpto.support.NameMatcher prefix =
                name -> name.startsWith("hero");
        assertThat(NmoxSymbolProvider.matches(prefix, ".hero-banner")).isTrue();
        assertThat(NmoxSymbolProvider.matches(prefix, "#hero-banner")).isTrue();
        assertThat(NmoxSymbolProvider.matches(prefix, "heroic")).isTrue();
        assertThat(NmoxSymbolProvider.matches(prefix, ".footer-note")).isFalse();
        // the full spelled form still matches too
        org.netbeans.spi.jumpto.support.NameMatcher dotted =
                name -> name.startsWith(".hero");
        assertThat(NmoxSymbolProvider.matches(dotted, ".hero-banner")).isTrue();
        // a name that is ONLY sigils never empties itself
        assertThat(NmoxSymbolProvider.sigilFree("...")).isEqualTo("...");
    }

    @Test
    @DisplayName("A vanished file drops out of the index on the next pass")
    void vanishedFileDrops(@TempDir Path root) throws Exception {
        Path f = root.resolve("gone.js");
        Files.writeString(f, "function ghost() {}\n");
        ProjectSymbols index = new ProjectSymbols();
        assertThat(refresh(index, root)).extracting(Symbol::name).contains("ghost");
        Files.delete(f);
        assertThat(refresh(index, root)).extracting(Symbol::name)
                .doesNotContain("ghost");
    }
}
