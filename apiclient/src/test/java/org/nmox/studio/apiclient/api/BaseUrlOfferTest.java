package org.nmox.studio.apiclient.api;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Environment;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.nmox.studio.core.spi.LiveServings;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {{baseUrl}} offer decision core: who gets offered, who is left
 * alone, which key gets written, and the once-per-session guard.
 */
class BaseUrlOfferTest {

    private static final File PROJECT = new File("/tmp/shop").getAbsoluteFile();
    private static final File OTHER = new File("/tmp/blog").getAbsoluteFile();
    private static final String URL = "http://localhost:5173";

    private static LiveServings.Serving web(String url, File dir) {
        return new LiveServings.Serving("dev-1", "DEV-SERVER", url,
                LiveServings.Kind.WEB, dir);
    }

    private static LiveServings.Serving chain(String url, File dir) {
        return new LiveServings.Serving("anvil-1", "ANVIL", url,
                LiveServings.Kind.CHAIN, dir);
    }

    private static Workspace workspaceWithEnv(String envName, String key, String value) {
        Workspace w = new Workspace();
        Environment env = new Environment();
        env.name = envName;
        if (key != null) {
            env.variables.put(key, value);
        }
        w.environments.add(env);
        w.activeEnvironment = envName;
        return w;
    }

    @Test
    @DisplayName("a WEB serving for the aimed project + an env without baseUrl: offer")
    void plainOffer() {
        Workspace w = workspaceWithEnv("Dev", null, null);
        BaseUrlOffer.Offer offer = BaseUrlOffer.shouldOffer(
                List.of(web(URL, PROJECT)), PROJECT, w, new HashSet<>());
        assertThat(offer).isNotNull();
        assertThat(offer.url()).isEqualTo(URL);
        assertThat(offer.envName()).isEqualTo("Dev");
        assertThat(offer.key()).isEqualTo("baseUrl");
        assertThat(offer.createEnvironment()).isFalse();
    }

    @Test
    @DisplayName("no environments at all: the friendly path creates \"Local\"")
    void createsLocal() {
        BaseUrlOffer.Offer offer = BaseUrlOffer.shouldOffer(
                List.of(web(URL, PROJECT)), PROJECT, new Workspace(), new HashSet<>());
        assertThat(offer).isNotNull();
        assertThat(offer.createEnvironment()).isTrue();
        assertThat(offer.envName()).isEqualTo("Local");
        assertThat(offer.key()).isEqualTo("baseUrl");
    }

    @Test
    @DisplayName("an env that already has any baseUrl spelling is left alone")
    void variantSpellingsBlock() {
        for (String key : new String[]{"baseUrl", "base_url", "BASE_URL", "base-url"}) {
            Workspace w = workspaceWithEnv("Dev", key, "http://localhost:3000");
            assertThat(BaseUrlOffer.shouldOffer(
                    List.of(web(URL, PROJECT)), PROJECT, w, new HashSet<>()))
                    .as("variable %s should block the offer", key)
                    .isNull();
        }
    }

    @Test
    @DisplayName("a BLANK base_url variant gets the offer aimed at that exact key")
    void blankVariantIsFilledNotDuplicated() {
        Workspace w = workspaceWithEnv("Local", "base_url", "");
        BaseUrlOffer.Offer offer = BaseUrlOffer.shouldOffer(
                List.of(web(URL, PROJECT)), PROJECT, w, new HashSet<>());
        assertThat(offer).isNotNull();
        assertThat(offer.key()).isEqualTo("base_url"); // {{base_url}} requests resolve
    }

