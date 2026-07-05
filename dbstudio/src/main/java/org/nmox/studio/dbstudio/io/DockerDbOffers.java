package org.nmox.studio.dbstudio.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.rack.docker.DockerClient.ContainerInfo;

/**
 * The pure decision core behind "a database is running in Docker —
 * create a connection?". Given the docker container list, the
 * workspace's existing connections, and the container ids already
 * offered this session, {@link #plan} returns the (at most
 * {@value #MAX_OFFERS_PER_REFRESH}) offers worth a balloon right now.
 * The Swing layer only shows balloons and opens the prefilled dialog —
 * every rule lives here, pinned by tests.
 *
 * <p>The rules, in order:
 * <ol>
 *   <li>Only RUNNING containers — a stopped postgres serves nothing.</li>
 *   <li>The engine comes from the image name first
 *       ({@code postgres:16-alpine} → PostgreSQL, {@code mariadb:11} →
 *       MariaDB, …), from the published container port second (a
 *       mapping onto container port 5432/3306/27017/5984 names the
 *       engine when the image doesn't).</li>
 *   <li>The offer's host port is the one mapped FROM the engine's
 *       conventional container port ({@code -p 15432:5432} offers
 *       15432). No such mapping → no way in from the host → no offer;
 *       this also silences image-name near-misses like
 *       {@code mongo-express} (matches "mongo", publishes no 27017).</li>
 *   <li>A workspace connection already pointing at
 *       {@code localhost:<hostPort>} suppresses the offer — the user
 *       is already wired up.</li>
 *   <li>One offer per container id per session (the {@code offered}
 *       set, owned by the caller), and at most
 *       {@value #MAX_OFFERS_PER_REFRESH} per refresh — no balloon
 *       storms; the rest surface on the next refresh.</li>
 * </ol>
 */
public final class DockerDbOffers {

    /** Balloon cap per refresh — the rest wait for the next one. */
    public static final int MAX_OFFERS_PER_REFRESH = 2;

    /**
     * One connection worth offering.
     *
     * @param engine        the inferred database engine
     * @param containerId   docker's container id — keys the
     *                      once-per-session guard
     * @param containerName the container's name, for the balloon text
     * @param hostPort      the host port the database is reachable on
     */
    public record Offer(DbEngine engine, String containerId, String containerName,
            int hostPort) {
    }

    /**
     * host:container publish pairs — "0.0.0.0:15432->5432/tcp" is host
     * 15432, container 5432. Digits around a literal "->" only: linear
     * time, no alternation to backtrack (the DockerClient idiom).
     */
    private static final Pattern PORT_PAIR = Pattern.compile(":(\\d+)->(\\d+)/");

    /** Container ports that name an engine when the image doesn't. */
    private static final int[] KNOWN_DB_PORTS = {5432, 3306, 27017, 5984};

    private DockerDbOffers() {
    }

    /**
     * The offers worth showing right now, per the class rules. Never
     * mutates its inputs — the caller records shown offers into
     * {@code offered} itself.
     */
    public static List<Offer> plan(List<ContainerInfo> containers,
            List<ConnectionSpec> specs, Set<String> offered) {
        List<Offer> offers = new ArrayList<>();
        for (ContainerInfo container : containers) {
            if (offers.size() >= MAX_OFFERS_PER_REFRESH) {
                break;
            }
            if (!container.running() || offered.contains(container.id())) {
                continue;
            }
            Map<Integer, Integer> published = publishedPorts(container.ports());
            DbEngine engine = engineForImage(container.image());
            if (engine == null) {
                engine = engineForPublishedPorts(published);
            }
            if (engine == null) {
                continue;
            }
            Integer hostPort = published.get(engine.defaultPort());
            if (hostPort == null) {
                continue; // not reachable from the host — nothing to offer
            }
            if (alreadyPointsAtLocalhost(specs, hostPort)) {
                continue;
            }
            offers.add(new Offer(engine, container.id(), container.name(), hostPort));
        }
        return offers;
    }

