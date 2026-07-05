package org.nmox.studio.rack.projectstudio;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one HTML edit the kits perform: insert markup just before
 * {@code </head>}. Callers decide idempotence (they only hand over what
 * the page doesn't already carry); this helper owns the mechanics.
 * Extracted from the PWA Kit's head wiring so the Classic Kit's script
 * tags ride the same proven seam.
 */
final class HtmlWiring {

    private static final Pattern HEAD_CLOSE
            = Pattern.compile("</head>", Pattern.CASE_INSENSITIVE);

    private HtmlWiring() {
    }

    /**
     * Returns the page with {@code additions} inserted immediately before
     * the first {@code </head>} (case-insensitive). The page comes back
     * unchanged when there is nothing to add or no head to wire into.
     */
    static String insertBeforeHeadClose(String html, String additions) {
        if (additions.isEmpty()) {
            return html;
        }
        Matcher close = HEAD_CLOSE.matcher(html);
        if (!close.find()) {
            return html;
        }
        int at = close.start();
        return html.substring(0, at) + additions + html.substring(at);
    }
}
