package org.nmox.studio.apiclient.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The route-line rule for "Test in API Studio" (v2.34.0): given the
 * LINE under the caret, the verb and path of an Express/Fastify/Koa
 * route registration — or null. The pattern is the v1.292.0 outline
 * rule (app/router-shaped receiver, a real HTTP verb, a string path),
 * kept deliberately narrow for the same reason it is everywhere else:
 * a wrong request drafted is worse than none. Pure, so the boundary
 * rules are unit tests.
 */
public final class RouteLine {

    private RouteLine() {
    }

    // the v1.292.0 rule, one line's worth (editor's Routes.SERVER_ROUTE
    // is module-private to editor; the rule itself is the shared law)
    private static final Pattern ROUTE = Pattern.compile(
            "^\\s*(?:module\\.exports\\s*=\\s*)?"
            + "[A-Za-z0-9_$]*(?:app|App|router|Router|server|Server|api|Api)"
            + "\\s*\\.\\s*(get|post|put|patch|delete|head|options)"
            + "\\s*\\(\\s*[`'\"](/[^`'\"]*)[`'\"]");

    /** {verb, path} for a route-registration line, or null. */
    public static String[] parse(String line) {
        if (line == null) {
            return null;
        }
        Matcher m = ROUTE.matcher(line);
        if (!m.find()) {
            return null;
        }
        return new String[] {m.group(1).toUpperCase(java.util.Locale.ROOT), m.group(2)};
    }
}
