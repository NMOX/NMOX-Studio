package org.nmox.studio.editor.fullstack;

import java.io.File;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;

/**
 * ⌘-click {@code process.env.DATABASE_URL} (or Vite's
 * {@code import.meta.env.VITE_…}) and land on the key's line in the
 * project's env family (v2.31.0, the full-stack wishlist). Refuses
 * with the file list when the key is declared nowhere — the honest
 * answer to "where is this configured?".
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = HyperlinkProviderExt.class, position = 16),
    @MimeRegistration(mimeType = "text/typescript", service = HyperlinkProviderExt.class, position = 16)
})
@org.openide.util.NbBundle.Messages({
    "EnvKeyHyperlink_tooltip=Go to the key's .env declaration",
    "EnvKeyHyperlink_notDeclared={0} is not declared in this project''s .env family"
})
public final class EnvKeyHyperlink extends ProjectJumpHyperlink {

    @Override
    protected int[] spanAt(String text, int offset) {
        return EnvKeys.keySpanAt(text, offset);
    }

    @Override
    protected String tooltip() {
        return Bundle.EnvKeyHyperlink_tooltip();
    }

    @Override
    protected void click(String text, int[] span, File projectDir) {
        String key = text.substring(span[0], span[1]);
        EnvKeys.EnvKey found = EnvKeys.scan(projectDir).stream()
                .filter(k -> k.name().equals(key))
                .findFirst().orElse(null);
        if (found == null) {
            status(Bundle.EnvKeyHyperlink_notDeclared(key));
        } else {
            openAt(found.file(), found.offset());
        }
    }
}
