package org.nmox.studio.editor.lsp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 81, the structural cure: in an Angular workspace ngserver is
 * the ONLY server on {@code text/typescript}, because the platform's
 * rename refactoring collects edit sets from EVERY binding on the mime
 * — its capability predicate ({@code RenameRefactoringPlugin.
 * lambda$prepare$6}, decompiled from the shipped lsp-client jar) is
 * {@code iconst_1; ireturn}: always true, {@code renameProvider} never
 * consulted. Two servers bound means a class-property rename lands
 * twice at the declaration ({@code headingheading}, proven live twice
 * — the second time through a verifiably armed capability-stripping
 * filter, which changed nothing). The only lever the platform leaves
 * is WHICH servers are bound.
 *
 * <p>Since ledger 83 the shape is sharper: {@code TypeScriptServer} is
 * a MULTI-MIME provider (one tsserver for .ts and .js — the platform
 * files a started server under every declared mime), which makes the
 * Angular yield WHOLE-WORKSPACE by necessity — if it started for .js
 * inside an Angular workspace, the multi-mime registration would rebind
 * tsserver to text/typescript beside ngserver, the exact double-apply.
 * Angular workspaces get their .js tsserver from the single-mime
 * {@code AngularJavaScriptTsServer}; between the two providers exactly
 * one returns non-null for any (workspace, mime).
 */
class TsServerAngularSuppressionTest {

    private static String src() throws Exception {
        // CRLF-normalize: the windows lane checks out with text=auto
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"),
                StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String between(String src, String from, String to) {
        int a = src.indexOf(from);
        int b = src.indexOf(to);
        assertThat(a).as("marker exists: " + from).isPositive();
        assertThat(b).as("marker exists: " + to).isGreaterThan(a);
        return src.substring(a, b);
    }

    @Test
    @DisplayName("tsserver yields the WHOLE workspace to Angular — both mimes at once")
    void tsYieldsInAngularWorkspaces() throws Exception {
        String body = between(src(), "class TypeScriptServer",
                "private static final TypeScriptServer");
        assertThat(body)
                .as("the Angular check keys on angular.json above the project")
                .contains("angularRootAbove(projectDir(lookup))");
        assertThat(body)
                .as("an Angular workspace gets NO shared tsserver — a multi-mime "
                        + "start from .js would rebind text/typescript beside ngserver")
                .containsPattern("if \\(angular\\) \\{\\s*\\n\\s*return null;");
        assertThat(body)
                .as("a Deno workspace gets NO tsserver either — deno lsp owns both mimes")
                .containsPattern("if \\(denoRootAbove\\(projectDir\\(lookup\\)\\) != null\\) \\{\\s*\\n\\s*return null;");
    }

    @Test
    @DisplayName("Angular JavaScript keeps its own tsserver, on the js mime alone")
    void angularJavascriptServed() throws Exception {
        String src = src();
        int js = src.indexOf("class AngularJavaScriptTsServer");
        assertThat(js).isPositive();
        String before = src.substring(0, js);
        int reg = before.lastIndexOf("@MimeRegistration(mimeType = \"text/javascript\"");
        assertThat(reg)
                .as("text/javascript has its own registration on the Angular-only class")
                .isPositive();
        assertThat(before.substring(reg))
                .as("single-mime ON PURPOSE — the mimes have different authorities here")
                .doesNotContain("text/typescript");
        String body = src.substring(js, src.indexOf("\n    }", src.indexOf("startServer", js)));
        assertThat(body)
                .as("this provider exists FOR Angular workspaces — plain ones use "
                        + "the shared TypeScriptServer")
                .containsPattern("if \\(angularRootAbove\\(projectDir\\(lookup\\)\\) == null\\) \\{\\s*\\n\\s*return null;");
        assertThat(body)
                .as("a Deno workspace gets NO tsserver on .js — deno lsp owns the mime")
                .containsPattern("if \\(denoRootAbove\\(projectDir\\(lookup\\)\\) != null\\) \\{\\s*\\n\\s*return null;");
        assertThat(body)
                .contains("launchNpm(lookup, \"typescript-language-server\", \"--stdio\")");
    }

    @Test
    @DisplayName("the pair partitions by workspace kind — no (workspace, mime) served twice")
    void pairIsComplementary() throws Exception {
        String src = src();
        // TypeScriptServer runs only when NEITHER angular NOR deno;
        // AngularJavaScriptTsServer only when angular AND NOT deno. The
        // two launchNpm calls are therefore mutually exclusive — assert
        // both bodies carry their full guard sets (the parity above),
        // and that no THIRD provider launches typescript-language-server.
        int count = 0;
        int at = -1;
        while ((at = src.indexOf("launchNpm(lookup, \"typescript-language-server\"", at + 1)) >= 0) {
            count++;
        }
        assertThat(count)
                .as("exactly the two partitioned providers launch tsserver")
                .isEqualTo(2);
    }
}
