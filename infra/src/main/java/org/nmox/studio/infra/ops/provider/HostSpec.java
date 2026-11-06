package org.nmox.studio.infra.ops.provider;

import java.util.List;
import java.util.regex.Pattern;

/**
 * What the New Server dialog asks for. The name is validated as a DNS
 * label because every provider uses it as the hostname; the key ids are
 * the provider's own.
 */
public record HostSpec(String name, String region, String size, String image, List<String> sshKeyIds,
        String userData) {

    private static final Pattern NAME = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    public HostSpec {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("not a host name: " + name);
        }
        if (sshKeyIds == null) {
            sshKeyIds = List.of();
        }
        if (userData == null) {
            userData = "";
        }
    }

    /** True for a name a provider will take as a hostname. */
    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }
}
