package org.nmox.studio.rack.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The port radar: who is listening on localhost, by port, with the
 * process that owns it. Built on lsof because every macOS and Linux
 * box has it - the flags nobody remembers, remembered once, here.
 */
public final class PortScanner {

    /** One listening socket: the port and the process holding it. */
    public record PortInfo(int port, long pid, String command) {
    }

    private PortScanner() {
    }

    /** All listening TCP ports, deduped by port, lowest pid wins. */
    public static CompletableFuture<List<PortInfo>> scan() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "lsof", "-nP", "-iTCP", "-sTCP:LISTEN", "-F", "pcn")
                        .redirectInput(new File("/dev/null"));
                pb.environment().put("PATH", ToolLocator.augmentedPath());
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes());
                p.waitFor(10, TimeUnit.SECONDS);
                return parse(out);
            } catch (Exception ex) {
                return List.of();
            }
        });
    }

    /**
     * Parses lsof -F pcn machine output: p<pid>, c<command>, n<name>
     * lines, where the name carries host:port. Pure; tested on canned
     * output.
     */
    static List<PortInfo> parse(String out) {
        Map<Integer, PortInfo> byPort = new LinkedHashMap<>();
        long pid = -1;
        String command = "";
        Pattern portAtEnd = Pattern.compile(":(\\d+)$");
        for (String line : out.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            char tag = line.charAt(0);
            String value = line.substring(1);
            switch (tag) {
                case 'p' -> pid = Long.parseLong(value.trim());
                case 'c' -> command = value.trim();
                case 'n' -> {
                    Matcher m = portAtEnd.matcher(value.trim());
                    if (m.find()) {
                        int port = Integer.parseInt(m.group(1));
                        byPort.putIfAbsent(port, new PortInfo(port, pid, command));
                    }
                }
                default -> {
                    // f/t/other field tags are noise for our purposes
                }
            }
        }
        List<PortInfo> result = new ArrayList<>(byPort.values());
        result.sort(java.util.Comparator.comparingInt(PortInfo::port));
        return result;
    }

    /** SIGTERM the owner of a port; the caller confirms first. */
    public static boolean kill(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::destroy).orElse(false);
    }
}