    /**
     * The dialog prefill for an offer: localhost + the published port,
     * with each engine's conventional first-login defaults —
     * postgres/postgres, root for the MySQL family, empty for the
     * document engines. No password: the field stays blank and a typed
     * one rides the dialog into the OS keychain as always.
     */
    public static EnvConnections.Suggestion suggestion(Offer offer) {
        return new EnvConnections.Suggestion(offer.engine(), "localhost",
                offer.hostPort(), defaultDatabase(offer.engine()),
                defaultUser(offer.engine()), null);
    }

    /** The balloon's one-liner: what runs where, and the question. */
    public static String offerText(Offer offer) {
        return offer.engine().displayName() + " container \"" + offer.containerName()
                + "\" publishes " + offer.hostPort() + " — create a connection?";
    }

    /** The conventional maintenance database, where the engine has one. */
    static String defaultDatabase(DbEngine engine) {
        return engine == DbEngine.POSTGRES ? "postgres" : "";
    }

    /** The conventional superuser login, where the engine has one. */
    static String defaultUser(DbEngine engine) {
        return switch (engine) {
            case POSTGRES -> "postgres";
            case MYSQL, MARIADB -> "root";
            default -> "";
        };
    }

    /**
     * container-port → host-port from docker's Ports column; the first
     * mapping per container port wins (IPv4/IPv6 duplicates collapse).
     */
    static Map<Integer, Integer> publishedPorts(String ports) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        Matcher m = PORT_PAIR.matcher(ports == null ? "" : ports);
        while (m.find()) {
            map.putIfAbsent(Integer.valueOf(m.group(2)), Integer.valueOf(m.group(1)));
        }
        return map;
    }

    /**
     * Engine by image-name keyword, tags and registry prefixes
     * included ({@code docker.io/library/postgres:16-alpine} is still
     * postgres). Null when no keyword matches.
     */
    static DbEngine engineForImage(String image) {
        String name = image == null ? "" : image.toLowerCase(Locale.ROOT);
        if (name.contains("postgres")) {
            return DbEngine.POSTGRES;
        }
        if (name.contains("mariadb")) {
            return DbEngine.MARIADB;
        }
        if (name.contains("mysql")) {
            return DbEngine.MYSQL;
        }
        if (name.contains("couchdb")) {
            return DbEngine.COUCHDB;
        }
        if (name.contains("mongo")) {
            return DbEngine.MONGODB;
        }
        return null;
    }

    /**
     * Engine by published container port when the image name says
     * nothing — the first conventional DB port found wins (3306 reads
     * as MySQL; only the image name can say "MariaDB").
     */
    static DbEngine engineForPublishedPorts(Map<Integer, Integer> published) {
        for (int port : KNOWN_DB_PORTS) {
            if (published.containsKey(port)) {
                return switch (port) {
                    case 5432 -> DbEngine.POSTGRES;
                    case 3306 -> DbEngine.MYSQL;
                    case 27017 -> DbEngine.MONGODB;
                    default -> DbEngine.COUCHDB;
                };
            }
        }
        return null;
    }

    /**
     * Whether any existing connection already targets
     * {@code localhost:<hostPort>} (127.0.0.1 counts), engine
     * regardless — one connection per socket is plenty. Specs without
     * a modeled engine (Services entries) or without a port (SQLite)
     * never match.
     */
    static boolean alreadyPointsAtLocalhost(List<ConnectionSpec> specs, int hostPort) {
        for (ConnectionSpec spec : specs) {
            if (spec.engine() == null || spec.engine() == DbEngine.SQLITE) {
                continue;
            }
            String host = spec.host() == null ? "" : spec.host();
            boolean local = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1");
            int effective = spec.port() > 0 ? spec.port() : spec.engine().defaultPort();
            if (local && effective == hostPort) {
                return true;
            }
        }
        return false;
    }
}
