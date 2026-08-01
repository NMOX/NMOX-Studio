package org.nmox.studio.editor.lsp;

import java.io.File;
import java.util.Map;
import org.nmox.studio.editor.lsp.LanguageServerCatalog.Server;
import org.nmox.studio.rack.engine.CommandExecutor;

/**
 * Installs a language server by running its ecosystem's own command
 * (npm / brew / go / cargo / gem / dotnet / coursier / opam) through the
 * rack's CommandExecutor — argv, never a shell string, so nothing is
 * interpolated; the live output streams to the Output window; and a
 * missing package manager is reported up front instead of as a cryptic
 * failure. No sudo is ever added.
 */
public final class LanguageServerInstaller {

    /** Outcome of an install attempt. */
    public enum Result {
        INSTALLED,
        FAILED,
        NEEDS_TOOLCHAIN,
        NOT_AUTO,
        /** A project-local install with no project aimed to install into. */
        NEEDS_PROJECT
    }

    /** UI hooks; all fire on the process worker thread, so marshal to the EDT. */
    public interface Listener {
        void onStarted(Server server);

        void onFinished(Server server, Result result, int exitCode);
    }

    private LanguageServerInstaller() {
    }

    /**
     * Starts the install; returns a handle to cancel it, or null when the
     * server isn't auto-installable or its package manager is absent (the
     * listener is told which).
     */
    public static CommandExecutor.Handle install(Server server, Listener listener) {
        if (!server.autoInstallable()) {
            listener.onFinished(server, Result.NOT_AUTO, -1);
            return null;
        }
        String installer = server.installer();
        if (!LanguageServerCatalog.isInstalled(installer)) {
            // e.g. "go install …" but go isn't here: don't run a doomed command
            listener.onFinished(server, Result.NEEDS_TOOLCHAIN, -1);
            return null;
        }

        // A project-local install (npm install --save-dev) must run in
        // the aimed project: run in $HOME it would create ~/package.json
        // and ~/node_modules, report INSTALLED, and leave the project
        // without the packages the server probes (v1.216.0). Global
        // installs (-g, brew, go install, cargo) don't care about cwd,
        // so $HOME stays their harmless default.
        File cwd = new File(System.getProperty("user.home", "."));
        if (server.projectLocal()) {
            org.nmox.studio.core.spi.ProjectAim aim =
                    org.nmox.studio.core.spi.ProjectAim.find();
            File project = aim == null ? null : aim.projectDir();
            if (project == null || !project.isDirectory()) {
                listener.onFinished(server, Result.NEEDS_PROJECT, -1);
                return null;
            }
            cwd = project;
        }

        listener.onStarted(server);
        String tab = "Install " + server.binary();
        CommandExecutor.Handle handle = CommandExecutor.run(
                tab, cwd, Map.of(), server.command(), line -> {
                }, exit -> {
                    // success = the command succeeded; a freshly-installed binary may
                    // not be on the resolved PATH until restart, so trust the exit code
                    Result r = exit == 0 ? Result.INSTALLED : Result.FAILED;
                    listener.onFinished(server, r, exit);
                });
        CommandExecutor.showOutput(tab);
        return handle;
    }
}
