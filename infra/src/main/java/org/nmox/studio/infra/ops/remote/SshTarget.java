package org.nmox.studio.infra.ops.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Where an ssh goes and how. Pure: holds the user, address, port and an
 * optional identity file, and builds the {@code ssh} / {@code mosh} argument
 * vectors every remote operation uses, so the connect door, the probe and
 * the deployer can never disagree about options.
 *
 * <p>The options, and why: {@code BatchMode=yes} so a non-interactive run
 * fails instead of waiting for a password nobody can type;
 * {@code ConnectTimeout} so a dead host answers in seconds;
 * {@code StrictHostKeyChecking=accept-new} with the Hub's OWN known_hosts
 * file, so a server created a minute ago is trusted on first contact
 * (there is nothing to compare it against) while a CHANGED key is still
 * refused — and the user's {@code ~/.ssh/known_hosts} is never written.
 */
public record SshTarget(String user, String address, int port, String identityFile) {

    /** Names ssh will accept as a host or user: no option-looking or shell-looking characters. */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:\\[\\]-]+");

    public SshTarget {
        if (user == null || user.isBlank()) {
            user = "root";
        }
        if (!SAFE.matcher(user).matches() || user.startsWith("-")) {
            throw new IllegalArgumentException("not an ssh user name: " + user);
        }
        if (address == null || !SAFE.matcher(address).matches() || address.startsWith("-")) {
            throw new IllegalArgumentException("not a host address: " + address);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("not a port: " + port);
        }
        if (identityFile != null && identityFile.isBlank()) {
            identityFile = null;
        }
    }

    public String spec() {
        return user + "@" + address;
    }

    /**
     * Parses {@code [user@]host[:port]}; the user defaults to root, the port to 22.
     * A bracketed IPv6 address keeps its colons: {@code root@[::1]:2222}.
     */
    public static SshTarget parse(String text, String identityFile) {
        String s = text == null ? "" : text.trim();
        String user = "root";
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            user = s.substring(0, at);
            s = s.substring(at + 1);
        }
        int port = 22;
        int colon = s.lastIndexOf(':');
        boolean bracketed = s.startsWith("[");
        if (colon > 0 && (!bracketed || s.indexOf(']') < colon)) {
            String portText = s.substring(colon + 1);
            if (!portText.chars().allMatch(Character::isDigit) || portText.isEmpty()) {
                throw new IllegalArgumentException("not a port: " + portText);
            }
            port = Integer.parseInt(portText);
            s = s.substring(0, colon);
        }
        return new SshTarget(user, s, port, identityFile);
    }

    /** The {@code -o}/{@code -p}/{@code -i} options, in one order, for ssh itself. */
    public List<String> sshOptions(String knownHostsFile, int connectTimeoutSeconds) {
        List<String> o = new ArrayList<>();
        o.add("-o");
        o.add("BatchMode=yes");
        o.add("-o");
        o.add("ConnectTimeout=" + connectTimeoutSeconds);
        o.add("-o");
        o.add("StrictHostKeyChecking=accept-new");
        if (knownHostsFile != null) {
            o.add("-o");
            o.add("UserKnownHostsFile=" + knownHostsFile);
        }
        o.add("-p");
        o.add(Integer.toString(port));
        if (identityFile != null) {
            o.add("-i");
            o.add(identityFile);
        }
        return o;
    }

    /** {@code ssh <options> user@host -- <remote command>}: one remote shell line. */
    public List<String> sshCommand(String knownHostsFile, String remoteCommand) {
        List<String> argv = new ArrayList<>();
        argv.add("ssh");
        argv.addAll(sshOptions(knownHostsFile, 8));
        argv.add(spec());
        argv.add("--");
        argv.add(remoteCommand);
        return argv;
    }

    /** The interactive {@code ssh} a terminal runs (no BatchMode: a passphrase prompt is fine there). */
    public List<String> interactiveSsh(String knownHostsFile) {
        List<String> argv = new ArrayList<>();
        argv.add("ssh");
        argv.add("-o");
        argv.add("StrictHostKeyChecking=accept-new");
        if (knownHostsFile != null) {
            argv.add("-o");
            argv.add("UserKnownHostsFile=" + knownHostsFile);
        }
        argv.add("-p");
        argv.add(Integer.toString(port));
        if (identityFile != null) {
            argv.add("-i");
            argv.add(identityFile);
        }
        argv.add(spec());
        return argv;
    }

    /**
     * The interactive {@code mosh} a terminal runs. mosh takes its ssh
     * command as ONE string it splits on whitespace, so every option is
     * passed in that string; a known_hosts path with a space or a quote
     * cannot be handed over that way and is left out (the user's own
     * known_hosts then applies — mosh still works, the Hub's isolation is
     * what is lost).
     */
    public List<String> interactiveMosh(String knownHostsFile) {
        StringBuilder ssh = new StringBuilder("ssh -o StrictHostKeyChecking=accept-new");
        if (knownHostsFile != null && knownHostsFile.chars().noneMatch(c -> Character.isWhitespace(c) || c == '\'' || c == '"')) {
            ssh.append(" -o UserKnownHostsFile=").append(knownHostsFile);
        }
        ssh.append(" -p ").append(port);
        if (identityFile != null && identityFile.chars().noneMatch(Character::isWhitespace)) {
            ssh.append(" -i ").append(identityFile);
        }
        List<String> argv = new ArrayList<>();
        argv.add("mosh");
        argv.add("--ssh=" + ssh);
        argv.add(spec());
        return argv;
    }
}
