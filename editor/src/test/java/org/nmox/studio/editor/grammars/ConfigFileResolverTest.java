package org.nmox.studio.editor.grammars;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dotfile/bare-name MIME resolver, checked against real FileObjects
 * (an in-memory filesystem — the platform idiom for headless FS tests).
 * These names carry no usable extension, so the whole feature IS this
 * name table; a missed name silently opens as plain text with prose
 * spellcheck, which is the bug the resolver exists to prevent.
 */
class ConfigFileResolverTest {

    private static String mimeOf(String name) throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        return new ConfigFileResolver().findMIMEType(root.createData(name));
    }

    @Test
    @DisplayName("INI family: .editorconfig, .npmrc, .gitconfig")
    void iniFamily() throws Exception {
        assertThat(mimeOf(".editorconfig")).isEqualTo("text/x-ini");
        assertThat(mimeOf(".npmrc")).isEqualTo("text/x-ini");
        assertThat(mimeOf(".gitconfig")).isEqualTo("text/x-ini");
    }

    @Test
    @DisplayName("Ignore family: .gitignore and friends")
    void ignoreFamily() throws Exception {
        assertThat(mimeOf(".gitignore")).isEqualTo("text/x-ignore");
        assertThat(mimeOf(".dockerignore")).isEqualTo("text/x-ignore");
        assertThat(mimeOf(".gitattributes")).isEqualTo("text/x-ignore");
    }

    @Test
    @DisplayName("Bare names: Makefile spellings, nginx.conf, Apache configs")
    void bareNames() throws Exception {
        assertThat(mimeOf("Makefile")).isEqualTo("text/x-makefile");
        assertThat(mimeOf("makefile")).isEqualTo("text/x-makefile");
        assertThat(mimeOf("GNUmakefile")).isEqualTo("text/x-makefile");
        assertThat(mimeOf("nginx.conf")).isEqualTo("text/x-nginx-conf");
        assertThat(mimeOf(".htaccess")).isEqualTo("text/x-apache-conf");
        assertThat(mimeOf("httpd.conf")).isEqualTo("text/x-apache-conf");
        assertThat(mimeOf("apache2.conf")).isEqualTo("text/x-apache-conf");
    }

    @Test
    @DisplayName("dotenv family matches .env and .env.* but not lookalikes")
    void dotenvFamily() throws Exception {
        assertThat(mimeOf(".env")).isEqualTo("text/x-properties");
        assertThat(mimeOf(".env.local")).isEqualTo("text/x-properties");
        assertThat(mimeOf(".env.production")).isEqualTo("text/x-properties");
        assertThat(mimeOf(".environment")).isNull();
    }

    @Test
    @DisplayName(".mdx rides Markdown; everything else stays unclaimed")
    void mdxAndUnclaimed() throws Exception {
        assertThat(mimeOf("post.mdx")).isEqualTo("text/x-markdown");
        assertThat(mimeOf("notes.txt")).isNull();
        assertThat(mimeOf("random.conf")).as("generic .conf deliberately unclaimed").isNull();
    }
}
