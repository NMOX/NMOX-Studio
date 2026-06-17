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

    private static final String[] PRIVILEGED = {
        "Templates/ClientSide/html.html",
        "Templates/ClientSide/javascript.js",
        "Templates/ClientSide/json.json",
        "Templates/ClientSide/css.css",
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
