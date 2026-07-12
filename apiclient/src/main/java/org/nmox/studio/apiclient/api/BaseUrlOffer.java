package org.nmox.studio.apiclient.api;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.nmox.studio.apiclient.model.ApiModel.Environment;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.nmox.studio.core.spi.LiveServings;

/**
 * The {{baseUrl}} offer, decided purely: when the rack serves a WEB URL
 * for the aimed project and the active environment has no base URL yet,
 * API Studio may offer — once per (url + project) per session — to set
 * it. All spellings count as "has one" ({@code baseUrl},
 * {@code base_url}, {@code BASE_URL}…): offering a second variable next
 * to an existing variant would just split the requests.
 *
 * <p>The friendly path: a workspace with no environments at all gets
 * "create environment "Local" with {{baseUrl}}" instead of silence.
 */
public final class BaseUrlOffer {

    /** The canonical variable name written when no variant exists yet. */
    public static final String CANONICAL_KEY = "baseUrl";
    /** The environment created when the workspace has none. */
    public static final String DEFAULT_ENV_NAME = "Local";

    /**
     * One concrete offer: the URL to set, which environment and
     * variable key to write, whether the environment must be created
     * first, and the session-guard key to record.
     */
    public record Offer(String url, String envName, String key,
            boolean createEnvironment, String guardKey) {
    }

    private BaseUrlOffer() {
    }

    /**
     * The decision core. Null means "no offer": nothing relevant is
     * serving, the environment already has a base URL, or this
     * (url + project) was already offered this session.
     */
    public static Offer shouldOffer(List<LiveServings.Serving> servings,
            File projectDir, Workspace workspace, Set<String> alreadyOffered) {
        if (servings == null || projectDir == null || workspace == null
                || alreadyOffered == null) {
            return null;
        }
        for (LiveServings.Serving serving : servings) {
            if (serving.kind() != LiveServings.Kind.WEB) {
                continue;
            }
            if (serving.url() == null || serving.url().isBlank()) {
                continue;
            }
            if (serving.projectDir() == null
                    || !sameDir(serving.projectDir(), projectDir)) {
                continue;
            }
            String guard = guardKey(serving.url(), projectDir);
            if (alreadyOffered.contains(guard)) {
                continue; // this one was offered; another serving may still qualify
            }
            if (workspace.environments.isEmpty()) {
                return new Offer(serving.url(), DEFAULT_ENV_NAME, CANONICAL_KEY,
                        true, guard);
            }
            Environment env = targetEnvironment(workspace);
            if (hasBaseUrl(env)) {
                return null; // the environment is already aimed somewhere
            }
            return new Offer(serving.url(), env.name, keyFor(env), false, guard);
        }
        return null;
    }

    /** The session guard: one offer per URL per project. */
    public static String guardKey(String url, File projectDir) {
        return url + "|" + projectDir.getAbsolutePath();
    }

    /**
     * True when the environment carries a non-blank base-URL variable
     * under any spelling. Public so the accept path can re-check right
     * before writing — a variable the user typed meanwhile is never
     * clobbered.
     */
    public static boolean hasBaseUrl(Environment env) {
        for (Map.Entry<String, String> variable : env.variables.entrySet()) {
            if (isBaseUrlName(variable.getKey())
                    && variable.getValue() != null && !variable.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The key the offer would write: an existing blank variant (so a
     * starter {@code {{base_url}}} request resolves), else the
     * canonical {@code baseUrl}.
     */
    static String keyFor(Environment env) {
        for (Map.Entry<String, String> variable : env.variables.entrySet()) {
            if (isBaseUrlName(variable.getKey())) {
                return variable.getKey();
            }
        }
        return CANONICAL_KEY;
    }

    /** The active environment, or the first one when the active name dangles. */
    static Environment targetEnvironment(Workspace workspace) {
        Environment active = workspace.active();
        return active != null ? active : workspace.environments.get(0);
    }

    /** baseUrl, base_url, BASE-URL… — one name, many spellings. */
    static boolean isBaseUrlName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").equals("baseurl");
    }

    private static boolean sameDir(File a, File b) {
        return a.getAbsoluteFile().equals(b.getAbsoluteFile());
    }
}
