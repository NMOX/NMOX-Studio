package org.nmox.studio.tools.npm;

import org.netbeans.spi.project.ui.PrivilegedTemplates;
import org.netbeans.spi.project.ui.RecommendedTemplates;

/**
 * Scopes the platform's New File wizard to what a web project actually
 * holds. Without this the wizard offers every template the IDE knows
 * (EJBs, persistence units, and other server-Java relics); with it, the
 * categories collapse to the web stack and the everyday files float to
 * the top of the list.
 */
final class WebProjectRecommendedTemplates implements RecommendedTemplates, PrivilegedTemplates {

    private static final String[] TYPES = {
        "web", "html5", "javascript", "json", "XML", "simple-files"
    };

    /**
     * Every entry here must be a REGISTERED template path, or the
     * "float the everyday files to the top" feature silently points at
     * nothing — which is exactly what happened from the day this class
     * was written until v1.286.0: the html/javascript/json/css entries
     * named ClientSide paths no shipped module ever registered (the JS
     * IDE had no JavaScript file in New File at all). The first three are
     * ours (editor's templates package registers them, layer-gated by
     * PrivilegedTemplatesExistTest); the rest are the platform's real
     * paths, read out of the assembled app's module layers.
     */
    private static final String[] PRIVILEGED = {
        "Templates/ClientSide/javascript.js",
        "Templates/ClientSide/typescript.ts",
        "Templates/ClientSide/json.json",
        "Templates/Other/html.html",
        "Templates/Other/CascadeStyleSheet.css",
        "Templates/Other/file"
    };

    @Override
    public String[] getRecommendedTypes() {
        return TYPES.clone();
    }

    @Override
    public String[] getPrivilegedTemplates() {
        return PRIVILEGED.clone();
    }
}
