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
}
