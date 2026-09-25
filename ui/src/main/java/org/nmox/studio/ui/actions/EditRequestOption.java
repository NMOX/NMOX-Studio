package org.nmox.studio.ui.actions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.netbeans.api.sendopts.CommandException;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.nmox.studio.core.util.AtomicFiles;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;

/**
 * {@code --nmox-request <folder>}: the IDE's half of {@code nmox --wait} and
 * {@code nmox --diff} (3.2.0), the doors git's editor and difftool walk
 * through ({@code git config core.editor "nmox --wait"}).
 *
 * <p>The terminal command writes an {@link EditRequest} into a private
 * folder it made with {@code mktemp -d} and hands the folder over, running
 * or starting the IDE the way every {@code nmox} does. The IDE answers
 * in the same folder, and the command reads the answer there, because the
 * IDE's own output goes nowhere a terminal can see:
 * <ul>
 * <li>{@code accepted} - the request was read, holding this JVM's process
 * id, so a waiting command can tell the IDE quitting from the IDE
 * thinking;</li>
 * <li>{@code refused} - one sentence the command prints before exiting 2;</li>
 * <li>{@code done} - every file and comparison the request opened has been
 * closed, and git may read the file.</li>
 * </ul>
 * Each is written whole or not at all (a temp sibling moved into place), so
 * the command never reads half a process id.
 *
 * <p>Why a folder and not the platform's own handshake: the handshake does
 * keep a caller waiting while an option works (the server writes a
 * keep-alive byte every second, read from the RELEASE310 {@code CLIHandler}),
 * but only when the IDE is already running - when {@code nmox} starts it,
 * the launcher IS the IDE and never returns. The folder works the same
 * whichever of the two happened, and on all three OSes.
 *
 * <p>The folder must be absolute, exist, and carry the name
 * {@code mktemp} gives it ({@code nmox-request.XXXXXX}): the option is
 * reachable by anything that can talk to the CLI port (the same user, with
 * the key in the userdir), and the IDE writes only its three answer files,
 * only into a folder shaped like one nmox made.
 */
@ServiceProvider(service = OptionProcessor.class)
public final class EditRequestOption extends OptionProcessor {

    private static final Logger LOG = Logger.getLogger(EditRequestOption.class.getName());

    /** The exit code a refused request returns, the one {@code nmox} uses for its own refusals. */
    static final int EXIT_REFUSED = 2;

    /** A request file past this is not one nmox wrote (64 items of long paths fit many times over). */
    static final int MAX_BYTES = 64 * 1024;

    /** The prefix {@code mktemp -d} gives the folder in all three launchers. */
    static final String FOLDER_PREFIX = "nmox-request.";

    // no description: the option is the launchers' private door, not one to type
    private static final Option REQUEST = Option.requiredArgument(Option.NO_SHORT_NAME, "nmox-request");

    /** The seam tests replace: what an accepted request is handed to. */
    static BiConsumer<File, EditRequest> shower = (folder, request) ->
            WindowManager.getDefault().invokeWhenUIReady(() -> EditRequestWatcher.show(folder, request));

    @Override
    protected Set<Option> getOptions() {
        return Set.of(REQUEST);
    }

    @Override
    protected void process(Env env, Map<Option, String[]> values) throws CommandException {
        String[] args = values.get(REQUEST);
        if (args == null || args.length == 0) {
            return;
        }
        File folder = new File(args[0]);
        if (!folder.isAbsolute() || !folder.isDirectory()
                || !folder.getName().startsWith(FOLDER_PREFIX)) {
            // not a folder nmox made: nothing is written into it
            throw new CommandException(EXIT_REFUSED, "--nmox-request: " + args[0] + " is not a request folder");
        }
        EditRequest request;
        try {
            request = EditRequest.parse(read(new File(folder, "request")));
        } catch (EditRequest.Refused r) {
            answer(folder, "refused", r.getMessage());
            throw new CommandException(EXIT_REFUSED, r.getMessage());
        }
        answer(folder, "accepted", Long.toString(ProcessHandle.current().pid()));
        shower.accept(folder, request);
    }

    /** The request file's text, refused when it is missing or too large to be one. */
    static String read(File request) throws EditRequest.Refused {
        try {
            if (!request.isFile()) {
                throw new EditRequest.Refused("the request folder holds no request");
            }
            if (request.length() > MAX_BYTES) {
                throw new EditRequest.Refused("the request is larger than nmox ever writes");
            }
            return Files.readString(request.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new EditRequest.Refused("the request could not be read: " + ex.getMessage());
        }
    }

    /**
     * Writes one answer file whole. A command that stopped waiting (Ctrl-C)
     * removes the folder, and then there is nobody to answer: logged, not
     * thrown.
     */
    static void answer(File folder, String name, String content) {
        try {
            AtomicFiles.writeString(new File(folder, name).toPath(), content + "\n");
        } catch (IOException ex) {
            LOG.log(Level.FINE, "nobody is waiting on " + folder + " any more", ex);
        }
    }
}
