package org.nmox.studio.apiclient.ui;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A first-run API workspace is named in the reader's language.
 *
 * <p>A German walk photographed a fully translated API Studio whose seeded
 * collection, request and environment read My API / Health check / Local.
 * The English never passed through a bundle, so no l10n gate could see it.
 *
 * <p>{@code base_url} is deliberately NOT translated and is asserted here:
 * it is an identifier the user types inside {@code {{…}}}, and translating
 * an identifier breaks every request that references it.
 */
class StarterWorkspaceSpeaksTest {

    @Test
    @DisplayName("the seeded names come from the bundle, and the active environment follows")
    void theSeedIsTranslated() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            Workspace w = ApiClientTopComponent.starterWorkspace();
            assertThat(w.collections.get(0).name).isEqualTo("Meine API").isNotEqualTo("My API");
            assertThat(w.collections.get(0).requests.get(0).name)
                    .isEqualTo("Statusprüfung").isNotEqualTo("Health check");
            assertThat(w.environments.get(0).name).isEqualTo("Lokal");
            assertThat(w.activeEnvironment)
                    .as("the active environment is resolved BY NAME, so the two must move together")
                    .isEqualTo(w.environments.get(0).name);
            assertThat(w.active()).as("and the lookup must still find it").isNotNull();
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("base_url stays an identifier in every language")
    void theVariableIsNotProse() {
        Locale original = Locale.getDefault();
        try {
            for (Locale l : new Locale[] {Locale.ENGLISH, Locale.GERMAN, Locale.of("ru"),
                    Locale.of("hi"), Locale.of("zh")}) {
                Locale.setDefault(l);
                Workspace w = ApiClientTopComponent.starterWorkspace();
                assertThat(w.environments.get(0).variables).containsKey("base_url");
                assertThat(w.collections.get(0).requests.get(0).url)
                        .as("the seeded URL references the variable by its identifier")
                        .isEqualTo("{{base_url}}/health");
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
