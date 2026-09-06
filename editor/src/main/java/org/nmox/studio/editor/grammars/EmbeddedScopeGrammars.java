package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;

/**
 * Grammars registered for their <em>scope names</em>, not their file
 * types. TM4E resolves cross-grammar includes through a global
 * scopeName registry built from every registered grammar; a scope
 * nothing registers makes the including rule unusable, and TM4E prunes
 * it ("CANNOT find grammar for scopeName", then "REMOVING ... DUE TO
 * EMPTY PATTERNS"). The platform's markdown grammar lost every
 * language-tagged fence and its YAML front-matter rule that way — 191
 * rules pruned per session.
 *
 * JS, TS and YAML editors are NOT TextMate-driven here (the custom
 * JS/TS lexer and the platform's YAML module own those mimes), so each
 * grammar is bound to a synthetic {@code text/x-nmox-embed-*} mime no
 * file ever resolves to: the registration exists purely to put the
 * scope in the registry for embedding. Do not add editor bindings
 * (CSL, loaders) to these mimes.
 */
public final class EmbeddedScopeGrammars {

    private EmbeddedScopeGrammars() {
    }

    /** source.yaml — markdown YAML front matter (```yaml fences too). */
    @GrammarRegistration(grammar = "yaml.tmLanguage.json", mimeType = "text/x-nmox-embed-yaml")
    public static final class Yaml {

        private Yaml() {
        }
    }

    /** source.js — ```js fences; the most-included scope we didn't have. */
    @GrammarRegistration(grammar = "javascript.tmLanguage.json", mimeType = "text/x-nmox-embed-js")
    public static final class JavaScript {

        private JavaScript() {
        }
    }

    /** source.ts — ```ts fences. */
    @GrammarRegistration(grammar = "typescript.tmLanguage.json", mimeType = "text/x-nmox-embed-ts")
    public static final class TypeScript {

        private TypeScript() {
        }
    }

    /** source.tsx — ```tsx fences. */
    @GrammarRegistration(grammar = "typescriptreact.tmLanguage.json", mimeType = "text/x-nmox-embed-tsx")
    public static final class TypeScriptReact {

        private TypeScriptReact() {
        }
    }

    /** text.html.derivative — markdown's inline-HTML include. */
    @GrammarRegistration(grammar = "html-derivative.tmLanguage.json", mimeType = "text/x-nmox-embed-html-derivative")
    public static final class HtmlDerivative {

        private HtmlDerivative() {
        }
    }

    /**
     * text.xml — included by the http/nim/ruby/php/perl/cobol grammars
     * for embedded XML (heredocs, request bodies). The 1.195.0 smoke
     * test saw "No grammar source for scope [text.xml]" on opening a
     * .http file. The platform's own XML editor keeps text/xml; this
     * registration exists only to resolve the scope.
     */
    @GrammarRegistration(grammar = "xml.tmLanguage.json", mimeType = "text/x-nmox-embed-xml")
    public static final class Xml {

        private Xml() {
        }
    }

    /**
     * source.js.jsx — included by the vue and graphql grammars (and
     * markdown ```jsx fences). .jsx FILES stay on the custom JS lexer;
     * this registration exists only to resolve the scope.
     */
    @GrammarRegistration(grammar = "javascriptreact.tmLanguage.json", mimeType = "text/x-nmox-embed-jsx")
    public static final class JavaScriptReact {

        private JavaScriptReact() {
        }
    }

    // ---- scope STUBS (v2.85.0) ------------------------------------------
    // Vendored grammars include scopes this product ships no grammar for
    // (C/C++ inline asm → source.x86_64/x86/asm/arm; php/julia/… → source.sql;
    // scss → source.sassdoc; cpp/nim → source.glsl; pug/astro → source.stylus;
    // the platform's markdown fences → source.dockerfile/batchfile/diff).
    // TM4E warns "No grammar source for scope" for each — 235 WARNING lines
    // per boot in the v2.85.0 Docker walk — and prunes the including rule.
    // A stub is an empty-patterns grammar registered for the scope: the
    // include resolves, the rule survives, the region reads as plain text,
    // the log is quiet. Stubs are not vendored grammars and are NOT counted
    // as such (their files carry no .tmLanguage suffix on purpose).

