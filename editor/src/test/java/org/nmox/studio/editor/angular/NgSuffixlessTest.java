package org.nmox.studio.editor.angular;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two-signal predicate's boundary rules (ledger 82): BOTH signals
 * required, the .component shape excluded (the declarative resolver
 * owns it), the ancestor walk repo-bounded, the sibling read capped.
 */
class NgSuffixlessTest {

    private File angularProject(Path work) throws IOException {
        File root = new File(work.toFile(), "app");
        File src = new File(root, "src/app");
        Files.createDirectories(src.toPath());
        Files.writeString(new File(root, "angular.json").toPath(), "{}");
        return src;
    }

    @Test
    @DisplayName("both signals → template; either alone → plain html")
    void bothSignalsRequired(@TempDir Path work) throws IOException {
        File src = angularProject(work);
        File html = new File(src, "widget.html");
        Files.writeString(html.toPath(), "<p>{{ x }}</p>");
        // signal 1 missing: no sibling at all
        assertThat(NgSuffixless.isSuffixlessTemplate(html)).isFalse();
        // sibling without the decorator: still no
        File ts = new File(src, "widget.ts");
        Files.writeString(ts.toPath(), "export const x = 1;");
        assertThat(NgSuffixless.isSuffixlessTemplate(html)).isFalse();
        // decorator lands: yes
        Files.writeString(ts.toPath(), "@Component({selector: 'app-w'}) export class W {}");
        assertThat(NgSuffixless.isSuffixlessTemplate(html)).isTrue();
    }

    @Test
    @DisplayName("no angular.json ancestry → plain html even with a perfect sibling")
    void ancestryRequired(@TempDir Path work) throws IOException {
        File src = new File(work.toFile(), "plain/src");
        Files.createDirectories(src.toPath());
        File html = new File(src, "widget.html");
        Files.writeString(html.toPath(), "<p></p>");
        Files.writeString(new File(src, "widget.ts").toPath(), "@Component({}) class W {}");
        assertThat(NgSuffixless.isSuffixlessTemplate(html)).isFalse();
    }

    @Test
    @DisplayName(".component.html stays the declarative resolver's — this predicate refuses it")
    void componentShapeExcluded(@TempDir Path work) throws IOException {
        File src = angularProject(work);
        File html = new File(src, "hero.component.html");
        Files.writeString(html.toPath(), "<p></p>");
        Files.writeString(new File(src, "hero.component.ts").toPath(), "@Component({}) class H {}");
        assertThat(NgSuffixless.isSuffixlessTemplate(html)).isFalse();
    }

    @Test
    @DisplayName("a .git boundary stops the walk — an angular.json above the repo never claims")
    void repoBounded(@TempDir Path work) throws IOException {
        Files.writeString(new File(work.toFile(), "angular.json").toPath(), "{}");
        File repo = new File(work.toFile(), "other-repo");
        File src = new File(repo, "src");
        Files.createDirectories(src.toPath());
        Files.createDirectories(new File(repo, ".git").toPath());
        File html = new File(src, "widget.html");
        Files.writeString(html.toPath(), "<p></p>");
        Files.writeString(new File(src, "widget.ts").toPath(), "@Component({}) class W {}");
        assertThat(NgSuffixless.isSuffixlessTemplate(html)).isFalse();
    }

    @Test
    @DisplayName("the decorator past the read cap does not count — the read is bounded")
    void siblingReadCapped(@TempDir Path work) throws IOException {
        File src = angularProject(work);
        File html = new File(src, "widget.html");
        Files.writeString(html.toPath(), "<p></p>");
        File ts = new File(src, "widget.ts");
        Files.writeString(ts.toPath(),
                "//" + "x".repeat(NgSuffixless.SIBLING_READ_CAP) + "\n@Component({})");
        assertThat(NgSuffixless.isSuffixlessTemplate(html)).isFalse();
    }
}
