package org.nmox.studio.editor.lsp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.openide.util.RequestProcessor;

/**
 * A language server's problems in Action Items (3.1.0). The platform's
 * LSP client paints {@code textDocument/publishDiagnostics} as squiggles
 * and keeps them there: nothing reaches the task list, so a TypeScript
 * error was visible only in the file that had it — while the docs told a
 * VS Code switcher that ⌘6 is their Problems panel.
 *
 * <p>This stream sits between the server's stdout and the client. Every
 * byte passes through unchanged, in the same read call; alongside, a
 * small frame parser watches for {@code publishDiagnostics} and hands
 * each file's current problems to the {@link DiagnosticsBus}, where
 * Action Items and the Agent Port already read. The squiggle layer
 * skips these tools ({@link #isLspTool}) — the client painted them.
 *
 * <p>Bounds: a header longer than 8 KiB or a body over 8 MiB is passed
 * through unparsed; at most 500 problems per file and 5,000 per server
 * name are kept. The parse never throws into the read: a malformed frame
 * is logged once and the stream carries on. Publishing runs on its own
 * single lane, coalesced, so a chatty server never waits on the task
 * list. When the server's stream ends, its problems are withdrawn.
 */
public final class DiagnosticsTap extends FilterInputStream {

    /** Tool names on the bus carry this prefix; see {@link #isLspTool}. */
    static final String TOOL_PREFIX = "lsp:";

    static final int MAX_HEADER = 8 * 1024;
    static final int MAX_BODY = 8 * 1024 * 1024;
    static final int MAX_PER_FILE = 500;
    static final int MAX_PER_TOOL = 5_000;

    private static final Logger LOG = Logger.getLogger(DiagnosticsTap.class.getName());
    private static final RequestProcessor LANE = new RequestProcessor("nmox-lsp-diagnostics", 1, false, false);

    /** tool → tap → uri → problems. Guarded by itself. */
    private static final Map<String, Map<DiagnosticsTap, Map<String, List<DiagnosticsBus.Problem>>>> STATE =
            new LinkedHashMap<>();
    private static final java.util.Set<String> DIRTY = new java.util.LinkedHashSet<>();
    private static final RequestProcessor.Task FLUSH = LANE.create(DiagnosticsTap::flush);

    /** True for a bus tool this class publishes (the squiggler skips them). */
    public static boolean isLspTool(String tool) {
        return tool != null && tool.startsWith(TOOL_PREFIX);
    }

