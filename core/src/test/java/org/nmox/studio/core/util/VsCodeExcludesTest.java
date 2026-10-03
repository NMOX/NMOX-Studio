package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * files.exclude and search.exclude, read the way VS Code reads them — and
 * every form this product does not honour named rather than guessed at.
 */
class VsCodeExcludesTest {

    private static VsCodeExcludes of(String json) {
        return VsCodeExcludes.parse(json, false);
    }

    @Test
    @DisplayName("true hides, at the depth the pattern names; a folder that matches takes what is beneath it")
    void filesExclude() {
        VsCodeExcludes x = of("""
                {
                  // generated
                  "files.exclude": { "**/dist": true, "*.log": true, "docs/api": true, },
                }
                """);
        assertThat(x.hides("dist")).isTrue();
        assertThat(x.hides("packages/web/dist")).isTrue();
        assertThat(x.hides("packages/web/dist/app.js")).as("beneath a hidden folder").isTrue();
        assertThat(x.hides("debug.log")).isTrue();
        assertThat(x.hides("logs/debug.log")).as("*.log names entries of the settings' folder only").isFalse();
        assertThat(x.hides("docs/api")).isTrue();
        assertThat(x.hides("docs/api/index.html")).isTrue();
        assertThat(x.hides("docs")).isFalse();
        assertThat(x.hides("src/app.js")).isFalse();
        assertThat(x.hides("")).isFalse();
        assertThat(x.hides(null)).isFalse();
        assertThat(x.notHonoured()).isEmpty();
        assertThat(x.hiddenPatterns()).containsExactly("**/dist", "*.log", "docs/api");
    }

    @Test
    @DisplayName("false says nothing: it switches off a default this product never applied")
    void falseSaysNothing() {
        VsCodeExcludes x = of("{\"files.exclude\": {\"**/.git\": false, \"**/out\": true}}");
        assertThat(x.hides(".git")).isFalse();
        assertThat(x.hides("out")).isTrue();
        assertThat(x.hiddenPatterns()).containsExactly("**/out");
        assertThat(x.notHonoured()).as("a false is a statement, not a refusal").isEmpty();
    }

    @Test
    @DisplayName("a conditional exclude (an object with when) is not honoured, and named")
    void conditionalIsNamedNotHonoured() {
        VsCodeExcludes x = of("{\"files.exclude\": {\"**/*.js\": {\"when\": \"$(basename).ts\"}, \"**/tmp\": true}}");
        assertThat(x.hides("src/app.js")).as("VS Code hides it only beside app.ts; here it is shown").isFalse();
        assertThat(x.hides("tmp")).isTrue();
        assertThat(x.notHonoured()).containsExactly("files.exclude \"**/*.js\" is conditional (\"when\")");
    }

    @Test
    @DisplayName("a value that is neither true nor false is not honoured, and named")
    void otherValues() {
        VsCodeExcludes x = of("{\"files.exclude\": {\"a\": \"yes\", \"b\": 1, \"c\": null, \"d\": {}, \"e\": {\"when\": 3}}}");
        for (String p : new String[] {"a", "b", "c", "d", "e"}) {
            assertThat(x.hides(p)).as(p).isFalse();
        }
        assertThat(x.notHonoured()).containsExactly(
                "files.exclude \"a\" is neither true nor false",
                "files.exclude \"b\" is neither true nor false",
                "files.exclude \"c\" is neither true nor false",
                "files.exclude \"d\" is neither true nor false",
                "files.exclude \"e\" is neither true nor false");
    }

    @Test
    @DisplayName("a setting that is not an object excludes nothing, and says so")
    void notAnObject() {
        VsCodeExcludes x = of("{\"files.exclude\": true, \"search.exclude\": [\"**/dist\"]}");
        assertThat(x.isEmpty()).isTrue();
        assertThat(x.hides("dist")).isFalse();
        assertThat(x.skipsSearch("dist")).isFalse();
        assertThat(x.notHonoured()).containsExactly(
                "files.exclude is not an object of patterns", "search.exclude is not an object of patterns");
    }

