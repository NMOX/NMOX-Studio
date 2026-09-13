package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Homebrew cask has TWO homes: the checked-in
 * {@code Casks/nmox-studio.rb} a user's tap actually reads, and the
 * heredoc in {@code .github/workflows/release.yml} that REGENERATES it
 * on every release. Nothing held them together until v2.149.0, and the
 * cost was measured on David's own machine: both homes carried a
 * {@code verified:} url parameter and a bare {@code postflight} block,
 * so every {@code brew upgrade} and {@code brew doctor} printed two
 * deprecation warnings naming our tap. Editing only the file would be
 * undone by the next release; editing only the workflow would leave the
 * shipped tap stale — which is v2.131.0's law one artifact over:
 * <em>the defect is the second home, not the disagreement</em>.
 *
 * <p>Two laws. The generator's output must equal the checked-in cask
 * byte for byte once the two stamped lines (version, sha256) are
 * accounted for — so a hand edit to either home fails the build naming
 * the straggler. And neither home may use a stanza Homebrew has
 * deprecated, so the warning class cannot come back silently.
 */
class CaskGeneratorParityTest {

    private static final String OPEN = "          cat > Casks/nmox-studio.rb <<EOF\n";
    private static final String CLOSE = "\n          EOF\n";

    private static String read(String repoRelative) throws Exception {
        Path p = Path.of("..", repoRelative);
        assertThat(p).as(repoRelative + " visible from application module").exists();
        return Files.readString(p).replace("\r\n", "\n");
    }

    /** The cask the release workflow would write, with the stamped lines left as placeholders. */
    private static String generated(String workflow) {
        int start = workflow.indexOf(OPEN);
        assertThat(start).as("release.yml still generates the cask with a heredoc").isNotEqualTo(-1);
        int body = start + OPEN.length();
        int end = workflow.indexOf(CLOSE, body);
        assertThat(end).as("the cask heredoc is terminated").isNotEqualTo(-1);
        StringBuilder out = new StringBuilder();
        for (String line : workflow.substring(body, end).split("\n", -1)) {
            // the heredoc body carries the YAML block's ten-space indent
            out.append(line.startsWith("          ") ? line.substring(10) : line).append('\n');
        }
        return out.toString();
    }

    private static String stamped(String cask, String stanza) {
        Matcher m = Pattern.compile("(?m)^  " + stanza + " \"([^\"]+)\"$").matcher(cask);
        assertThat(m.find()).as("the checked-in cask carries a " + stanza + " stanza").isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("the checked-in cask is byte-identical to what release.yml regenerates")
    void oneCaskTwoHomesInLockstep() throws Exception {
        String cask = read("Casks/nmox-studio.rb");
        String fromGenerator = generated(read(".github/workflows/release.yml"))
                .replace("${VERSION}", stamped(cask, "version"))
                .replace("${SHA256}", stamped(cask, "sha256"));
        assertThat(fromGenerator)
                .as("release.yml's heredoc and Casks/nmox-studio.rb must not drift")
                .isEqualTo(cask);
    }

    @Test
    @DisplayName("neither home uses a stanza Homebrew has deprecated")
    void noDeprecatedStanzas() throws Exception {
        String cask = read("Casks/nmox-studio.rb");
        String generator = generated(read(".github/workflows/release.yml"));
        for (String home : new String[] {"Casks/nmox-studio.rb", "release.yml"}) {
            String text = home.startsWith("Casks") ? cask : generator;
            assertThat(text)
                    .as(home + " must not pass `verified:` in the url stanza "
                            + "(deprecated; the default URL verification behaviour covers it)")
                    .doesNotContain("verified:");
            assertThat(Pattern.compile("(?m)^\\s*p(?:re|ost)flight\\s+do\\b").matcher(text).find())
                    .as(home + " must use `preflight_steps`/`postflight_steps`, "
                            + "never the deprecated bare `preflight`/`postflight` block")
                    .isFalse();
        }
    }
}