    /** source.x86_64 — stub. */
    @GrammarRegistration(grammar = "stub-source.x86_64.json", mimeType = "text/x-nmox-embed-stub-x86_64")
    public static final class StubX8664 {

        private StubX8664() {
        }
    }

    /** source.x86 — stub. */
    @GrammarRegistration(grammar = "stub-source.x86.json", mimeType = "text/x-nmox-embed-stub-x86")
    public static final class StubX86 {

        private StubX86() {
        }
    }

    /** source.asm — stub. */
    @GrammarRegistration(grammar = "stub-source.asm.json", mimeType = "text/x-nmox-embed-stub-asm")
    public static final class StubAsm {

        private StubAsm() {
        }
    }

    /** source.arm — stub. */
    @GrammarRegistration(grammar = "stub-source.arm.json", mimeType = "text/x-nmox-embed-stub-arm")
    public static final class StubArm {

        private StubArm() {
        }
    }

    /** source.sql — stub. */
    @GrammarRegistration(grammar = "stub-source.sql.json", mimeType = "text/x-nmox-embed-stub-sql")
    public static final class StubSql {

        private StubSql() {
        }
    }

    /** source.sassdoc — stub. */
    @GrammarRegistration(grammar = "stub-source.sassdoc.json", mimeType = "text/x-nmox-embed-stub-sassdoc")
    public static final class StubSassdoc {

        private StubSassdoc() {
        }
    }

    /** source.glsl — stub. */
    @GrammarRegistration(grammar = "stub-source.glsl.json", mimeType = "text/x-nmox-embed-stub-glsl")
    public static final class StubGlsl {

        private StubGlsl() {
        }
    }

    /** source.stylus — stub. */
    @GrammarRegistration(grammar = "stub-source.stylus.json", mimeType = "text/x-nmox-embed-stub-stylus")
    public static final class StubStylus {

        private StubStylus() {
        }
    }

    /** source.dockerfile — stub. */
    @GrammarRegistration(grammar = "stub-source.dockerfile.json", mimeType = "text/x-nmox-embed-stub-dockerfile")
    public static final class StubDockerfile {

        private StubDockerfile() {
        }
    }

    /** source.batchfile — stub. */
    @GrammarRegistration(grammar = "stub-source.batchfile.json", mimeType = "text/x-nmox-embed-stub-batchfile")
    public static final class StubBatchfile {

        private StubBatchfile() {
        }
    }

    /** source.diff — stub. */
    @GrammarRegistration(grammar = "stub-source.diff.json", mimeType = "text/x-nmox-embed-stub-diff")
    public static final class StubDiff {

        private StubDiff() {
        }
    }

    // ---- the second stub batch (v2.85.0): the boot proof of the first
    // eleven left 132 warnings over thirty-one more scopes — markdown fences
    // (go, less, toml, json5, powershell, latex, bibtex, git-commit…),
    // grammar-internal aliases (source.c++, source.cpp.embedded.macro,
    // source.js.regexp — unresolvable upstream-wide, see the NOTICE) and
    // sub-grammars we never vendored (elixir, postscript, twig, objc…).
    // Every one an empty grammar for its scope: the region reads as plain
    // text, exactly as it did while pruned, and the log says nothing.

    /** source.js.regexp — stub. */
    @GrammarRegistration(grammar = "stub-source.js.regexp.json", mimeType = "text/x-nmox-embed-stub-source-js-regexp")
    public static final class StubSourceJsRegexp {

        private StubSourceJsRegexp() {
        }
    }

    /** source.js.jquery — stub. */
    @GrammarRegistration(grammar = "stub-source.js.jquery.json", mimeType = "text/x-nmox-embed-stub-source-js-jquery")
    public static final class StubSourceJsJquery {

