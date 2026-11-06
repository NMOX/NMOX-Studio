package org.nmox.studio.infra.ops.remote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exactly what runs on the host, and that nothing unvalidated reaches it. */
class DeployPlanTest {

    @Test
    @DisplayName("a folder name becomes a slug the shell cannot misread")
    void slug() {
        assertThat(DeployPlan.slugOf("My Shop (2026)")).isEqualTo("my-shop-2026");
        assertThat(DeployPlan.slugOf("$(rm -rf /)")).isEqualTo("rm-rf");
        assertThat(DeployPlan.slugOf("")).isEqualTo("app");
        assertThat(DeployPlan.slugOf("a".repeat(60))).hasSize(40);
    }

    @Test
    @DisplayName("the plan's lines carry the slug, the ports, the stamp and the label — and nothing else from outside")
    void lines() {
        DeployPlan p = new DeployPlan("shop", 80, 3000, "1762400000000");
        assertThat(p.unpack()).isEqualTo("set -e; mkdir -p /srv/nmox/shop; rm -rf /srv/nmox/shop/src.next; mkdir /srv/nmox/shop/src.next; tar xzf - -C /srv/nmox/shop/src.next");
        assertThat(p.swap()).contains("mv src.next src");
        assertThat(p.build()).isEqualTo("docker build -t nmox/shop:1762400000000 -t nmox/shop:latest /srv/nmox/shop/src");
        assertThat(p.run()).contains("docker rm -f nmox-shop").contains("--label nmox.ops.app=shop")
                .contains("--restart unless-stopped -p 80:3000 nmox/shop:1762400000000");
        assertThat(p.verify()).contains("--filter 'name=^nmox-shop$'").contains("http://127.0.0.1:80/");
        assertThat(p.describe()).hasSize(5);
    }

    @Test
    @DisplayName("a slug, a port or a stamp the shell could misread is refused at construction")
    void refuses() {
        assertThatThrownBy(() -> new DeployPlan("Shop", 80, 3000, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeployPlan("shop;ls", 80, 3000, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeployPlan("shop", 0, 3000, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeployPlan("shop", 80, 3000, "1 2")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EXPOSE lines yield ports; variables and comments do not")
    void expose() {
        assertThat(Dockerfiles.exposedPorts("FROM node\nEXPOSE 3000 8080/tcp\n expose 3000 # again\nEXPOSE $PORT\n"))
                .containsExactly(3000, 8080);
        assertThat(Dockerfiles.exposedPorts(null)).isEmpty();
    }
}
