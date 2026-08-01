package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarInjectionRegistration;

/**
 * Angular template intelligence for the eyes (v1.217.0): the five
 * grammars the Angular team ships for VS Code, vendored verbatim (MIT,
 * angular/vscode-ng-language-service, sha256 in NOTICE-grammars.md) and
 * registered as INJECTIONS into the HTML grammar — the first use of the
 * platform's injection support in this codebase.
 *
 * <p><b>Why injections, not a new mime.</b> A {@code .component.html}
 * file IS an HTML file: it keeps text/html, so nothing already working
 * (typing intelligence, spellcheck scoping, the platform HTML editor
 * kit) is lost. The Angular grammars are DESIGNED as overlays — each
 * carries an {@code injectionSelector} like {@code L:text.html
 * -comment} — and TM4E composites them onto the host tokenization. Our
 * html grammar's scope is {@code text.html.basic}, which the selectors'
 * {@code text.html} prefix matches. The cost of the overlay in plain
 * non-Angular HTML is nil-to-invisible: the added scopes only light up
 * on Angular constructs ({@code {{…}}}, {@code @if}, {@code @let},
 * {@code <ng-template>}) that plain pages do not contain.
 *
 * <p>What each file carries:
 * <ul>
 *   <li>{@code ng-template} — {@code {{ interpolation }}} regions,
 *       delegating their content to the expression grammar.</li>
 *   <li>{@code ng-expression} — the Angular template expression
 *       language (pipes, safe navigation, template literals).</li>
 *   <li>{@code ng-template-blocks} — the Angular 17+ control flow:
 *       {@code @if/@else}, {@code @for/@empty}, {@code @switch/@case/
 *       @default}, {@code @defer/@placeholder/@loading/@error}.</li>
 *   <li>{@code ng-let-declaration} — {@code @let} template variables
 *       (Angular 18+).</li>
 *   <li>{@code ng-template-tag} — {@code <ng-template>} attribute
 *       forms, injected inside tags ({@code text.html#meta.tag}).</li>
 * </ul>
 */
@GrammarInjectionRegistration(grammar = "org/nmox/studio/editor/grammars/ng-template.tmLanguage.json",
        injectTo = {"text.html.basic"})
@GrammarInjectionRegistration(grammar = "org/nmox/studio/editor/grammars/ng-expression.tmLanguage.json",
        injectTo = {"text.html.basic"})
@GrammarInjectionRegistration(grammar = "org/nmox/studio/editor/grammars/ng-template-blocks.tmLanguage.json",
        injectTo = {"text.html.basic"})
@GrammarInjectionRegistration(grammar = "org/nmox/studio/editor/grammars/ng-let-declaration.tmLanguage.json",
        injectTo = {"text.html.basic"})
@GrammarInjectionRegistration(grammar = "org/nmox/studio/editor/grammars/ng-template-tag.tmLanguage.json",
        injectTo = {"text.html.basic"})
/*
 * No bridge grammar is needed for the blocks grammar's
 * `text.html.derivative` include: the real VS Code derived-HTML grammar
 * is already vendored and registered embed-only (EmbeddedScopeGrammars,
 * v1.200.x), so the scope resolves and markup inside a control-flow
 * block keeps full HTML highlighting.
 */
public class NgTemplateGrammars {
}