        private StubSourceJsJquery() {
        }
    }

    /** source.c++ — stub. */
    @GrammarRegistration(grammar = "stub-source.c++.json", mimeType = "text/x-nmox-embed-stub-source-cplusplus")
    public static final class StubSourceCPlusPlus {

        private StubSourceCPlusPlus() {
        }
    }

    /** text.html.elixir — stub. */
    @GrammarRegistration(grammar = "stub-text.html.elixir.json", mimeType = "text/x-nmox-embed-stub-text-html-elixir")
    public static final class StubTextHtmlElixir {

        private StubTextHtmlElixir() {
        }
    }

    /** text.elixir — stub. */
    @GrammarRegistration(grammar = "stub-text.elixir.json", mimeType = "text/x-nmox-embed-stub-text-elixir")
    public static final class StubTextElixir {

        private StubTextElixir() {
        }
    }

    /** source.regexp.python — stub. */
    @GrammarRegistration(grammar = "stub-source.regexp.python.json", mimeType = "text/x-nmox-embed-stub-source-regexp-python")
    public static final class StubSourceRegexpPython {

        private StubSourceRegexpPython() {
        }
    }

    /** source.postscript — stub. */
    @GrammarRegistration(grammar = "stub-source.postscript.json", mimeType = "text/x-nmox-embed-stub-source-postscript")
    public static final class StubSourcePostscript {

        private StubSourcePostscript() {
        }
    }

    /** source.less — stub. */
    @GrammarRegistration(grammar = "stub-source.less.json", mimeType = "text/x-nmox-embed-stub-source-less")
    public static final class StubSourceLess {

        private StubSourceLess() {
        }
    }

    /** source.cpp.embedded.macro — stub. */
    @GrammarRegistration(grammar = "stub-source.cpp.embedded.macro.json", mimeType = "text/x-nmox-embed-stub-source-cpp-embedded-macro")
    public static final class StubSourceCppEmbeddedMacro {

        private StubSourceCppEmbeddedMacro() {
        }
    }

    /** text.xml.xsl — stub. */
    @GrammarRegistration(grammar = "stub-text.xml.xsl.json", mimeType = "text/x-nmox-embed-stub-text-xml-xsl")
    public static final class StubTextXmlXsl {

        private StubTextXmlXsl() {
        }
    }

    /** text.tex.latex — stub. */
    @GrammarRegistration(grammar = "stub-text.tex.latex.json", mimeType = "text/x-nmox-embed-stub-text-tex-latex")
    public static final class StubTextTexLatex {

        private StubTextTexLatex() {
        }
    }

    /** text.log — stub. */
    @GrammarRegistration(grammar = "stub-text.log.json", mimeType = "text/x-nmox-embed-stub-text-log")
    public static final class StubTextLog {

        private StubTextLog() {
        }
    }

    /** text.git-rebase — stub. */
    @GrammarRegistration(grammar = "stub-text.git-rebase.json", mimeType = "text/x-nmox-embed-stub-text-git-rebase")
    public static final class StubTextGitRebase {

        private StubTextGitRebase() {
        }
    }

    /** text.git-commit — stub. */
    @GrammarRegistration(grammar = "stub-text.git-commit.json", mimeType = "text/x-nmox-embed-stub-text-git-commit")
    public static final class StubTextGitCommit {

        private StubTextGitCommit() {
        }
    }

    /** text.bibtex — stub. */
    @GrammarRegistration(grammar = "stub-text.bibtex.json", mimeType = "text/x-nmox-embed-stub-text-bibtex")
    public static final class StubTextBibtex {

        private StubTextBibtex() {
        }
    }

    /** source.twig — stub. */
    @GrammarRegistration(grammar = "stub-source.twig.json", mimeType = "text/x-nmox-embed-stub-source-twig")
    public static final class StubSourceTwig {

        private StubSourceTwig() {
        }
    }

    /** source.powershell — stub. */
    @GrammarRegistration(grammar = "stub-source.powershell.json", mimeType = "text/x-nmox-embed-stub-source-powershell")
    public static final class StubSourcePowershell {

