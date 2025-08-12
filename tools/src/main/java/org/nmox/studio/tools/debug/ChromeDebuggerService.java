package org.nmox.studio.tools.debug;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.websocket.*;
import org.openide.util.lookup.ServiceProvider;

/**
 * Chrome DevTools Protocol implementation for JavaScript debugging.
 */
@ServiceProvider(service = DebuggerService.class)
public class ChromeDebuggerService implements DebuggerService {
    
    private final Map<String, ChromeDebugSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    @Override
    public CompletableFuture<DebugSession> startDebugSession(DebugConfiguration config) {
        if ("launch".equals(config.getRequest())) {
            return launch(URI.create(config.getUrl()), 
                LaunchConfiguration.builder()
                    .browser(config.getType())
                    .build());
        } else {
            return attach("localhost", config.getPort());
        }
    }
    
    @Override
    public CompletableFuture<DebugSession> attach(String host, int port) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get debugging endpoint from Chrome
                URL url = new URL("http://" + host + ":" + port + "/json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // Parse JSON response to get WebSocket debugger URL
                String wsUrl = parseWebSocketUrl(response.toString());
                
                // Create debug session
                ChromeDebugSession session = new ChromeDebugSession(wsUrl);
                session.connect();
                
                sessions.put(session.getId(), session);
                return session;
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to attach to Chrome", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<DebugSession> launch(URI url, LaunchConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build Chrome launch command
                List<String> command = new ArrayList<>();
                
                // Find Chrome executable
                String chromePath = findChrome(config.getBrowser());
                command.add(chromePath);
                
                // Add debugging flags
                command.add("--remote-debugging-port=9222");
                command.add("--no-first-run");
                command.add("--no-default-browser-check");
                
                if (config.getUserDataDir() != null) {
                    command.add("--user-data-dir=" + config.getUserDataDir());
                }
                
                if (config.isHeadless()) {
                    command.add("--headless");
                }
                
                command.add("--window-size=" + config.getWidth() + "," + config.getHeight());
                
                // Add custom args
                if (config.getArgs() != null) {
                    command.addAll(Arrays.asList(config.getArgs()));
                }
                
                // Add URL
                command.add(url.toString());
                
                // Launch Chrome
                ProcessBuilder pb = new ProcessBuilder(command);
                Process process = pb.start();
                
                // Wait for Chrome to start
                Thread.sleep(2000);
                
                // Attach to the launched instance
                return attach("localhost", 9222).get();
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to launch Chrome", e);
            }
        }, executor);
    }
    
    @Override
    public List<DebugSession> getActiveSessions() {
        return new ArrayList<>(sessions.values());
    }
    
    @Override
    public void stopSession(String sessionId) {
        ChromeDebugSession session = sessions.remove(sessionId);
        if (session != null) {
            session.disconnect();
        }
    }
    
    @Override
    public void stopAllSessions() {
        sessions.values().forEach(ChromeDebugSession::disconnect);
        sessions.clear();
    }
    
    private String findChrome(String browser) {
        // Platform-specific Chrome paths
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("mac")) {
            if ("edge".equals(browser)) {
                return "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge";
            }
            return "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
        } else if (os.contains("win")) {
            if ("edge".equals(browser)) {
                return "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
            }
            return "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
        } else {
            // Linux
            if ("edge".equals(browser)) {
                return "microsoft-edge";
            }
            return "google-chrome";
        }
    }
    
    private String parseWebSocketUrl(String json) {
        // Simple JSON parsing for WebSocket URL
        int start = json.indexOf("\"webSocketDebuggerUrl\":\"") + 24;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
    
    /**
     * Chrome debug session implementation.
     */
    private class ChromeDebugSession implements DebugSession {
        private final String id = UUID.randomUUID().toString();
        private final String wsUrl;
        private Session wsSession;
        private DebugState state = DebugState.CONNECTING;
        private final Map<String, Breakpoint> breakpoints = new ConcurrentHashMap<>();
        private final List<StackFrame> callStack = new ArrayList<>();
        private final Map<String, Variable> variables = new HashMap<>();
        private final List<WatchExpression> watches = new ArrayList<>();
        private final List<DebugEventListener> listeners = new CopyOnWriteArrayList<>();
        private int messageId = 1;
        private final Map<Integer, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();
        
        public ChromeDebugSession(String wsUrl) {
            this.wsUrl = wsUrl;
        }
        
        public void connect() throws Exception {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
            
            wsSession = container.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            handleMessage(message);
                        }
                    });
                    state = DebugState.CONNECTED;
                    notifyStateChanged(state);
                    
                    // Enable necessary domains
                    sendCommand("Debugger.enable", null);
                    sendCommand("Runtime.enable", null);
                    sendCommand("Console.enable", null);
                    sendCommand("Network.enable", null);
                }
                
                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    state = DebugState.DISCONNECTED;
                    notifyStateChanged(state);
                }
                
                @Override
                public void onError(Session session, Throwable throwable) {
                    throwable.printStackTrace();
                }
            }, config, URI.create(wsUrl));
        }
        
        private CompletableFuture<String> sendCommand(String method, Map<String, Object> params) {
            CompletableFuture<String> future = new CompletableFuture<>();
            int id = messageId++;
            
            Map<String, Object> message = new HashMap<>();
            message.put("id", id);
            message.put("method", method);
            if (params != null) {
                message.put("params", params);
            }
            
            pendingResponses.put(id, future);
            
            try {
                wsSession.getBasicRemote().sendText(toJson(message));
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
            
            return future;
        }
        
        private void handleMessage(String message) {
            Map<String, Object> msg = parseJson(message);
            
            if (msg.containsKey("id")) {
                // Response to a command
                int id = (Integer) msg.get("id");
                CompletableFuture<String> future = pendingResponses.remove(id);
                if (future != null) {
                    future.complete(message);
                }
            } else if (msg.containsKey("method")) {
                // Event notification
                String method = (String) msg.get("method");
                Map<String, Object> params = (Map<String, Object>) msg.get("params");
                handleEvent(method, params);
            }
        }
        
        private void handleEvent(String method, Map<String, Object> params) {
            switch (method) {
                case "Debugger.paused":
                    state = DebugState.PAUSED;
                    notifyStateChanged(state);
                    updateCallStack(params);
                    break;
                    
                case "Debugger.resumed":
                    state = DebugState.RUNNING;
                    notifyStateChanged(state);
                    break;
                    
                case "Console.messageAdded":
                    Map<String, Object> message = (Map<String, Object>) params.get("message");
                    String text = (String) message.get("text");
                    String level = (String) message.get("level");
                    notifyConsoleOutput(text, level);
                    break;
                    
                case "Debugger.breakpointResolved":
                    String breakpointId = (String) params.get("breakpointId");
                    Breakpoint bp = breakpoints.get(breakpointId);
                    if (bp != null) {
                        bp.setVerified(true);
                    }
                    break;
            }
        }
        
        private void updateCallStack(Map<String, Object> params) {
            callStack.clear();
            List<Map<String, Object>> frames = (List<Map<String, Object>>) params.get("callFrames");
            
            for (Map<String, Object> frame : frames) {
                String frameId = (String) frame.get("callFrameId");
                String functionName = (String) frame.get("functionName");
                Map<String, Object> location = (Map<String, Object>) frame.get("location");
                String scriptId = (String) location.get("scriptId");
                int lineNumber = (Integer) location.get("lineNumber");
                int columnNumber = (Integer) location.get("columnNumber");
                
                StackFrame stackFrame = new StackFrame(
                    frameId, functionName, scriptId, lineNumber, columnNumber, new HashMap<>()
                );
                callStack.add(stackFrame);
            }
        }
        
        @Override
        public String getId() {
            return id;
        }
        
        @Override
        public String getName() {
            return "Chrome Debug Session";
        }
        
        @Override
        public DebugState getState() {
            return state;
        }
        
        @Override
        public CompletableFuture<Breakpoint> setBreakpoint(String file, int line) {
            return setBreakpoint(file, line, null);
        }
        
        @Override
        public CompletableFuture<Breakpoint> setBreakpoint(String file, int line, String condition) {
            return CompletableFuture.supplyAsync(() -> {
                Map<String, Object> params = new HashMap<>();
                params.put("lineNumber", line);
                params.put("url", file);
                if (condition != null) {
                    params.put("condition", condition);
                }
                
                try {
                    String response = sendCommand("Debugger.setBreakpointByUrl", params).get();
                    Map<String, Object> result = parseJson(response);
                    String breakpointId = (String) ((Map<String, Object>) result.get("result")).get("breakpointId");
                    
                    Breakpoint bp = new Breakpoint(breakpointId, file, line, condition);
                    breakpoints.put(breakpointId, bp);
                    return bp;
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to set breakpoint", e);
                }
            }, executor);
        }
        
        @Override
        public CompletableFuture<Void> removeBreakpoint(String breakpointId) {
            return CompletableFuture.runAsync(() -> {
                Map<String, Object> params = new HashMap<>();
                params.put("breakpointId", breakpointId);
                
                try {
                    sendCommand("Debugger.removeBreakpoint", params).get();
                    breakpoints.remove(breakpointId);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to remove breakpoint", e);
                }
            }, executor);
        }
        
        @Override
        public List<Breakpoint> getBreakpoints() {
            return new ArrayList<>(breakpoints.values());
        }
        
        @Override
        public CompletableFuture<Void> resume() {
            return CompletableFuture.runAsync(() -> {
                try {
                    sendCommand("Debugger.resume", null).get();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to resume", e);
                }
            }, executor);
        }
        
        @Override
        public CompletableFuture<Void> stepOver() {
            return CompletableFuture.runAsync(() -> {
                try {
                    sendCommand("Debugger.stepOver", null).get();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to step over", e);
                }
            }, executor);
        }
        
        @Override
        public CompletableFuture<Void> stepInto() {
            return CompletableFuture.runAsync(() -> {
                try {
                    sendCommand("Debugger.stepInto", null).get();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to step into", e);
                }
            }, executor);
        }
        
        @Override
        public CompletableFuture<Void> stepOut() {
            return CompletableFuture.runAsync(() -> {
                try {
                    sendCommand("Debugger.stepOut", null).get();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to step out", e);
                }
            }, executor);
        }
        
        @Override
        public CompletableFuture<Void> pause() {
            return CompletableFuture.runAsync(() -> {
                try {
                    sendCommand("Debugger.pause", null).get();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to pause", e);
                }
            }, executor);
        }
        
        @Override
        public CompletableFuture<EvaluationResult> evaluate(String expression) {
            return CompletableFuture.supplyAsync(() -> {
                Map<String, Object> params = new HashMap<>();
                params.put("expression", expression);
                params.put("includeCommandLineAPI", true);
                
                try {
                    String response = sendCommand("Runtime.evaluate", params).get();
                    Map<String, Object> result = parseJson(response);
                    Map<String, Object> evalResult = (Map<String, Object>) result.get("result");
                    
                    String value = String.valueOf(evalResult.get("value"));
                    String type = (String) evalResult.get("type");
                    boolean success = !(Boolean) evalResult.getOrDefault("wasThrown", false);
                    String error = success ? null : (String) evalResult.get("description");
                    
                    return new EvaluationResult(value, type, success, error);
                    
                } catch (Exception e) {
                    return new EvaluationResult(null, null, false, e.getMessage());
                }
            }, executor);
        }
        
        @Override
        public List<StackFrame> getCallStack() {
            return new ArrayList<>(callStack);
        }
        
        @Override
        public Map<String, Variable> getVariables() {
            return new HashMap<>(variables);
        }
        
        @Override
        public void addWatch(String expression) {
            watches.add(new WatchExpression(expression));
        }
        
        @Override
        public void removeWatch(String expression) {
            watches.removeIf(w -> w.getExpression().equals(expression));
        }
        
        @Override
        public List<WatchExpression> getWatches() {
            return new ArrayList<>(watches);
        }
        
        @Override
        public void disconnect() {
            try {
                if (wsSession != null && wsSession.isOpen()) {
                    wsSession.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        @Override
        public void addListener(DebugEventListener listener) {
            listeners.add(listener);
        }
        
        @Override
        public void removeListener(DebugEventListener listener) {
            listeners.remove(listener);
        }
        
        private void notifyStateChanged(DebugState newState) {
            for (DebugEventListener listener : listeners) {
                listener.onStateChanged(newState);
            }
        }
        
        private void notifyConsoleOutput(String message, String level) {
            for (DebugEventListener listener : listeners) {
                listener.onConsoleOutput(message, level);
            }
        }
        
        private String toJson(Map<String, Object> map) {
            // Simple JSON serialization
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                
                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(value).append("\"");
                } else if (value instanceof Map) {
                    json.append(toJson((Map<String, Object>) value));
                } else {
                    json.append(value);
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        }
        
        private Map<String, Object> parseJson(String json) {
            // Simple JSON parsing - in real implementation use a JSON library
            Map<String, Object> result = new HashMap<>();
            // Simplified parsing logic
            return result;
        }
    }
}