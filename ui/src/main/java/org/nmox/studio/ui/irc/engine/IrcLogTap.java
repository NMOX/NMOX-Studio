package org.nmox.studio.ui.irc.engine;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.nmox.studio.ui.irc.protocol.Ctcp;
import org.nmox.studio.ui.irc.protocol.IrcMessage;
import org.nmox.studio.ui.irc.protocol.NickPrefix;

/**
 * Logging as a CLIENT-lifetime concern, not a window-lifetime one
 * (v1.322.0, ledger 66 closed).
 *
 * <p>The debt: connections deliberately outlive the IRC window
 * (v1.204.0), but every {@link IrcLogger} call lived in the window's
 * Bridge listener — so with logging enabled and the tab closed,
 * traffic in that window-closed period was silently lost to the log
 * files the user turned on. This tap is a second {@link
 * IrcClient.Listener} attached when the CLIENT is created, so it lives
 * exactly as long as the connection does; the Bridge no longer logs
 * inbound traffic at all (one writer per line, no double-logging).
 *
 * <p>It keeps its own minimal channel-membership map — seeded by 353,
 * maintained by JOIN/PART/KICK/NICK — because QUIT names no channels:
 * fanning a quit out to the channels the nick was actually in is the
 * one translation that needs state. The map is the tap's own rather
 * than a reach into the window's nick lists precisely because the
 * window may not exist.
 *
 * <p>Deliberate boundary: the user's OWN sends are still logged by the
 * window's send path. A send requires a window by construction, so no
 * window-closed loss window exists for them — and the engine's
 * {@code privmsg} verb stays logging-free for programmatic callers.
 *
 * <p>Threading: {@code lineReceived} arrives on the engine thread;
 * {@link IrcLogger} queues writes on its own single lane, so nothing
 * here blocks the socket pump.
 */
public final class IrcLogTap implements IrcClient.Listener {

    private final String network;
    private final IrcLogger logger;
    /** channel (lower) → member nicks (lower); QUIT fan-out only. */
    private final Map<String, Set<String>> members = new ConcurrentHashMap<>();
    private volatile String self = "";

    public IrcLogTap(String network, IrcLogger logger) {
        this.network = network;
        this.logger = logger;
    }

    @Override
    public void connected() {
    }

    @Override
    public void registered(String nick) {
        self = nick == null ? "" : nick;
    }

    @Override
    public void disconnected(String reason) {
        members.clear();
    }

    @Override
    public void lineReceived(IrcMessage msg) {
        switch (msg.command()) {
            case "PRIVMSG" -> privmsg(msg);
            case "JOIN" -> {
                String chan = msg.trailing() != null ? msg.trailing() : msg.param(0);
                String who = msg.nick() == null ? "?" : msg.nick();
                channelMembers(chan).add(lower(who));
                logger.event(network, chan, who + " joined");
            }
            case "PART" -> {
                String chan = msg.param(0);
                String who = msg.nick() == null ? "?" : msg.nick();
                channelMembers(chan).remove(lower(who));
                logger.event(network, chan, who + " left");
            }
            case "KICK" -> {
                String chan = msg.param(0);
                String victim = msg.param(1);
                if (victim != null) {
                    channelMembers(chan).remove(lower(victim));
                }
                logger.event(network, chan, victim + " was kicked");
            }
            case "QUIT" -> {
                String who = msg.nick() == null ? "?" : msg.nick();
                String lowerWho = lower(who);
                for (Map.Entry<String, Set<String>> e : members.entrySet()) {
                    if (e.getValue().remove(lowerWho)) {
                        logger.event(network, e.getKey(), who + " quit");
                    }
                }
            }
            case "NICK" -> {
                // no log line (the Bridge never logged renames either), but
                // the membership map must follow, or this nick's later QUIT
                // fans out to nothing
                String from = msg.nick() == null ? "" : lower(msg.nick());
                String to = msg.trailing() != null ? msg.trailing() : msg.param(0);
                if (to != null && !from.isEmpty()) {
                    for (Set<String> chanMembers : members.values()) {
                        if (chanMembers.remove(from)) {
                            chanMembers.add(lower(to));
                        }
                    }
                    if (from.equals(lower(self))) {
                        self = to;
                    }
                }
            }
            case "353" -> {
                // RPL_NAMREPLY: <me> <sym> <chan> :@nick1 +nick2 nick3 — the
                // only way to learn who was already in a channel when we
                // joined; without it, a long-time member's QUIT logs nowhere
                String chan = msg.param(2);
                String names = msg.trailing();
                if (chan != null && names != null) {
                    Set<String> set = channelMembers(chan);
                    for (String raw : names.split(" ")) {
                        if (!raw.isEmpty()) {
                            set.add(lower(NickPrefix.strip(raw)));
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    private void privmsg(IrcMessage msg) {
        String sender = msg.nick() == null ? network : msg.nick();
        String target = msg.param(0);
        String body = msg.trailing() == null ? "" : msg.trailing();
        boolean action = false;
        Ctcp ctcp = Ctcp.extract(body);
        if (ctcp != null) {
            if (Ctcp.ACTION.equals(ctcp.command())) {
                action = true;
                body = ctcp.argument();
            } else {
                // non-ACTION CTCP (VERSION, PING…) is protocol chatter, not
                // conversation — the Bridge never logged it and neither do we
                return;
            }
        }
        // channel traffic files under the channel; a private message files
        // under the PEER — for an echo-message copy of our own line that is
        // the target, for anything inbound it is the sender (the Bridge's
        // exact keying, so window-open and window-closed logs interleave
        // into the same files)
        String targetName;
        if (target != null && (target.startsWith("#") || target.startsWith("&"))) {
            targetName = target;
        } else if (sender.equalsIgnoreCase(self)) {
            targetName = target;
        } else {
            targetName = sender;
        }
        if (action) {
            logger.action(network, targetName, sender, body);
        } else {
            logger.chat(network, targetName, sender, body);
        }
    }

    private Set<String> channelMembers(String chan) {
        return members.computeIfAbsent(lower(chan),
                x -> ConcurrentHashMap.newKeySet());
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
