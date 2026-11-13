package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.service.WorkspaceTrust;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Express is pinned in TWO generators: the EXPRESS_API project template
 * and the express learning space. A learner who finishes the space and
 * starts a project must meet the same Express, and for six months they
 * did not: the template moved to 5 while the space stayed on ^4.19.2
 * (the pin sits inside a JSON string, where Dependabot does not read).
 * Express 4 and 5 differ where a first API touches them - under 5
 * {@code req.body} is undefined for a request with no parsed body, so
 * the space's own POST handler answered 500 where 4 answered 201.
 *
 * Both numbers come from what the real generators write to disk, so
 * neither home can move alone (the AngularSpacePinParityTest law).
 */
class ExpressSpacePinParityTest {

    private static final Pattern MAJOR = Pattern.compile("^[\\^~>=\\s]*(\\d+)\\b");

    @Test
    @DisplayName("the express space and the EXPRESS_API template pin the same Express major")
    void spaceMatchesTemplate(@TempDir Path tmp) throws Exception {
        File tplDir = tmp.resolve("tpl").toFile();
        ProjectTemplates.EXPRESS_API.generate(tplDir, "parity-express");
        String tplPin = expressPin(tplDir);

        WorkspaceTrust.clearForTest(); // create() pre-trusts; never the real grants
        String realHome = System.getProperty("user.home");
        System.setProperty("user.home", tmp.resolve("home").toString());
        String spacePin;
        try {
            LearningCatalog.Space space = LearningCatalog.find("express");
            assertThat(space).as("express space present in the catalog").isNotNull();
            spacePin = expressPin(LearningSpace.create(space));
        } finally {
            System.setProperty("user.home", realHome);
        }

        assertThat(major(spacePin))
                .as("the express learning space pins \"%s\" and the EXPRESS_API template"
                        + " pins \"%s\": move both together, and re-prove the space"
                        + " (npm install, node server.js) on the new major",
                        spacePin, tplPin)
                .isEqualTo(major(tplPin));
    }

    private static String expressPin(File projectDir) throws Exception {
        JSONObject pkg = new JSONObject(
                Files.readString(projectDir.toPath().resolve("package.json")));
        return pkg.getJSONObject("dependencies").getString("express");
    }

    private static int major(String range) {
        Matcher m = MAJOR.matcher(range);
        assertThat(m.find()).as("a version range with a leading major: \"%s\"", range).isTrue();
        return Integer.parseInt(m.group(1));
    }
}
