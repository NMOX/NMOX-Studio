package org.nmox.studio.editor.importmap;

import java.io.File;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.nmox.studio.editor.fullstack.ProjectJumpHyperlink;
import org.nmox.studio.editor.importmap.ImportMaps.PageMap;

/**
 * ⌘-click a bare import specifier in JS/TS and land on its mapping in
 * the page's {@code <script type="importmap">} (futures-2031 F3). The
 * ProjectJumpHyperlink skeleton's fifth consumer: span math pure, the
 * page read and jump off the EDT, refusals on the status line —
 * "no import map in this project" and "not in the import map" are
 * different honest misses and say so.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript",
            service = HyperlinkProviderExt.class, position = 18),
    @MimeRegistration(mimeType = "text/typescript",
            service = HyperlinkProviderExt.class, position = 18)
})
public final class ImportMapHyperlink extends ProjectJumpHyperlink {

    @Override
    protected int[] spanAt(String text, int offset) {
        return ImportMaps.specifierSpanAt(text, offset);
    }

    @Override
    protected String tooltip() {
        return "Open this specifier's import-map entry";
    }

    @Override
    protected void click(String text, int[] span, File projectDir) {
        String specifier = text.substring(span[0], span[1]);
        PageMap map = ImportMaps.findProjectMap(projectDir);
        if (map == null) {
            status("No import map found in this project's entry page.");
            return;
        }
        String key = ImportMaps.resolveKey(specifier, map.imports());
        if (key == null) {
            status("'" + specifier + "' is not in " + map.page().getName()
                    + "'s import map.");
            return;
        }
        status(specifier + " → " + map.imports().get(key));
        openAt(map.page(), map.keyOffsets().getOrDefault(key, 0));
    }
}
