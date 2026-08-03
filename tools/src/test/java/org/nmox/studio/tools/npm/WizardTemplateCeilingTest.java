package org.nmox.studio.tools.npm;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform wizard's templates are a THIRD generator home for npm
 * pins (beside rack's ProjectTemplates and the learning catalog), and
 * the only one Dependabot bumps directly — which is how its vue
 * template marched to vite ^8 (refuses node &lt;22.12; a starter must
 * run on a learner's node, the v1.237.0 ceiling) and its react
 * template sat on dead react-scripts while Dependabot kept the react
 * pin fresh around it. This gate holds the proven line: any future
 * bump across a ceiling fails CI on the bump PR itself, so a human
 * decides instead of a bot.
 */
class WizardTemplateCeilingTest {

    private JSONObject pkg(String template) throws Exception {
        String path = "templates/" + template + "/package.json";
        try (InputStream is = WebProjectWizardIterator.class.getResourceAsStream(path)) {
            assertThat(is).as(path + " exists on the classpath").isNotNull();
            return new JSONObject(new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${projectName}", "ceiling-probe"));
        }
    }

    @Test
    @DisplayName("react is Vite, not CRA, on the proven vite-6 peer set")
    void reactIsViteNotCra() throws Exception {
        JSONObject p = pkg("react");
        JSONObject dev = p.getJSONObject("devDependencies");
        assertThat(dev.has("react-scripts"))
                .as("react-scripts is dead upstream and cannot install beside React 19")
                .isFalse();
        assertThat(dev.getString("vite")).startsWith("^6.");
        assertThat(dev.getString("@vitejs/plugin-react")).startsWith("^5.");
        assertThat(p.getJSONObject("dependencies").getString("react")).startsWith("^19.");
        assertThat(p.getJSONObject("scripts").getString("dev")).isEqualTo("vite");
    }

    @Test
    @DisplayName("vue stays on the proven vite-6 peer set")
    void vueObeysTheCeiling() throws Exception {
        JSONObject dev = pkg("vue").getJSONObject("devDependencies");
        // vite stays ^6: 7+ requires node >=22.12 and a starter must run
        // on whatever node a learner has (the v1.237.0 ceiling); the
        // plugin major matches (plugin-vue 6 needs vite 7)
        assertThat(dev.getString("vite")).startsWith("^6.");
        assertThat(dev.getString("@vitejs/plugin-vue")).startsWith("^5.");
    }

    @Test
    @DisplayName("the wizard's react file list matches the template dir")
    void wizardFileListMatchesTemplate() throws Exception {
        // the iterator copies a HARDCODED list — a template-dir rename
        // that misses the java half throws at wizard Finish, so pin the
        // pairing here where a plain test can see both sides
        for (String path : new String[]{
            "templates/react/package.json", "templates/react/index.html",
            "templates/react/vite.config.js", "templates/react/src/main.jsx",
            "templates/react/src/App.jsx", "templates/react/src/index.css"}) {
            try (InputStream is = WebProjectWizardIterator.class.getResourceAsStream(path)) {
                assertThat(is).as(path + " (named in createReactProject) exists").isNotNull();
            }
        }
    }
}
