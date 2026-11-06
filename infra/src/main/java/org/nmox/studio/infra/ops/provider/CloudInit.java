package org.nmox.studio.infra.ops.provider;

/**
 * The first-boot configuration a Hub-made host gets: docker (so a deploy
 * has somewhere to run) and mosh-server (so Connect can prefer mosh), and
 * a marker the probe reads through cloud-init's own status. Ubuntu and
 * Debian images carry both packages; the Hub offers only those.
 */
public final class CloudInit {

    public static final String USER_DATA = """
            #cloud-config
            package_update: true
            packages:
              - mosh
              - docker.io
              - curl
            runcmd:
              - systemctl enable --now docker
            """;

    private CloudInit() {
    }
}