    @Test
    @DisplayName("text that is not JSON excludes nothing, and says so")
    void notJson() {
        VsCodeExcludes x = of("{ \"files.exclude\": ");
        assertThat(x.isEmpty()).isTrue();
        assertThat(x.notHonoured()).hasSize(1).first().asString().startsWith("the file is not a JSON object");
        assertThat(of(null).isEmpty()).isTrue();
        assertThat(of("{}")).isSameAs(VsCodeExcludes.NONE);
        assertThat(of("{\"editor.tabSize\": 2}")).isSameAs(VsCodeExcludes.NONE);
    }

    @Test
    @DisplayName("neither setting can be written per language: a language block's is not read")
    void languageBlocksAreNotRead() {
        VsCodeExcludes x = of("{\"[typescript]\": {\"files.exclude\": {\"**/dist\": true}, \"search.exclude\": {\"**/x\": true}}}");
        assertThat(x).isSameAs(VsCodeExcludes.NONE);
    }

    @Test
    @DisplayName("a search skips what files.exclude hides and what search.exclude names")
    void searchSkipsBoth() {
        VsCodeExcludes x = of("""
                { "files.exclude": { "**/.cache": true },
                  "search.exclude": { "**/vendor": true, "**/*.min.js": true } }
                """);
        assertThat(x.skipsSearch(".cache/a")).as("hidden files are not searched either, as in VS Code").isTrue();
        assertThat(x.skipsSearch("lib/vendor/x.js")).isTrue();
        assertThat(x.skipsSearch("src/app.min.js")).isTrue();
        assertThat(x.skipsSearch("src/app.js")).isFalse();
        assertThat(x.hides("lib/vendor")).as("search.exclude hides nothing from the trees").isFalse();
        assertThat(x.hides("src/app.min.js")).isFalse();
        assertThat(x.searchPatterns()).containsExactly("**/.cache", "**/*.min.js", "**/vendor");
    }

    @Test
    @DisplayName("search.exclude is laid over files.exclude key by key: a false there brings a hidden folder back into the search")
    void searchOverridesKeyByKey() {
        VsCodeExcludes x = of("""
                { "files.exclude": { "**/dist": true, "**/out": true },
                  "search.exclude": { "**/dist": false } }
                """);
        assertThat(x.hides("dist")).isTrue();
        assertThat(x.skipsSearch("dist/app.js")).as("hidden, and searched").isFalse();
        assertThat(x.skipsSearch("out/app.js")).isTrue();

        VsCodeExcludes conditional = of("""
                { "files.exclude": { "**/dist": true },
                  "search.exclude": { "**/dist": { "when": "x" } } }
                """);
        assertThat(conditional.hides("dist")).isTrue();
        assertThat(conditional.skipsSearch("dist")).as("the search's own value is the one in force, and it is not honoured").isFalse();
        assertThat(conditional.notHonoured()).containsExactly("search.exclude \"**/dist\" is conditional (\"when\")");
    }

    @Test
    @DisplayName("a pattern the matcher refuses is named and excludes nothing; the others still apply")
    void refusedPatterns() {
        VsCodeExcludes x = of("{\"files.exclude\": {\"{a,{b,c}}\": true, \"foo[\": true, \"**/ok\": true}}");
        assertThat(x.hides("a")).isFalse();
        assertThat(x.hides("ok")).isTrue();
        assertThat(x.notHonoured()).containsExactly(
                "files.exclude \"foo[\" is not a pattern this reads: a [ with no ]",
                "files.exclude \"{a,{b,c}}\" is not a pattern this reads: braces inside braces");
    }

    @Test
    @DisplayName("past the pattern cap the rest are named, not applied")
    void patternCap() {
        StringBuilder json = new StringBuilder("{\"files.exclude\": {");
        int n = VsCodeExcludes.MAX_PATTERNS + 3;
        for (int i = 0; i < n; i++) {
            json.append(i == 0 ? "" : ",").append(String.format("\"p%04d\": true", i));
        }
        json.append("}}");
        VsCodeExcludes x = of(json.toString());
        assertThat(x.hiddenPatterns()).hasSize(VsCodeExcludes.MAX_PATTERNS);
        assertThat(x.hides("p0000")).isTrue();
        assertThat(x.hides(String.format("p%04d", VsCodeExcludes.MAX_PATTERNS - 1))).isTrue();
        assertThat(x.hides(String.format("p%04d", VsCodeExcludes.MAX_PATTERNS))).as("the first past the cap").isFalse();
        assertThat(x.notHonoured()).hasSize(3)
                .allMatch(line -> line.endsWith("is past the first " + VsCodeExcludes.MAX_PATTERNS + " patterns"));
    }

