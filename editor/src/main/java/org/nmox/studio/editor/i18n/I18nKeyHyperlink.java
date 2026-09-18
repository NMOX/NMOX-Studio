package org.nmox.studio.editor.i18n;

import java.io.File;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.nmox.studio.editor.fullstack.ProjectJumpHyperlink;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalog;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalogs;
import org.nmox.studio.editor.i18n.I18nCatalogs.Entry;

/**
 * ⌘-click a translation key in a lookup and land on its line in the
 * project's SOURCE catalog (v2.177.0) — the env-key jump one catalog
 * family over. A namespaced key ({@code common:title}) goes to that
 * namespace's file; a bare key to the first source catalog declaring it.
 * Refuses on the status line with the catalog folder's name when the
 * key is declared nowhere, and with the project's own sentence when it
 * has no catalogs at all — the honest answers to "where is this
 * translated?".
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = HyperlinkProviderExt.class, position = 19),
    @MimeRegistration(mimeType = "text/typescript", service = HyperlinkProviderExt.class, position = 19),
    @MimeRegistration(mimeType = "text/html", service = HyperlinkProviderExt.class, position = 19),
    @MimeRegistration(mimeType = "text/x-vue", service = HyperlinkProviderExt.class, position = 19),
    @MimeRegistration(mimeType = "text/x-svelte", service = HyperlinkProviderExt.class, position = 19),
    @MimeRegistration(mimeType = "text/x-ng-template", service = HyperlinkProviderExt.class, position = 19)
})
@org.openide.util.NbBundle.Messages({
    "I18nKeyHyperlink_tooltip=Go to the key in the source catalog",
    "I18nKeyHyperlink_notDeclared={0} is not declared in {1}",
    "I18nKeyHyperlink_noCatalogs=No translation catalogs found in this project"
})
public final class I18nKeyHyperlink extends ProjectJumpHyperlink {

    /** One resolved key: the catalog that declares it and its entry. */
    record Target(Catalog catalog, Entry entry) {
    }

    @Override
    protected int[] spanAt(String text, int offset) {
        return I18nKeys.keySpanAt(text, offset);
    }

    @Override
    protected String tooltip() {
        return Bundle.I18nKeyHyperlink_tooltip();
    }

    @Override
    protected void click(String text, int[] span, File projectDir) {
        String key = text.substring(span[0], span[1]);
        if (projectDir == null) {
            status(Bundle.I18nKeyHyperlink_noCatalogs());
            return;
        }
        Catalogs catalogs;
        try {
            catalogs = I18nCatalogs.detect(projectDir.toPath());
        } catch (I18nCatalogs.ParseFailure failed) {
            status(Bundle.CheckTranslationsAction_parseError(
                    projectDir.toPath().relativize(failed.file()).toString(), failed.getMessage()));
            return;
        }
        if (catalogs == null) {
            status(Bundle.I18nKeyHyperlink_noCatalogs());
            return;
        }
        Target found = find(catalogs, key);
        if (found == null) {
            status(Bundle.I18nKeyHyperlink_notDeclared(key, catalogs.catalogHome()));
        } else {
            openLine(found.catalog().file().toFile(), found.entry().line());
        }
    }

    /**
     * The source catalog entry a key names — its namespace's file when
     * the key carries one, else the first source catalog declaring it —
     * or null. Pure over the record, so the resolution is a unit test.
     */
    static Target find(Catalogs catalogs, String key) {
        String[] wanted = I18nKeys.split(key);
        for (Catalog catalog : catalogs.source()) {
            if (wanted[0] != null && !catalog.namespace().equals(wanted[0])) {
                continue;
            }
            Entry entry = catalog.entries().get(wanted[1]);
            if (entry != null) {
                return new Target(catalog, entry);
            }
        }
        return null;
    }
}