    /**
     * The name a server's problems carry in Action Items, from its
     * command: {@code typescript-language-server} reads {@code typescript},
     * {@code rust-analyzer} stays itself.
     */
    static String toolFor(String command) {
        String name = new File(command).getName().toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".cmd", ".exe", ".bat", ".ps1"}) {
            if (name.endsWith(ext)) {
                name = name.substring(0, name.length() - ext.length());
            }
        }
        for (String suffix : new String[]{"-language-server", "-langserver", "-languageserver", "-lsp"}) {
            if (name.endsWith(suffix) && name.length() > suffix.length()) {
                name = name.substring(0, name.length() - suffix.length());
                break;
            }
        }
        return TOOL_PREFIX + name;
    }

    private final String tool;
    private final ByteArrayOutputStream header = new ByteArrayOutputStream();
    private ByteArrayOutputStream body;
    private int remaining = -1;   // body bytes still to come; -1 = reading a header
    private boolean skipping;     // header or body over its bound: pass through only
    private boolean warned;
    private boolean ended;

    public DiagnosticsTap(InputStream serverOut, String command) {
        super(serverOut);
        this.tool = toolFor(command);
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b < 0) {
            end();
        } else {
            watch(new byte[]{(byte) b}, 0, 1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n < 0) {
            end();
        } else if (n > 0) {
            watch(b, off, n);
        }
        return n;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            end();
        }
    }

    private void watch(byte[] b, int off, int len) {
        try {
            int i = off;
            int stop = off + len;
            while (i < stop) {
                if (remaining < 0) {
                    i = readHeader(b, i, stop);
                } else {
                    int take = Math.min(remaining, stop - i);
                    if (!skipping) {
                        body.write(b, i, take);
                    }
                    remaining -= take;
                    i += take;
                    if (remaining == 0) {
                        if (!skipping) {
                            frame(body.toByteArray());
                        }
                        body = null;
                        skipping = false;
                        remaining = -1;
                    }
                }
            }
        } catch (RuntimeException ex) {
            // the parse must never break the stream it watches
            header.reset();
            body = null;
            remaining = -1;
            skipping = false;
            if (!warned) {
                warned = true;
                LOG.log(Level.INFO, "language server diagnostics not readable for Action Items ({0}): {1}",
                        new Object[]{tool, ex.toString()});
            }
        }
    }

    /** Consumes header bytes; returns the index after the last one taken. */
    private int readHeader(byte[] b, int i, int stop) {
        while (i < stop) {
            header.write(b[i++]);
            int size = header.size();
            if (size >= 4) {
                byte[] h = header.toByteArray();
                if (h[size - 4] == '\r' && h[size - 3] == '\n' && h[size - 2] == '\r' && h[size - 1] == '\n') {
                    int length = contentLength(new String(h, StandardCharsets.US_ASCII));
                    header.reset();
                    if (length < 0) {
                        throw new IllegalStateException("frame without Content-Length");
                    }
                    remaining = length;
                    skipping = length > MAX_BODY;
                    body = skipping ? null : new ByteArrayOutputStream(Math.max(16, length));
                    if (length == 0) {
                        remaining = -1;
                        body = null;
                        skipping = false;
                    }
                    return i;
                }
            }
            if (size > MAX_HEADER) {
                throw new IllegalStateException("header over " + MAX_HEADER + " bytes");
            }
        }
        return i;
    }

    static int contentLength(String headers) {
        for (String line : headers.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    return Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException ex) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private void frame(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.contains("textDocument/publishDiagnostics")) {
            return;
        }
        JSONObject message = new JSONObject(text);
        if (!"textDocument/publishDiagnostics".equals(message.optString("method"))) {
            return;
        }
        JSONObject params = message.optJSONObject("params");
        if (params == null) {
            return;
        }
        String uri = params.optString("uri", "");
        File file = fileOf(uri);
        if (file == null) {
            return;
        }
        List<DiagnosticsBus.Problem> problems = problemsOf(file, params.optJSONArray("diagnostics"));
        record(this, tool, uri, problems);
    }

    /** A {@code file:} URI as a file; any other scheme has no row to show. */
    static File fileOf(String uri) {
        if (!uri.startsWith("file:")) {
            return null;
        }
        try {
            return new File(java.net.URI.create(uri));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Errors and warnings, as Action Items rows. Information and hints are
     * left to the editor: the list is for what needs doing. A diagnostic
     * with no severity is an error, as VS Code reads it.
     */
    static List<DiagnosticsBus.Problem> problemsOf(File file, JSONArray diagnostics) {
        List<DiagnosticsBus.Problem> out = new ArrayList<>();
        if (diagnostics == null) {
            return out;
        }
        for (int i = 0; i < diagnostics.length() && out.size() < MAX_PER_FILE; i++) {
            JSONObject d = diagnostics.optJSONObject(i);
            if (d == null) {
                continue;
            }
            int severity = d.optInt("severity", 1);
            if (severity != 1 && severity != 2) {
                continue;
            }
            JSONObject start = d.optJSONObject("range") == null ? null
                    : d.getJSONObject("range").optJSONObject("start");
            int line = start == null ? 1 : Math.max(0, start.optInt("line", 0)) + 1;
            String message = d.optString("message", "").strip();
            int newline = message.indexOf('\n');
            if (newline > 0) {
                message = message.substring(0, newline).strip();
            }
            if (message.isEmpty()) {
                continue;
            }
            String code = d.opt("code") == null ? "" : String.valueOf(d.opt("code"));
            if (!code.isEmpty() && !"null".equals(code)) {
                message = message + " (" + code + ")";
            }
            out.add(new DiagnosticsBus.Problem(file, line, message, severity == 1));
        }
        return out;
    }

    private void end() {
        if (ended) {
            return;
        }
        ended = true;
        synchronized (STATE) {
            Map<DiagnosticsTap, Map<String, List<DiagnosticsBus.Problem>>> taps = STATE.get(tool);
            if (taps != null && taps.remove(this) != null) {
                DIRTY.add(tool);
            }
        }
        FLUSH.schedule(0);
    }

    static void record(DiagnosticsTap tap, String tool, String uri, List<DiagnosticsBus.Problem> problems) {
        synchronized (STATE) {
            Map<String, List<DiagnosticsBus.Problem>> files = STATE
                    .computeIfAbsent(tool, t -> new LinkedHashMap<>())
                    .computeIfAbsent(tap, t -> new LinkedHashMap<>());
            if (problems.isEmpty()) {
                if (files.remove(uri) == null) {
                    return;
                }
            } else {
                files.put(uri, List.copyOf(problems));
            }
            DIRTY.add(tool);
        }
        FLUSH.schedule(0);
    }

    private static void flush() {
        Map<String, List<DiagnosticsBus.Problem>> batches = new LinkedHashMap<>();
        synchronized (STATE) {
            for (String tool : DIRTY) {
                List<DiagnosticsBus.Problem> all = new ArrayList<>();
                Map<DiagnosticsTap, Map<String, List<DiagnosticsBus.Problem>>> taps = STATE.get(tool);
                if (taps != null) {
                    outer:
                    for (Map<String, List<DiagnosticsBus.Problem>> files : taps.values()) {
                        for (List<DiagnosticsBus.Problem> ps : files.values()) {
                            for (DiagnosticsBus.Problem p : ps) {
                                if (all.size() >= MAX_PER_TOOL) {
                                    break outer;
                                }
                                all.add(p);
                            }
                        }
                    }
                }
                batches.put(tool, all);
            }
            DIRTY.clear();
        }
        batches.forEach(DiagnosticsBus::publish);
    }

    /** Test barrier: every scheduled publish has reached the bus. */
    static void awaitIdle() {
        FLUSH.waitFinished();
        LANE.post(() -> { }).waitFinished();
    }
}
