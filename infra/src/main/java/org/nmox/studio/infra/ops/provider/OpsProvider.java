package org.nmox.studio.infra.ops.provider;

import java.io.IOException;
import java.util.List;

/**
 * A place that makes servers. DigitalOcean is the first; the simulation is
 * the second and says so; Hetzner is the obvious third. Everything after
 * a host exists — connect, probe, deploy — is ssh and knows no provider,
 * which is what keeps this interface small.
 *
 * <p>Implementations are found through the default Lookup
 * ({@code @ServiceProvider}), so another provider is a class, not a change
 * to the Hub.
 */
public interface OpsProvider {

    /** A short stable id: {@code digitalocean}, {@code simulation}. */
    String id();

    /** What the chip reads. */
    String displayName();

    /** True when nothing this provider reports is real. */
    boolean simulated();

    /** False until a token (or whatever the provider needs) is in place; the Hub then offers the door, not the action. */
    boolean configured();

    /**
     * Whether this provider is configured with an API token the Hub's own
     * Token… button can set. The Simulation takes none.
     */
    default boolean takesToken() {
        return false;
    }

    /**
     * Keeps {@code token} where this provider reads its token from (the OS
     * keychain). Blocking: never on the EDT. A provider that takes no token
     * does nothing.
     */
    default void storeToken(String token) {
    }

    /** What a new host can be: regions, sizes with prices, images, ssh keys. Network. */
    ProviderOptions options() throws IOException, InterruptedException;

    /** Every host the provider lists for this account. Network. */
    List<HostRecord> listHosts() throws IOException, InterruptedException;

    /** Asks for a host; returns it as first listed (usually without an address yet). Network, billable. */
    HostRecord createHost(HostSpec spec) throws IOException, InterruptedException;

    /** One host's current state, or null when the provider no longer lists it. Network. */
    HostRecord readHost(String providerId) throws IOException, InterruptedException;

    /** Destroys a host. Network, irreversible. */
    void destroyHost(String providerId) throws IOException, InterruptedException;

    /**
     * Registers this machine's public key with the account so a new host
     * can be reached; returns the provider's key id. Network.
     */
    String addSshKey(String name, String publicKey) throws IOException, InterruptedException;
}
