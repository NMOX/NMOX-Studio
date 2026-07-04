package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Apache config TextMate grammar (see NOTICE-grammars.md
 * for the pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 *
 * <p>Only {@code .vhost} is claimed by extension; the generic
 * {@code .conf} extension stays unclaimed (nginx, systemd and half of
 * /etc use it too). The extension-less names Apache is actually known
 * by ({@code .htaccess}, {@code httpd.conf}, {@code apache2.conf}) are
 * matched by full filename in {@link ConfigFileResolver}.
 */
@GrammarRegistration(grammar = "apache.tmLanguage.json", mimeType = "text/x-apache-conf")
@MIMEResolver.ExtensionRegistration(displayName = "Apache Config", mimeType = "text/x-apache-conf", extension = {"vhost"}, position = 2440)
public final class ApacheGrammar {

    private ApacheGrammar() {
    }
}