    @Test
    @DisplayName("CHAIN servings, other projects, and blank URLs never offer")
    void irrelevantServings() {
        Workspace w = workspaceWithEnv("Dev", null, null);
        Set<String> none = new HashSet<>();
        assertThat(BaseUrlOffer.shouldOffer(
                List.of(chain("http://127.0.0.1:8545", PROJECT)), PROJECT, w, none)).isNull();
        assertThat(BaseUrlOffer.shouldOffer(
                List.of(web(URL, OTHER)), PROJECT, w, none)).isNull();
        assertThat(BaseUrlOffer.shouldOffer(
                List.of(web("", PROJECT)), PROJECT, w, none)).isNull();
        assertThat(BaseUrlOffer.shouldOffer(
                List.of(web(URL, null)), PROJECT, w, none)).isNull();
        assertThat(BaseUrlOffer.shouldOffer(List.of(), PROJECT, w, none)).isNull();
    }

    @Test
    @DisplayName("no aimed project or no workspace: no offer, no exception")
    void nullTolerance() {
        Workspace w = workspaceWithEnv("Dev", null, null);
        assertThat(BaseUrlOffer.shouldOffer(
                List.of(web(URL, PROJECT)), null, w, new HashSet<>())).isNull();
        assertThat(BaseUrlOffer.shouldOffer(
                List.of(web(URL, PROJECT)), PROJECT, null, new HashSet<>())).isNull();
        assertThat(BaseUrlOffer.shouldOffer(
                null, PROJECT, w, new HashSet<>())).isNull();
    }

    @Test
    @DisplayName("the session guard: the same (url + project) is offered once")
    void guardIsPerUrlAndProject() {
        Workspace w = workspaceWithEnv("Dev", null, null);
        Set<String> offered = new HashSet<>();
        BaseUrlOffer.Offer first = BaseUrlOffer.shouldOffer(
                List.of(web(URL, PROJECT)), PROJECT, w, offered);
        assertThat(first).isNotNull();
        offered.add(first.guardKey());

        assertThat(BaseUrlOffer.shouldOffer(
                List.of(web(URL, PROJECT)), PROJECT, w, offered)).isNull();

        // a DIFFERENT url for the same project is a fresh offer
        BaseUrlOffer.Offer other = BaseUrlOffer.shouldOffer(
                List.of(web("http://localhost:4173", PROJECT)), PROJECT, w, offered);
        assertThat(other).isNotNull();
        assertThat(other.guardKey()).isNotEqualTo(first.guardKey());
    }

    @Test
    @DisplayName("a guarded serving does not shadow a later unguarded one in the snapshot")
    void guardedServingIsSkippedNotTerminal() {
        Workspace w = workspaceWithEnv("Dev", null, null);
        Set<String> offered = new HashSet<>();
        offered.add(BaseUrlOffer.guardKey(URL, PROJECT));
        BaseUrlOffer.Offer offer = BaseUrlOffer.shouldOffer(
                List.of(web(URL, PROJECT), web("http://localhost:4173", PROJECT)),
                PROJECT, w, offered);
        assertThat(offer).isNotNull();
        assertThat(offer.url()).isEqualTo("http://localhost:4173");
    }

    @Test
    @DisplayName("a dangling activeEnvironment falls back to the first environment")
    void danglingActiveEnvironment() {
        Workspace w = workspaceWithEnv("Dev", null, null);
        w.activeEnvironment = "Prod"; // renamed away
        BaseUrlOffer.Offer offer = BaseUrlOffer.shouldOffer(
                List.of(web(URL, PROJECT)), PROJECT, w, new HashSet<>());
        assertThat(offer).isNotNull();
        assertThat(offer.envName()).isEqualTo("Dev");
    }

    @Test
    @DisplayName("hasBaseUrl is the re-check the accept path uses — blank does not count")
    void hasBaseUrlSemantics() {
        Environment env = new Environment();
        assertThat(BaseUrlOffer.hasBaseUrl(env)).isFalse();
        env.variables.put("base_url", "");
        assertThat(BaseUrlOffer.hasBaseUrl(env)).isFalse();
        env.variables.put("base_url", "http://localhost:3000");
        assertThat(BaseUrlOffer.hasBaseUrl(env)).isTrue();
    }
}
