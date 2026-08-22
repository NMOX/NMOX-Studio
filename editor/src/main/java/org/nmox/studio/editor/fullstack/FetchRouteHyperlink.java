package org.nmox.studio.editor.fullstack;

import java.io.File;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;

/**
 * ⌘-click the {@code '/api/users'} in a {@code fetch(}/{@code axios.*}
 * call and land on the Express/Fastify/Koa route that serves it
 * (v2.31.0, the full-stack wishlist) — the client and the server of
 * the same project, finally on speaking terms. Exact-path match; a
 * path no route declares refuses with the sweep's honest scope.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = HyperlinkProviderExt.class, position = 17),
    @MimeRegistration(mimeType = "text/typescript", service = HyperlinkProviderExt.class, position = 17)
})
public final class FetchRouteHyperlink extends ProjectJumpHyperlink {

    @Override
    protected int[] spanAt(String text, int offset) {
        return Routes.clientPathSpanAt(text, offset);
    }

    @Override
    protected String tooltip() {
        return "Go to the route that serves this path";
    }

    @Override
    protected void click(String text, int[] span, File projectDir) {
        String path = text.substring(span[0], span[1]);
        Routes.Route found = Routes.findRoute(projectDir, path);
        if (found == null) {
            status("No route registers " + path
                    + " in this project's JS/TS sources");
        } else {
            openAt(found.file(), found.offset());
        }
    }
}
