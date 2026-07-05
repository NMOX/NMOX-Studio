package org.nmox.studio.dbstudio.io;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.rack.docker.DockerClient.ContainerInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Docker connection-offer rules: engine from the image name first
 * (tags and registries included), from the published DB port second;
 * the host port is the one mapped from the engine's container port;
 * stopped containers, already-configured sockets, already-offered ids
 * and everything past the per-refresh cap stay silent.
 */
class DockerDbOffersTest {

    private static ContainerInfo running(String id, String name, String image, String ports) {
        return new ContainerInfo(id, name, image, "running", "Up 2 hours", ports, List.of());
    }

    private static ConnectionSpec spec(DbEngine engine, String host, int port) {
        return new ConnectionSpec("id-" + host + port, "c", engine, host, port, "db", "u", "");
    }

    // ---- engine inference: image name first ---------------------------

    @Test
    @DisplayName("postgres:16-alpine offers PostgreSQL on the published port")
    void postgresImageWithTag() {
        List<DockerDbOffers.Offer> offers = DockerDbOffers.plan(
                List.of(running("c1", "shop-db", "postgres:16-alpine",
                        "0.0.0.0:5432->5432/tcp")),
                List.of(), Set.of());

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).engine()).isEqualTo(DbEngine.POSTGRES);
        assertThat(offers.get(0).containerName()).isEqualTo("shop-db");
        assertThat(offers.get(0).hostPort()).isEqualTo(5432);
    }

    @Test
    @DisplayName("A registry-prefixed image is still recognized")
    void registryPrefixedImage() {
        List<DockerDbOffers.Offer> offers = DockerDbOffers.plan(
                List.of(running("c1", "db", "docker.io/library/postgres:16",
                        "0.0.0.0:5432->5432/tcp")),
                List.of(), Set.of());

        assertThat(offers).extracting(DockerDbOffers.Offer::engine)
                .containsExactly(DbEngine.POSTGRES);
    }

    @Test
    @DisplayName("mariadb beats the mysql keyword and the 3306 fallback")
    void mariadbImage() {
        List<DockerDbOffers.Offer> offers = DockerDbOffers.plan(
                List.of(running("c1", "db", "mariadb:11", "0.0.0.0:3306->3306/tcp")),
                List.of(), Set.of());

        assertThat(offers).extracting(DockerDbOffers.Offer::engine)
                .containsExactly(DbEngine.MARIADB);
    }

    @Test
    @DisplayName("mysql, mongo and couchdb images map to their engines")
    void otherImages() {
        assertThat(DockerDbOffers.engineForImage("mysql:8")).isEqualTo(DbEngine.MYSQL);
        assertThat(DockerDbOffers.engineForImage("mongo:7")).isEqualTo(DbEngine.MONGODB);
        assertThat(DockerDbOffers.engineForImage("couchdb:3")).isEqualTo(DbEngine.COUCHDB);
        assertThat(DockerDbOffers.engineForImage("nginx:alpine")).isNull();
        assertThat(DockerDbOffers.engineForImage(null)).isNull();
    }

    // ---- engine inference: port fallback -------------------------------

    @Test
    @DisplayName("An unrecognized image with a published 5432 reads as PostgreSQL")
    void portFallback() {
        List<DockerDbOffers.Offer> offers = DockerDbOffers.plan(
                List.of(running("c1", "warehouse", "acme/warehouse-db:2",
                        "0.0.0.0:5432->5432/tcp")),
                List.of(), Set.of());

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).engine()).isEqualTo(DbEngine.POSTGRES);
    }

    @Test
    @DisplayName("Port-only 3306 reads as MySQL — only an image name can say MariaDB")
    void portFallback3306() {
        List<DockerDbOffers.Offer> offers = DockerDbOffers.plan(
                List.of(running("c1", "legacy", "acme/legacy:1", "0.0.0.0:3306->3306/tcp")),
                List.of(), Set.of());

        assertThat(offers).extracting(DockerDbOffers.Offer::engine)
                .containsExactly(DbEngine.MYSQL);
    }

    // ---- the host-port rule --------------------------------------------

    @Test
    @DisplayName("A remapped port offers the HOST side of the mapping")
    void remappedHostPort() {
        List<DockerDbOffers.Offer> offers = DockerDbOffers.plan(
                List.of(running("c1", "shop-db", "postgres:16", "0.0.0.0:15432->5432/tcp")),
                List.of(), Set.of());

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).hostPort()).isEqualTo(15432);
    }

    @Test
    @DisplayName("An image match without its DB port published offers nothing (mongo-express)")
    void imageMatchWithoutDbPort() {
        List<DockerDbOffers.Offer> offers = DockerDbOffers.plan(
                List.of(running("c1", "mongo-ui", "mongo-express:1",
                        "0.0.0.0:8081->8081/tcp")),
                List.of(), Set.of());

        assertThat(offers).isEmpty();
    }

    @Test
    @DisplayName("IPv4 and IPv6 duplicates of one mapping collapse to one offer")
    void ipv6Duplicates() {
        List<DockerDbOffers.Offer> offers = DockerDbOffers.plan(
                List.of(running("c1", "db", "postgres:16",
                        "0.0.0.0:5432->5432/tcp, :::5432->5432/tcp")),
                List.of(), Set.of());

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).hostPort()).isEqualTo(5432);
    }

    @Test
    @DisplayName("Among several published ports, the engine's own is chosen")
    void picksTheEnginePortAmongMany() {
        Map<Integer, Integer> published = DockerDbOffers.publishedPorts(
                "0.0.0.0:8080->8080/tcp, 0.0.0.0:15432->5432/tcp");

        assertThat(published).containsEntry(8080, 8080).containsEntry(5432, 15432);

        List<DockerDbOffers.Offer> offers = DockerDbOffers.plan(
                List.of(running("c1", "db", "postgres:16",
                        "0.0.0.0:8080->8080/tcp, 0.0.0.0:15432->5432/tcp")),
                List.of(), Set.of());
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).hostPort()).isEqualTo(15432);
    }

    // ---- suppression ----------------------------------------------------

    @Test
    @DisplayName("A stopped database container offers nothing")
    void stoppedContainerIgnored() {
        ContainerInfo stopped = new ContainerInfo("c1", "db", "postgres:16",
                "exited", "Exited (0) 2 hours ago", "", List.of());

        assertThat(DockerDbOffers.plan(List.of(stopped), List.of(), Set.of())).isEmpty();
    }

    @Test
    @DisplayName("An existing localhost connection on the same port suppresses the offer")
    void alreadyConfiguredSuppressed() {
        List<ContainerInfo> containers =
                List.of(running("c1", "db", "postgres:16", "0.0.0.0:5432->5432/tcp"));

        assertThat(DockerDbOffers.plan(containers,
                List.of(spec(DbEngine.POSTGRES, "localhost", 5432)), Set.of())).isEmpty();
        assertThat(DockerDbOffers.plan(containers,
                List.of(spec(DbEngine.MYSQL, "LOCALHOST", 5432)), Set.of()))
                .as("engine regardless, host case-insensitive")
                .isEmpty();
        assertThat(DockerDbOffers.plan(containers,
                List.of(spec(DbEngine.POSTGRES, "127.0.0.1", 5432)), Set.of())).isEmpty();
        assertThat(DockerDbOffers.plan(containers,
                List.of(spec(DbEngine.POSTGRES, "localhost", -1)), Set.of()))
                .as("an unset spec port means the engine default, 5432")
                .isEmpty();
    }

    @Test
    @DisplayName("Connections to other hosts or ports do not suppress")
    void unrelatedSpecsDoNotSuppress() {
        List<ContainerInfo> containers =
                List.of(running("c1", "db", "postgres:16", "0.0.0.0:5432->5432/tcp"));

        assertThat(DockerDbOffers.plan(containers,
                List.of(spec(DbEngine.POSTGRES, "db.internal", 5432)), Set.of())).hasSize(1);
        assertThat(DockerDbOffers.plan(containers,
                List.of(spec(DbEngine.POSTGRES, "localhost", 15432)), Set.of())).hasSize(1);
    }

    @Test
    @DisplayName("SQLite and engine-less (Services) specs never suppress")
    void sqliteAndServicesSpecsNeverMatch() {
        List<ContainerInfo> containers =
                List.of(running("c1", "db", "postgres:16", "0.0.0.0:5432->5432/tcp"));
        ConnectionSpec sqlite = new ConnectionSpec("s", "file db", DbEngine.SQLITE,
                "", -1, "", "", "/tmp/x.db");
        ConnectionSpec services = new ConnectionSpec("nb:1", "derby", null,
                "localhost", 5432, "db", "u", "");

        assertThat(DockerDbOffers.plan(containers, List.of(sqlite, services), Set.of()))
                .hasSize(1);
    }

    @Test
    @DisplayName("A container already offered this session stays quiet")
    void alreadyOfferedSuppressed() {
        List<ContainerInfo> containers =
                List.of(running("c1", "db", "postgres:16", "0.0.0.0:5432->5432/tcp"));

        assertThat(DockerDbOffers.plan(containers, List.of(), Set.of("c1"))).isEmpty();
    }

    // ---- the cap (the bounded-reaction law) ------------------------------

    @Test
    @DisplayName("At most two offers per refresh; the rest surface on the next one")
    void capAndRerun() {
        List<ContainerInfo> containers = List.of(
                running("c1", "pg", "postgres:16", "0.0.0.0:5432->5432/tcp"),
                running("c2", "my", "mysql:8", "0.0.0.0:3306->3306/tcp"),
                running("c3", "mg", "mongo:7", "0.0.0.0:27017->27017/tcp"));

        List<DockerDbOffers.Offer> first = DockerDbOffers.plan(containers, List.of(), Set.of());
        assertThat(first).hasSize(DockerDbOffers.MAX_OFFERS_PER_REFRESH);
        assertThat(first).extracting(DockerDbOffers.Offer::containerId)
                .containsExactly("c1", "c2");

        Set<String> offered = new HashSet<>();
        first.forEach(offer -> offered.add(offer.containerId()));
        List<DockerDbOffers.Offer> second = DockerDbOffers.plan(containers, List.of(), offered);
        assertThat(second).extracting(DockerDbOffers.Offer::containerId)
                .containsExactly("c3");

        offered.add("c3");
        assertThat(DockerDbOffers.plan(containers, List.of(), offered))
                .as("a third refresh reacts to nothing — bounded, not a drumbeat")
                .isEmpty();
    }

    // ---- the dialog prefill ----------------------------------------------

    @Test
    @DisplayName("The prefill carries each engine's conventional first-login defaults")
    void suggestionDefaults() {
        var pg = DockerDbOffers.suggestion(
                new DockerDbOffers.Offer(DbEngine.POSTGRES, "c1", "shop-db", 15432));
        assertThat(pg.engine()).isEqualTo(DbEngine.POSTGRES);
        assertThat(pg.host()).isEqualTo("localhost");
        assertThat(pg.port()).isEqualTo(15432);
        assertThat(pg.database()).isEqualTo("postgres");
        assertThat(pg.user()).isEqualTo("postgres");
        assertThat(pg.passwordOrNull()).as("passwords are typed, never guessed").isNull();

        var my = DockerDbOffers.suggestion(
                new DockerDbOffers.Offer(DbEngine.MYSQL, "c2", "shop", 3306));
        assertThat(my.database()).isEmpty();
        assertThat(my.user()).isEqualTo("root");

        var maria = DockerDbOffers.suggestion(
                new DockerDbOffers.Offer(DbEngine.MARIADB, "c3", "shop", 3306));
        assertThat(maria.user()).isEqualTo("root");

        var mongo = DockerDbOffers.suggestion(
                new DockerDbOffers.Offer(DbEngine.MONGODB, "c4", "docs", 27017));
        assertThat(mongo.database()).isEmpty();
        assertThat(mongo.user()).isEmpty();

        var couch = DockerDbOffers.suggestion(
                new DockerDbOffers.Offer(DbEngine.COUCHDB, "c5", "docs", 5984));
        assertThat(couch.user()).isEmpty();
    }

    @Test
    @DisplayName("The balloon line names the engine, the container and the host port")
    void offerText() {
        String text = DockerDbOffers.offerText(
                new DockerDbOffers.Offer(DbEngine.POSTGRES, "c1", "shop-db", 5432));

        assertThat(text).isEqualTo(
                "PostgreSQL container \"shop-db\" publishes 5432 — create a connection?");
    }

    @Test
    @DisplayName("A null or empty ports string parses to no mappings")
    void emptyPorts() {
        assertThat(DockerDbOffers.publishedPorts(null)).isEmpty();
        assertThat(DockerDbOffers.publishedPorts("")).isEmpty();
    }

    // ---- the visibility gate: balloons only where someone can see them ----

    @Test
    @DisplayName("hidden → plan held, guard unconsumed; showing → displayed once, guard consumed")
    void hiddenHoldsThenShowingDisplaysOnce() {
        DockerDbOffers.Hold hold = new DockerDbOffers.Hold();
        Set<String> offered = new HashSet<>();
        List<ContainerInfo> probe = List.of(
                running("c1", "shop-db", "postgres:16", "0.0.0.0:5432->5432/tcp"));

        // probe finishes while the tab is hidden: nothing to display,
        // nothing planned, so the caller consumes no guard
        assertThat(hold.onProbe(probe, false)).isEmpty();
        assertThat(offered).isEmpty();

        // the tab becomes visible: the held plan releases exactly once,
        // and only this display pass records the container id
        List<ContainerInfo> shown = hold.onShowing();
        assertThat(shown).isEqualTo(probe);
        for (DockerDbOffers.Offer offer : DockerDbOffers.plan(shown, List.of(), offered)) {
            offered.add(offer.containerId()); // the caller's display-time add
        }
        assertThat(offered).containsExactly("c1");
        assertThat(hold.onShowing()).as("released once, not on every showing").isEmpty();
    }

    @Test
    @DisplayName("A probe finishing while showing displays immediately, holds nothing")
    void showingDisplaysImmediately() {
        DockerDbOffers.Hold hold = new DockerDbOffers.Hold();
        List<ContainerInfo> probe = List.of(
                running("c1", "shop-db", "postgres:16", "0.0.0.0:5432->5432/tcp"));

        assertThat(hold.onProbe(probe, true)).isEqualTo(probe);
        assertThat(hold.onShowing()).isEmpty();
    }

    @Test
    @DisplayName("Bounded: only the latest hidden probe is held")
    void latestPlanOnly() {
        DockerDbOffers.Hold hold = new DockerDbOffers.Hold();
        List<ContainerInfo> older = List.of(
                running("c1", "old-db", "postgres:16", "0.0.0.0:5432->5432/tcp"));
        List<ContainerInfo> newer = List.of(
                running("c2", "new-db", "mariadb:11", "0.0.0.0:3306->3306/tcp"));

        assertThat(hold.onProbe(older, false)).isEmpty();
        assertThat(hold.onProbe(newer, false)).isEmpty();
        assertThat(hold.onShowing()).isEqualTo(newer);
    }

    @Test
    @DisplayName("A visible display drops any staler held plan; clear() empties the hold")
    void freshDisplayAndClearDropHeld() {
        DockerDbOffers.Hold hold = new DockerDbOffers.Hold();
        List<ContainerInfo> older = List.of(
                running("c1", "old-db", "postgres:16", "0.0.0.0:5432->5432/tcp"));
        List<ContainerInfo> newer = List.of(
                running("c2", "new-db", "mariadb:11", "0.0.0.0:3306->3306/tcp"));

        hold.onProbe(older, false);
        assertThat(hold.onProbe(newer, true)).isEqualTo(newer);
        assertThat(hold.onShowing()).as("shown truth supersedes the held plan").isEmpty();

        hold.onProbe(older, false);
        hold.clear(); // the tab closed — a held plan is stale by reopen time
        assertThat(hold.onShowing()).isEmpty();
    }
}
