package org.nmox.studio.ui.actions;

import java.io.File;
import java.util.Map;
import java.util.Set;

import org.netbeans.api.sendopts.CommandException;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;

/**
 * {@code nmoxstudio --aim <folder>}: aim the IDE at a folder from a
 * terminal, exactly as File ▸ Open Folder… does - the door the {@code nmox}
 * command walks through.
 *
 * <p>Why not {@code --open}: the platform hands a directory to its openfile
 * module, whose handler (friend-only, not implementable here) opens a folder
 * WITHOUT a manifest as a raw explorer tab in the left dock - measured on a
 * running 3.0.2: one more duplicate tab per call, and the IDE not aimed.
 * {@code --aim} aims any folder, manifest or not, and a folder with a
 * manifest still opens as a platform project through the aim's own bridge.
 *
 * <p>A relative path resolves against the CALLER's directory
 * ({@link Env#getCurrentDirectory()}), which is the running instance's
 * view of the terminal the command was typed in when it is forwarded, so
 * {@code cd app && nmoxstudio --aim .} aims {@code app}. Anything that is
 * not an existing directory is refused on the caller's terminal with a
 * non-zero exit - the refusal speaks where the command was typed.
 */
@ServiceProvider(service = OptionProcessor.class)
@Messages({
    "AimOption_description=aim the IDE at a folder, as File ▸ Open Folder… does",
    "AimOption_notADirectory=--aim needs a folder, and {0} is not one."
})
public final class AimOption extends OptionProcessor {

    /** The exit code a refused {@code --aim} returns to the caller's shell. */
    static final int EXIT_NOT_A_DIRECTORY = 2;

    private static final Option AIM = Option.shortDescription(
            Option.requiredArgument(Option.NO_SHORT_NAME, "aim"),
            "org.nmox.studio.ui.actions.Bundle", "AimOption_description");

    /** The seam tests replace: what an accepted folder is handed to. */
    static java.util.function.Consumer<File> aimer =
            dir -> WindowManager.getDefault().invokeWhenUIReady(() -> OpenFolderAction.openFolder(dir));

    @Override
    protected Set<Option> getOptions() {
        return Set.of(AIM);
    }

    @Override
    protected void process(Env env, Map<Option, String[]> values) throws CommandException {
        String[] args = values.get(AIM);
        if (args == null || args.length == 0) {
            return;
        }
        File dir = resolve(env.getCurrentDirectory(), args[0]);
        if (dir == null) {
            throw new CommandException(EXIT_NOT_A_DIRECTORY,
                    Bundle.AimOption_notADirectory(args[0]));
        }
        aimer.accept(dir);
    }

    /**
     * The folder {@code arg} names, resolved against {@code cwd} when
     * relative and normalized ({@code .}/{@code ..} folded), or null when it
     * is not an existing directory. Pure, so every rule is a unit test.
     */
    static File resolve(File cwd, String arg) {
        if (arg == null || arg.isBlank()) {
            return null;
        }
        File f = new File(arg);
        if (!f.isAbsolute()) {
            if (cwd == null) {
                return null;
            }
            f = new File(cwd, arg);
        }
        File normal = f.toPath().toAbsolutePath().normalize().toFile();
        return normal.isDirectory() ? normal : null;
    }
}
