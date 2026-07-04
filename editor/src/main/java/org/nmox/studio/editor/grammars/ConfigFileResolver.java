package org.nmox.studio.editor.grammars;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.util.lookup.ServiceProvider;

/**
 * MIME resolution for the config files a web developer lives in that
 * carry no usable extension: dotfiles like {@code .editorconfig} and
 * {@code .env}, and bare names like {@code Makefile} and
 * {@code nginx.conf}. NetBeans treats a leading-dot filename as all
 * name and no extension, so the grammar classes' extension rules never
 * fire for these - the file would open as plain text, losing both
 * highlighting and (worse) gaining prose spellcheck on its keys and
 * values. Matching the full name the way the platform's own
 * DockerfileResolver does is the reliable path.
 *
 * Real extensions (.ini, .vue, .graphql, .proto, ...) stay on each
 * grammar's {@code @MIMEResolver.ExtensionRegistration}; this covers
 * only what extensions cannot.
 */
@ServiceProvider(service = MIMEResolver.class, position = 480)
public final class ConfigFileResolver extends MIMEResolver {

    public ConfigFileResolver() {
        super("text/x-ini", "text/x-ignore", "text/x-properties",
                "text/x-makefile", "text/x-nginx-conf", "text/x-apache-conf",
                "text/x-markdown");
    }

    @Override
    public String findMIMEType(FileObject fo) {
        String name = fo.getNameExt();
        switch (name) {
            case ".editorconfig":
            case ".npmrc":
            case ".gitconfig":
                return "text/x-ini";
            case ".gitignore":
            case ".dockerignore":
            case ".npmignore":
            case ".eslintignore":
            case ".prettierignore":
            case ".gitattributes":
                return "text/x-ignore";
            case "Makefile":
            case "makefile":
            case "GNUmakefile":
                return "text/x-makefile";
            case "nginx.conf":
                return "text/x-nginx-conf";
            case ".htaccess":
            case "httpd.conf":
            case "apache2.conf":
                return "text/x-apache-conf";
            default:
                // dotenv family: .env, .env.local, .env.production, ...
                if (name.equals(".env") || name.startsWith(".env.")) {
                    return "text/x-properties";
                }
                // .mdx rides the platform's Markdown support
                if ("mdx".equals(fo.getExt())) {
                    return "text/x-markdown";
                }
                return null;
        }
    }
}
