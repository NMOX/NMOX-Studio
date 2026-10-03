package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.ServerCatalog;
import org.openide.util.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the editor tells another module about its language servers: only
 * servers it really starts, each with the catalog's own name, and
 * "installed" only when the binary is somewhere a launch would find it.
 */
class CataloguedServersTest {

    private final CataloguedServers servers = new CataloguedServers();

    @AfterEach
    void restore() {
        CataloguedServers.resetOnPath();
    }

    @Test
    @DisplayName("the facade resolves through Lookup to this provider")
    void publishedThroughLookup() {
        assertThat(Lookup.getDefault().lookup(ServerCatalog.class)).isInstanceOf(CataloguedServers.class);
    }

    @Test
    @DisplayName("a catalogued server answers with the catalog's language and install hint")
    void cataloguedServer() {
        CataloguedServers.onPath = b -> true;
        ServerCatalog.Server go = servers.server("gopls", null);
        assertThat(go.language()).isEqualTo("Go");
        assertThat(go.install()).contains("gopls");
        assertThat(go.installed()).isTrue();
    }

    @Test
    @DisplayName("a server the editor starts but the install catalog does not list is known, without a name or a hint")
    void launchedButUncatalogued() {
        CataloguedServers.onPath = b -> false;
        ServerCatalog.Server yaml = servers.server("yaml-language-server", null);
        assertThat(yaml).as("launched by YamlServer, held by the trust ledger").isNotNull();
        assertThat(yaml.language()).isNull();
        assertThat(yaml.install()).isNull();
        assertThat(yaml.installed()).isFalse();
    }

    @Test
    @DisplayName("a binary the editor never starts is answered null, never as a server")
    void unknownBinaryIsNull() {
        CataloguedServers.onPath = b -> true;
        assertThat(servers.server("tailwindcss-language-server", null)).isNull();
        assertThat(servers.server("", null)).isNull();
        assertThat(servers.server(null, null)).isNull();
    }

    @Test
    @DisplayName("a server installed into the project counts as installed; the PATH alone would say missing")
    void projectLocalCounts(@TempDir Path project) throws Exception {
        CataloguedServers.onPath = b -> false;
        File dir = project.toFile();
        assertThat(servers.server("ngserver", dir).installed()).isFalse();
        Files.createDirectories(project.resolve("node_modules/.bin"));
        Files.writeString(project.resolve("node_modules/.bin/ngserver"), "#!/bin/sh\n");
        assertThat(servers.server("ngserver", dir).installed()).isTrue();
        assertThat(servers.server("ngserver", null).installed()).as("no project, no project copy").isFalse();
    }

    @Test
    @DisplayName("npm's Windows shim (.cmd) in the project counts too")
    void windowsShimCounts(@TempDir Path project) throws Exception {
        CataloguedServers.onPath = b -> false;
        Files.createDirectories(project.resolve("node_modules/.bin"));
        Files.writeString(project.resolve("node_modules/.bin/vue-language-server.cmd"), "@echo off\r\n");
        assertThat(servers.server("vue-language-server", project.toFile()).installed()).isTrue();
    }

    @Test
    @DisplayName("a rust-analyzer on the PATH is installed only when it answers: rustup's proxy alone is not a server")
    void rustupProxyIsNotAServer() {
        java.util.List<String> probed = new java.util.ArrayList<>();
        CataloguedServers.onPath = b -> true;
        CataloguedServers.answers = b -> {
            probed.add(b);
            return !CataloguedServers.RUST_ANALYZER.equals(b);
        };
        ServerCatalog.Server rust = servers.server("rust-analyzer", null);
        assertThat(rust).isNotNull();
        assertThat(rust.installed()).as("the proxy exits non-zero without the component").isFalse();
        assertThat(rust.install()).as("the catalog's way to add it still shows").isNotBlank();
        CataloguedServers.answers = b -> true;
        assertThat(servers.server("rust-analyzer", null).installed()).isTrue();
        // a binary that is not on the PATH is not probed at all
        CataloguedServers.onPath = b -> false;
        probed.clear();
        CataloguedServers.answers = b -> {
            probed.add(b);
            return true;
        };
        assertThat(servers.server("rust-analyzer", null).installed()).isFalse();
        assertThat(probed).isEmpty();
    }

    @Test
    @DisplayName("only rust-analyzer is run to be believed; every other binary is what its name says")
    void onlyRustAnalyzerIsProbed() {
        assertThat(CataloguedServers.answers("gopls")).isTrue();
        assertThat(CataloguedServers.answers("pyright-langserver")).isTrue();
    }
}
