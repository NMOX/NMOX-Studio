package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenJFX version lives in FOUR homes — ui/pom.xml's two
 * provided-scope deps (the compile-time API), bundle-jre.sh (the
 * mac/linux runtime), release.yml (the windows runtime), and NOTICE
 * (the license attribution) — and nothing bound them together until
 * v1.249.0: the v1.248.0 bump moved all four BY HAND, and a partial
 * future bump would ship per-OS runtimes on different WebKits, or
 * compile against an API the runtime doesn't carry (the exact skew
 * ledger 74 refused from Dependabot). One version, four homes,
 * lockstep or the build fails naming the straggler.
 */
class FxPinLockstepTest {

    private static String read(String repoRelative) throws Exception {
        Path p = Path.of("..", repoRelative);
        assertThat(p).as(repoRelative + " visible from application module").exists();
        return Files.readString(p);
    }

    private static String extract(String text, String regex, String where) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertThat(m.find()).as(where + " carries an FX version pin").isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("ui pom, bundle-jre.sh, release.yml and NOTICE pin ONE OpenJFX version")
    void fourHomesOneVersion() throws Exception {
        String script = extract(read("packaging/tools/bundle-jre.sh"),
                "FX_VERSION=\"([0-9.]+)\"", "bundle-jre.sh");
        String workflow = extract(read(".github/workflows/release.yml"),
                "\\$fxVersion = \"([0-9.]+)\"", "release.yml");
        String pom = read("ui/pom.xml");
        Matcher fx = Pattern.compile(
                "<groupId>org\\.openjfx</groupId>\\s*<artifactId>[a-z-]+</artifactId>\\s*"
                + "<version>([0-9.]+)</version>").matcher(pom);
        int deps = 0;
        while (fx.find()) {
            deps++;
            assertThat(fx.group(1))
                    .as("ui/pom.xml openjfx dep #" + deps + " matches bundle-jre.sh")
                    .isEqualTo(script);
        }
        assertThat(deps).as("both provided FX deps found in ui/pom.xml").isEqualTo(2);
        assertThat(workflow).as("release.yml matches bundle-jre.sh").isEqualTo(script);
        assertThat(read("NOTICE"))
                .as("NOTICE attributes the bundled OpenJFX at the pinned version")
                .contains("OpenJFX " + script);
    }
}
