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
 * path no route declares refuses with the sweep's honest scope — and a
 * sweep that stopped at its cap says so rather than claim no route exists.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = HyperlinkProviderExt.class, position = 17),
    @MimeRegistration(mimeType = "text/typescript", service = HyperlinkProviderExt.class, position = 17)
})
@org.openide.util.NbBundle.Messages({
    "FetchRouteHyperlink_tooltip=Go to the route that serves this path",
    "FetchRouteHyperlink_noRoute=No route registers {0} in this project''s JS/TS sources",
    "# {0} - the path, {1} - how many files the lookup reads at most",
    "FetchRouteHyperlink_noRouteCapped=No route registers {0} in the first {1} of this project''s JS/TS files, and only those are read"
})
public final class FetchRouteHyperlink extends ProjectJumpHyperlink {

    @Override
    protected int[] spanAt(String text, int offset) {
        return Routes.clientPathSpanAt(text, offset);
    }

    @Override
    protected String tooltip() {
        return Bundle.FetchRouteHyperlink_tooltip();
    }

    @Override
    protected void click(String text, int[] span, File projectDir) {
        String path = text.substring(span[0], span[1]);
        Routes.Lookup found = Routes.lookup(projectDir, path);
        if (found.route() != null) {
            openAt(found.route().file(), found.route().offset());
        } else if (found.complete()) {
            status(Bundle.FetchRouteHyperlink_noRoute(path));
        } else {
            // the census stopped at its cap: "no route" would be a claim
            // about files it never read (a monorepo's server package)
            status(Bundle.FetchRouteHyperlink_noRouteCapped(path, Routes.MAX_FILES));
        }
    }
}
