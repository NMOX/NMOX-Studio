package org.nmox.studio.infra.ops.provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.infra.ops.remote.DeployPlan;
import org.nmox.studio.infra.ops.remote.Deployer;
import org.nmox.studio.infra.ops.remote.HostFacts;
import org.nmox.studio.infra.ops.remote.HostProbe;
import org.nmox.studio.infra.ops.remote.RemoteRunner;
import org.nmox.studio.infra.ops.remote.SshTarget;

import static org.assertj.core.api.Assertions.assertThat;

/** The simulation's timeline, driven by a clock the test owns. */
class SimulatedWorldTest {

    @Test
    @DisplayName("a simulated host is new, then active with a documentation address, then ssh answers, then cloud-init is done")
    void timeline() {
        AtomicLong now = new AtomicLong(1_000_000);
        SimulatedWorld world = new SimulatedWorld(now::get).unpaced();
        SimulatedOps ops = new SimulatedOps(world);
        HostRecord r = ops.createHost(new HostSpec("demo", "sim1", "s-1vcpu-1gb", "ubuntu-24-04-x64", List.of(), ""));
        assertThat(r.active()).isFalse();
        assertThat(r.address()).isNull();
        now.addAndGet(SimulatedWorld.ACTIVE_AFTER_S * 1000L);
        r = ops.readHost(r.providerId());
        assertThat(r.active()).isTrue();
        assertThat(SimulatedWorld.owns(r.address())).isTrue();
        SshTarget t = new SshTarget("root", r.address(), 22, null);
        RemoteRunner runner = world.runner();
        List<String> lines = new ArrayList<>();
        assertThat(runner.run(t, HostProbe.SCRIPT, null, lines::add, java.time.Duration.ofSeconds(1)).exitCode()).isEqualTo(255);
        now.addAndGet((SimulatedWorld.SSH_AFTER_S - SimulatedWorld.ACTIVE_AFTER_S) * 1000L);
        HostFacts facts = HostProbe.probe(runner, t);
        assertThat(facts).isNotNull();
        assertThat(facts.cloudInit()).isEqualTo("running");
        assertThat(facts.docker()).isFalse();
        now.addAndGet((SimulatedWorld.CLOUD_INIT_DONE_AFTER_S - SimulatedWorld.SSH_AFTER_S) * 1000L);
        facts = HostProbe.probe(runner, t);
        assertThat(facts.cloudInit()).isEqualTo("done");
        assertThat(facts.docker()).isTrue();
        assertThat(facts.moshServer()).isTrue();
        assertThat(facts.load1()).isGreaterThan(0);
        assertThat(facts.containers()).isEmpty();
        ops.destroyHost(r.providerId());
        assertThat(ops.readHost(r.providerId())).isNull();
        assertThat(ops.listHosts()).isEmpty();
    }

    @Test
    @DisplayName("a deploy against the simulation leaves a container the next probe lists, with the label and the port")
    void deploy(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("Dockerfile"), "FROM scratch\nEXPOSE 3000\n");
        AtomicLong now = new AtomicLong(0);
        SimulatedWorld world = new SimulatedWorld(now::get).unpaced();
        SimulatedOps ops = new SimulatedOps(world);
        HostRecord r = ops.createHost(new HostSpec("demo", "sim1", "s-1vcpu-1gb", "ubuntu-24-04-x64", List.of(), ""));
        now.set(SimulatedWorld.CLOUD_INIT_DONE_AFTER_S * 1000L + 1);
        r = ops.readHost(r.providerId());
        SshTarget t = new SshTarget("root", r.address(), 22, null);
        DeployPlan plan = new DeployPlan("shop", 80, 3000, "5");
        Deployer.Outcome out = new Deployer(world.runner()).deploy(project, plan, t, new Deployer.Listener() {
            public void stepStarted(Deployer.Step step) { }
            public void line(Deployer.Step step, String line) { }
            public void stepFinished(Deployer.Step step, boolean ok) { }
        });
        assertThat(out.ok()).as(out.reason()).isTrue();
        assertThat(out.httpCode()).isEqualTo("200");
        HostFacts facts = HostProbe.probe(world.runner(), t);
        assertThat(facts.containers()).hasSize(1);
        assertThat(facts.containers().get(0).labels()).containsEntry(DeployPlan.LABEL, "shop");
        assertThat(facts.containers().get(0).publishedPorts()).containsExactly(80);
    }

    @Test
    @DisplayName("the simulation keeps its image history: two deploys can be listed, and the earlier one started again")
    void rollback(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("Dockerfile"), "FROM scratch\nEXPOSE 3000\n");
        AtomicLong now = new AtomicLong(0);
        SimulatedWorld world = new SimulatedWorld(now::get).unpaced();
        SimulatedOps ops = new SimulatedOps(world);
        HostRecord r = ops.createHost(new HostSpec("demo", "sim1", "s-1vcpu-1gb", "ubuntu-24-04-x64", List.of(), ""));
        now.set(SimulatedWorld.CLOUD_INIT_DONE_AFTER_S * 1000L + 1);
        SshTarget t = new SshTarget("root", ops.readHost(r.providerId()).address(), 22, null);
        Deployer.Listener quiet = new Deployer.Listener() {
            public void stepStarted(Deployer.Step step) { }
            public void line(Deployer.Step step, String line) { }
            public void stepFinished(Deployer.Step step, boolean ok) { }
        };
        assertThat(new Deployer(world.runner()).deploy(project, new DeployPlan("shop", 80, 3000, "100"), t, quiet).ok()).isTrue();
        assertThat(new Deployer(world.runner()).deploy(project, new DeployPlan("shop", 80, 3000, "200"), t, quiet).ok()).isTrue();
        List<String> tags = new java.util.ArrayList<>();
        assertThat(world.runner().run(t, org.nmox.studio.infra.ops.remote.Rollback.imagesCommand("shop"), null,
                tags::add, java.time.Duration.ofSeconds(5)).ok()).isTrue();
        assertThat(org.nmox.studio.infra.ops.remote.Rollback.previous("200", tags)).isEqualTo("100");
        Deployer.Outcome back = new Deployer(world.runner()).rollback(new DeployPlan("shop", 80, 3000, "100"), t, quiet);
        assertThat(back.ok()).as(String.valueOf(back.reason())).isTrue();
        HostFacts facts = HostProbe.probe(world.runner(), t);
        assertThat(facts.containers()).hasSize(1);
        assertThat(facts.containers().get(0).image()).isEqualTo("nmox/shop:100");
    }

    @Test
    @DisplayName("before cloud-init is done, docker is not there and a deploy says so")
    void tooEarly(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("Dockerfile"), "FROM scratch\n");
        AtomicLong now = new AtomicLong(0);
        SimulatedWorld world = new SimulatedWorld(now::get).unpaced();
        HostRecord r = new SimulatedOps(world).createHost(new HostSpec("demo", "sim1", "s", "i", List.of(), ""));
        now.set(SimulatedWorld.SSH_AFTER_S * 1000L + 1);
        r = new SimulatedOps(world).readHost(r.providerId());
        Deployer.Outcome out = new Deployer(world.runner()).deploy(project, new DeployPlan("shop", 80, 3000, "5"),
                new SshTarget("root", r.address(), 22, null), new Deployer.Listener() {
            public void stepStarted(Deployer.Step step) { }
            public void line(Deployer.Step step, String line) { }
            public void stepFinished(Deployer.Step step, boolean ok) { }
        });
        assertThat(out.failedAt()).isEqualTo(Deployer.Step.BUILD);
        assertThat(out.reason()).contains("docker: command not found");
    }
}
