package org.nmox.studio.apiclient.ui;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.apiclient.api.WorkspaceIO;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.core.util.DocsFixtures;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The workspace every language's API Studio picture sends. It carries no
 * fixture content: the names are the product's own starter translations, so
 * the staged file must equal what the product would create on a first run.
 */
class DocsApiTest {

    @Test
    @DisplayName("the staged workspace is the product's own starter, sending /health to the forge's loopback")
    void stagesTheProductsOwnStarter(@TempDir Path home) throws Exception {
        File dir = new DocsApi().stage(home.toFile(), "{\"en\":{}}", "en");
        assertThat(dir).isEqualTo(DocsFixtures.projectDir(home.toFile()));
        ApiModel.Workspace staged = WorkspaceIO.load(dir);
        ApiModel.Workspace starter = ApiClientTopComponent.starterWorkspace();

        assertThat(staged.collections).hasSize(1);
        assertThat(staged.collections.get(0).name).isEqualTo(starter.collections.get(0).name);
        assertThat(staged.collections.get(0).requests.get(0).name)
                .isEqualTo(starter.collections.get(0).requests.get(0).name);
        assertThat(staged.collections.get(0).requests.get(0).url).isEqualTo("{{base_url}}/health");
        assertThat(staged.activeEnvironment).isEqualTo(starter.activeEnvironment);
        assertThat(staged.active().variables.get("base_url")).isEqualTo("http://localhost:3000");
    }

    @Test
    @DisplayName("arranging with no API Studio window open does nothing and throws nothing")
    void arrangeWithoutAWindowIsQuiet() {
        new DocsApi().arrange();
    }
}
