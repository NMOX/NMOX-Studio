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
 * The four-file switcher on the SUFFIXLESS family (v2.38.0, riding
 * ledger 82): Angular 21's CLI drops the .component infix, and every
 * NgSwitch leg already works by basename — pinned here so a future
 * convention-keyed "improvement" can't regress the suffixless shape.
 */
class NgSwitchSuffixlessTest {

    @Test
    @DisplayName("all four legs resolve on widget.* with no .component infix")
    void suffixlessFamily(@TempDir Path work) throws IOException {
        File dir = work.toFile();
        File ts = new File(dir, "widget.ts");
        File html = new File(dir, "widget.html");
        File css = new File(dir, "widget.css");
        File spec = new File(dir, "widget.spec.ts");
        Files.writeString(ts.toPath(),
                "@Component({selector: 'app-w', templateUrl: './widget.html'}) export class W {}");
        Files.writeString(html.toPath(), "<p>{{ x }}</p>");
        Files.writeString(css.toPath(), "p { color: tomato; }");
        Files.writeString(spec.toPath(), "describe('W', () => {});");

        assertThat(NgSwitch.componentFor(html, f -> {
            try {
                return Files.readString(f.toPath());
            } catch (IOException e) {
                return null;
            }
        })).isEqualTo(ts);
        assertThat(NgSwitch.stylesFor(ts, Files.readString(ts.toPath()))).isEqualTo(css);
        assertThat(NgSwitch.specFor(ts)).isEqualTo(spec);
        assertThat(NgSwitch.componentForSibling(css)).isEqualTo(ts);
        assertThat(NgSwitch.componentForSibling(spec)).isEqualTo(ts);
    }
}
