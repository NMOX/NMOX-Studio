package org.nmox.studio.editor.grammars;

import org.openide.filesystems.MIMEResolver;
import org.openide.util.NbBundle;

/**
 * {@code *.component.html} → {@code text/x-ng-template} (v1.217.0).
 *
 * <p><b>Why a mime at all.</b> The first cut of Angular template
 * awareness registered the Angular grammars as TextMate INJECTIONS into
 * {@code text.html.basic} and left the files as text/html — the design
 * VS Code uses, and a headless tm4e probe proved the tokenization
 * correct. It never reached the screen: the ide cluster ships its OWN
 * HTML lexer ({@code org-netbeans-modules-html-lexer}) which owns
 * text/html, so the TextMate pipeline — injections and all — never runs
 * for HTML files. A dedicated mime is the only way onto the TextMate
 * lexer, and {@code .component.html} is the Angular convention worth
 * claiming.
 *
 * <p><b>Why declarative.</b> Two live rounds proved a programmatic
 * {@code @ServiceProvider} resolver loses to the platform's DECLARATIVE
 * html claim (org-netbeans-modules-html, ext=html at position 300)
 * regardless of the ServiceProvider position — the layer-registered
 * declarative resolvers are consulted first. So this registration is
 * declarative too, at position 250, in the same ordered folder. The
 * XML matches ext=html AND basename containing ".component" — the DTD
 * has containment, not suffix, which in practice is the same set.
 */
@MIMEResolver.Registration(displayName = "#NgTemplateResolver",
        resource = "ng-template-resolver.xml", position = 250)
@NbBundle.Messages("NgTemplateResolver=Angular Template Files")
public final class NgTemplateResolver {

    private NgTemplateResolver() {
    }
}