    @Test
    @DisplayName("past what one setting's patterns may cost the rest are named, not applied")
    void stateCap() {
        // each pattern is as long as a pattern may be: a few dozen of them pass the total
        StringBuilder json = new StringBuilder("{\"files.exclude\": {");
        int n = 40;
        for (int i = 0; i < n; i++) {
            String name = String.format("%03d", i) + "a".repeat(VsCodeGlob.MAX_LENGTH - 3);
            json.append(i == 0 ? "" : ",").append('"').append(name).append("\": true");
        }
        json.append("}}");
        VsCodeExcludes x = of(json.toString());
        int kept = x.hiddenPatterns().size();
        assertThat(kept).isBetween(1, n - 1);
        assertThat(kept * (VsCodeGlob.MAX_LENGTH + 1)).isLessThanOrEqualTo(VsCodeExcludes.MAX_STATES);
        assertThat((kept + 1) * (VsCodeGlob.MAX_LENGTH + 1)).isGreaterThan(VsCodeExcludes.MAX_STATES);
        assertThat(x.notHonoured()).hasSize(n - kept)
                .allMatch(line -> line.endsWith("is past what one setting's patterns may cost"));
    }

    @Test
    @DisplayName("asked from a folder beneath the settings' own, paths are judged from the settings' folder")
    void underASubfolder() {
        VsCodeExcludes root = of("{\"files.exclude\": {\"packages/*/dist\": true, \"*.log\": true, \"**/tmp\": true}}");
        VsCodeExcludes web = root.under("packages/web");
        assertThat(web.hides("dist")).as("packages/web/dist").isTrue();
        assertThat(web.hides("dist/app.js")).isTrue();
        assertThat(web.hides("debug.log")).as("*.log is the repository root's").isFalse();
        assertThat(web.hides("src/tmp")).isTrue();
        assertThat(root.hides("dist")).isFalse();
        assertThat(root.under("")).isSameAs(root);
        assertThat(root.under("/packages/web/")).isEqualTo(web);
        assertThat(root.under("packages\\web")).isEqualTo(web);
    }

    @Test
    @DisplayName("a tree aimed AT an excluded folder shows what is in it: only folders inside the tree count")
    void theFolderAskedFromIsNeverItselfExcluded() {
        VsCodeExcludes root = of("{\"files.exclude\": {\"**/dist\": true}}");
        VsCodeExcludes insideDist = root.under("packages/web/dist");
        assertThat(insideDist.hides("app.js")).isFalse();
        assertThat(insideDist.hides("chunks/a.js")).isFalse();
        assertThat(insideDist.hides("dist")).as("a dist inside it is still one").isTrue();
    }

    @Test
    @DisplayName("case is ignored when the caller says the file system ignores it")
    void caseFollowsTheCaller() {
        String json = "{\"files.exclude\": {\"**/Dist\": true}}";
        assertThat(VsCodeExcludes.parse(json, false).hides("dist")).isFalse();
        assertThat(VsCodeExcludes.parse(json, true).hides("dist")).isTrue();
    }

    @Test
    @DisplayName("equal when the same patterns apply from the same place - what a tree compares before it redraws")
    void equality() {
        String json = "{\"files.exclude\": {\"**/dist\": true}, \"search.exclude\": {\"**/x\": true}}";
        assertThat(of(json)).isEqualTo(of(json + " ")).hasSameHashCodeAs(of(json));
        assertThat(of(json)).isNotEqualTo(of("{\"files.exclude\": {\"**/dist\": true}}"));
        assertThat(of(json)).isNotEqualTo(of(json).under("a"));
        assertThat(of(json)).isNotEqualTo(VsCodeExcludes.parse(json, true));
        assertThat(of(json)).isNotEqualTo(VsCodeExcludes.NONE);
        assertThat(of("{\"files.exclude\": {\"a\": true, \"b\": true}}"))
                .as("the order a file writes its keys in is not a difference")
                .isEqualTo(of("{\"files.exclude\": {\"b\": true, \"a\": true}}"));
    }
}
