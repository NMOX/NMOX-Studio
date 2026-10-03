package org.nmox.studio.editor.lsp;

import java.io.File;
import java.util.function.Predicate;
import org.nmox.studio.core.spi.ServerCatalog;
import org.openide.util.lookup.ServiceProvider;

/**
 * The editor's answer to {@link ServerCatalog}: which language servers
 * this product starts, by binary name, and whether each is installed.
 * "Starts" is read from the two tables that already hold it — the
 * install catalog ({@link LanguageServerCatalog}) and the launch ledger
 * ({@link ServerTrust#SERVERS}, every binary a provider launches, held
 * complete by {@code ServerTrustLedgerTest}) — so a name in neither is
 * answered null: the product starts no such server, and nobody is told
 * it does.
 *
 * <p>"Installed" is the Language Servers window's own test (the binary
 * on the augmented PATH), widened by one stat: a server installed into
 * the project's {@code node_modules/.bin}, which is where the Angular
 * and Vue servers are told to go and where the npm-distributed ones are
 * preferred from. One server is asked more: a {@code rust-analyzer} on
 * the PATH is usually rustup's proxy, which is there and runs whether or
 * not the component was ever added, so it counts as installed only when
 * {@code rust-analyzer --version} exits zero (v1.351.0's law, through the
 * editor's own probe, {@link LanguageServers.RustServer#analyzerAnswers},
 * which remembers only a success). Everything here is disk or process
 * work: callers ask off the event thread.
 */
@ServiceProvider(service = ServerCatalog.class)
public final class CataloguedServers implements ServerCatalog {

    /** Whether a binary is on the PATH; a seam so a test needs no installed tool. */
    static volatile Predicate<String> onPath = LanguageServerCatalog::isInstalled;

    /**
     * Whether a binary found on the PATH is a server that answers; a seam
     * so a test runs nothing.
     */
    static volatile Predicate<String> answers = CataloguedServers::answers;

    /** Puts the production PATH test and probe back; a test that swapped either calls this. */
    static void resetOnPath() {
        onPath = LanguageServerCatalog::isInstalled;
        answers = CataloguedServers::answers;
    }

    /**
     * The production answer: a binary is what its name says, except
     * {@code rust-analyzer}, which must run its {@code --version} to zero
     * (a proxy without the component exits non-zero at once).
     */
    static boolean answers(String binary) {
        return !RUST_ANALYZER.equals(binary) || LanguageServers.RustServer.analyzerAnswers();
    }

    /** The one binary whose presence on the PATH proves nothing. */
    static final String RUST_ANALYZER = "rust-analyzer";

    @Override
    public Server server(String binary, File projectDir) {
        if (binary == null || binary.isBlank()) {
            return null;
        }
        LanguageServerCatalog.Server entry = LanguageServerCatalog.forBinary(binary);
        if (entry == null && !ServerTrust.SERVERS.containsKey(binary)) {
            return null;
        }
        boolean installed = (onPath.test(binary) && answers.test(binary)) || inProject(projectDir, binary);
        return new Server(binary, entry == null ? null : entry.language(), installed,
                entry == null ? null : entry.install());
    }

    /**
     * A project's own copy, at the path {@code LanguageServers.launchNpm}
     * prefers it from: the shim npm writes, with Windows' {@code .cmd}
     * beside it.
     */
    static boolean inProject(File projectDir, String binary) {
        if (projectDir == null) {
            return false;
        }
        return new File(projectDir, "node_modules/.bin/" + binary).isFile()
                || new File(projectDir, "node_modules/.bin/" + binary + ".cmd").isFile();
    }
}
