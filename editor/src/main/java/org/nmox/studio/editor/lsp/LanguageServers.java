package org.nmox.studio.editor.lsp;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.api.project.Project;
import org.netbeans.modules.lsp.client.spi.LanguageServerProvider;
import org.nmox.studio.rack.engine.ToolLocator;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Real language intelligence: each provider launches the language's
 * own server over stdio and hands it to the platform's LSP client -
 * definitions, hover, semantic completion, rename and live
 * diagnostics arrive wholesale. Servers are found through ToolLocator
 * (Homebrew, npm, cargo, go installs); a missing server degrades
 * silently to the TextMate-level experience.
 */
public final class LanguageServers {

    private LanguageServers() {
    }

    /** Launches a server command in the project root; null when unavailable. */
    static LanguageServerProvider.LanguageServerDescription launch(Lookup lookup, List<String> command) {
        try {
            File dir = projectDir(lookup);
            List<String> resolved = ToolLocator.resolveCommand(command);
            // a bare unresolved name that doesn't exist anywhere: refuse quietly
            if (resolved.get(0).equals(command.get(0)) && !onPath(command.get(0))) {
                return null;
            }
            ProcessBuilder pb = new ProcessBuilder(resolved);
            if (dir != null) {
                pb.directory(dir);
            }
            pb.environment().put("PATH", ToolLocator.augmentedPath());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            return LanguageServerProvider.LanguageServerDescription.create(
                    process.getInputStream(), process.getOutputStream(), process);
        } catch (IOException ex) {
            return null;
        }
    }

    private static boolean onPath(String name) {
        for (String dir : ToolLocator.augmentedPath().split(File.pathSeparator)) {
            if (new File(dir, name).canExecute()) {
                return true;
            }
        }
        return false;
    }

    private static File projectDir(Lookup lookup) {
        Project project = lookup.lookup(Project.class);
        return project == null ? null : FileUtil.toFile(project.getProjectDirectory());
    }

    /** TypeScript + JavaScript via typescript-language-server. */
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/typescript", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/javascript", service = LanguageServerProvider.class)
    })
    public static final class TypeScriptServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            File dir = projectDir(lookup);
            // prefer the project's own install, then the global binary
            if (dir != null) {
                File local = new File(dir, "node_modules/.bin/typescript-language-server");
                if (local.canExecute()) {
                    return launch(lookup, List.of(local.getAbsolutePath(), "--stdio"));
                }
            }
            return launch(lookup, List.of("typescript-language-server", "--stdio"));
        }
    }

    /** Python via pyright. */
    @MimeRegistration(mimeType = "text/x-python", service = LanguageServerProvider.class)
    public static final class PythonServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launch(lookup, List.of("pyright-langserver", "--stdio"));
        }
    }

    /** Go via gopls. */
    @MimeRegistration(mimeType = "text/x-go", service = LanguageServerProvider.class)
    public static final class GoServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launch(lookup, List.of("gopls"));
        }
    }

    /** Rust via rust-analyzer. */
    @MimeRegistration(mimeType = "text/x-rust", service = LanguageServerProvider.class)
    public static final class RustServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launch(lookup, List.of("rust-analyzer"));
        }
    }

    /** Elixir via elixir-ls (when installed as language_server.sh on PATH). */
    @MimeRegistration(mimeType = "text/x-elixir", service = LanguageServerProvider.class)
    public static final class ElixirServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launch(lookup, List.of("elixir-ls"));
        }
    }

}
