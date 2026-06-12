package org.nmox.studio.rack.docker;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DockerDevice;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/** The Dockerize generator: deterministic, production-shaped output. */
class DockerizeGeneratorTest {

    @Test
    @DisplayName("Static Node projects get a multi-stage build into nginx")
    void nodeStatic() {
        Map<String, String> files = DockerizeGenerator.generate(ProjectKind.NODE, "shop", true);
        assertThat(files).containsKeys("Dockerfile", ".dockerignore", "compose.yaml");
        assertThat(files.get("Dockerfile"))
                .contains("AS build").contains("npm ci").contains("nginx").contains("EXPOSE 80");
        assertThat(files.get(".dockerignore")).contains("node_modules");
        assertThat(files.get("compose.yaml")).contains("shop:").contains("\"80:80\"");
    }

    @Test
    @DisplayName("Node servers run as the non-root node user with prod deps only")
    void nodeServer() {
        String df = DockerizeGenerator.generate(ProjectKind.NODE, "api", false).get("Dockerfile");
        assertThat(df).contains("USER node").contains("--omit=dev").contains("EXPOSE 3000");
    }

    @Test
    @DisplayName("Go gets a distroless static binary")
    void goDistroless() {
        String df = DockerizeGenerator.generate(ProjectKind.GO, "svc", false).get("Dockerfile");
        assertThat(df).contains("CGO_ENABLED=0").contains("distroless");
        assertThat(df).contains("ENTRYPOINT [\"/svc\"]");
    }

    @Test
    @DisplayName("Rust and Python templates carry their toolchains")
    void rustAndPython() {
        assertThat(DockerizeGenerator.generate(ProjectKind.RUST, "rs", false).get("Dockerfile"))
                .contains("cargo build --release");
        assertThat(DockerizeGenerator.generate(ProjectKind.PYTHON, "py", false).get("Dockerfile"))
                .contains("pip install").contains("uvicorn");
    }

    @Test
    @DisplayName("Unknown toolchains say so instead of guessing")
    void genericIsHonest() {
        assertThat(DockerizeGenerator.generate(ProjectKind.NONE, "x", false).get("Dockerfile"))
                .contains("No toolchain detected");
    }

    @Test
    @DisplayName("Default ports follow the runtime")
    void defaultPorts() {
        assertThat(DockerizeGenerator.defaultPort(ProjectKind.NODE, true)).isEqualTo(80);
        assertThat(DockerizeGenerator.defaultPort(ProjectKind.NODE, false)).isEqualTo(3000);
        assertThat(DockerizeGenerator.defaultPort(ProjectKind.PYTHON, false)).isEqualTo(8000);
    }

    @Test
    @DisplayName("HARBOR's reclaimable total sums mixed units readably")
    void reclaimableTotal() {
        var rows = java.util.List.of(
                new DockerClient.DfRow("Images", "12", "4", "6.5GB", "4.2GB (64%)"),
                new DockerClient.DfRow("Build Cache", "88", "0", "2.1GB", "512MB"),
                new DockerClient.DfRow("Containers", "3", "1", "10MB", "0B"));
        assertThat(DockerDevice.totalReclaimable(rows)).isEqualTo("4.7GB");
    }
}