        private StubSourcePowershell() {
        }
    }

    /** source.perl.6 — stub. */
    @GrammarRegistration(grammar = "stub-source.perl.6.json", mimeType = "text/x-nmox-embed-stub-source-perl-6")
    public static final class StubSourcePerl6 {

        private StubSourcePerl6() {
        }
    }

    /** source.objc — stub. */
    @GrammarRegistration(grammar = "stub-source.objc.json", mimeType = "text/x-nmox-embed-stub-source-objc")
    public static final class StubSourceObjc {

        private StubSourceObjc() {
        }
    }

    /** source.json.comments — stub. */
    @GrammarRegistration(grammar = "stub-source.json.comments.json", mimeType = "text/x-nmox-embed-stub-source-json-comments")
    public static final class StubSourceJsonComments {

        private StubSourceJsonComments() {
        }
    }

    /** source.go — stub. */
    @GrammarRegistration(grammar = "stub-source.go.json", mimeType = "text/x-nmox-embed-stub-source-go")
    public static final class StubSourceGo {

        private StubSourceGo() {
        }
    }

    /** source.asp.vb.net — stub. */
    @GrammarRegistration(grammar = "stub-source.asp.vb.net.json", mimeType = "text/x-nmox-embed-stub-source-asp-vb-net")
    public static final class StubSourceAspVbNet {

        private StubSourceAspVbNet() {
        }
    }

    /** source.css.postcss — stub. */
    @GrammarRegistration(grammar = "stub-source.css.postcss.json", mimeType = "text/x-nmox-embed-stub-source-css-postcss")
    public static final class StubSourceCssPostcss {

        private StubSourceCssPostcss() {
        }
    }

    /** text.html.javadoc — stub. */
    @GrammarRegistration(grammar = "stub-text.html.javadoc.json", mimeType = "text/x-nmox-embed-stub-text-html-javadoc")
    public static final class StubTextHtmlJavadoc {

        private StubTextHtmlJavadoc() {
        }
    }

    /** source.toml — stub. */
    @GrammarRegistration(grammar = "stub-source.toml.json", mimeType = "text/x-nmox-embed-stub-source-toml")
    public static final class StubSourceToml {

        private StubSourceToml() {
        }
    }

    /** source.postcss — stub. */
    @GrammarRegistration(grammar = "stub-source.postcss.json", mimeType = "text/x-nmox-embed-stub-source-postcss")
    public static final class StubSourcePostcss {

        private StubSourcePostcss() {
        }
    }

    /** source.openesql — stub. */
    @GrammarRegistration(grammar = "stub-source.openesql.json", mimeType = "text/x-nmox-embed-stub-source-openesql")
    public static final class StubSourceOpenesql {

        private StubSourceOpenesql() {
        }
    }

    /** source.ocaml.ocamldoc — stub. */
    @GrammarRegistration(grammar = "stub-source.ocaml.ocamldoc.json", mimeType = "text/x-nmox-embed-stub-source-ocaml-ocamldoc")
    public static final class StubSourceOcamlOcamldoc {

        private StubSourceOcamlOcamldoc() {
        }
    }

    /** source.ocaml.interface — stub. */
    @GrammarRegistration(grammar = "stub-source.ocaml.interface.json", mimeType = "text/x-nmox-embed-stub-source-ocaml-interface")
    public static final class StubSourceOcamlInterface {

        private StubSourceOcamlInterface() {
        }
    }

    /** source.json5 — stub. */
    @GrammarRegistration(grammar = "stub-source.json5.json", mimeType = "text/x-nmox-embed-stub-source-json5")
    public static final class StubSourceJson5 {

        private StubSourceJson5() {
        }
    }

    /** regexp — stub. */
    @GrammarRegistration(grammar = "stub-regexp.json", mimeType = "text/x-nmox-embed-stub-regexp")
    public static final class StubRegexp {

        private StubRegexp() {
        }
    }
}
