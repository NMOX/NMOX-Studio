package org.nmox.studio.tools.preview;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.Executors;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = LivePreviewServer.class)
public class LivePreviewServer {

    private HttpServer server;
    private WatchService watchService;
    private Thread watchThread;
    private File rootDirectory;
    private int port = 8080;
    private boolean running = false;

    public void start(File rootDir) throws IOException {
        if (running) {
            stop();
        }

        this.rootDirectory = rootDir;
        
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/ws", new WebSocketHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        startFileWatcher();
        running = true;
        
        System.out.println("Live preview server started at http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            watchService = null;
        }
        
        running = false;
        System.out.println("Live preview server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUrl() {
        return "http://localhost:" + port;
    }

    private void startFileWatcher() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        Path rootPath = rootDirectory.toPath();
        
        Files.walk(rootPath)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        dir.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        watchThread = new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        notifyClients();
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private void notifyClients() {
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            File file = new File(rootDirectory, path);
            
            if (!file.exists() || !file.isFile()) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            String contentType = getContentType(file.getName());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            
            if (contentType.startsWith("text/html")) {
                String content = new String(Files.readAllBytes(file.toPath()));
                content = injectLiveReloadScript(content);
                byte[] bytes = content.getBytes();
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(200, file.length());
                try (FileInputStream fis = new FileInputStream(file);
                     OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }
        }

        private String getContentType(String fileName) {
            if (fileName.endsWith(".html")) return "text/html";
            if (fileName.endsWith(".css")) return "text/css";
            if (fileName.endsWith(".js")) return "application/javascript";
            if (fileName.endsWith(".json")) return "application/json";
            if (fileName.endsWith(".png")) return "image/png";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
            if (fileName.endsWith(".gif")) return "image/gif";
            if (fileName.endsWith(".svg")) return "image/svg+xml";
            return "application/octet-stream";
        }

        private String injectLiveReloadScript(String html) {
            String script = "<script>" +
                    "(function() {" +
                    "  var ws = new WebSocket('ws://localhost:" + port + "/ws');" +
                    "  ws.onmessage = function(event) {" +
                    "    if (event.data === 'reload') {" +
                    "      location.reload();" +
                    "    }" +
                    "  };" +
                    "})();" +
                    "</script>";
            
            int bodyEnd = html.lastIndexOf("</body>");
            if (bodyEnd != -1) {
                return html.substring(0, bodyEnd) + script + html.substring(bodyEnd);
            } else {
                return html + script;
            }
        }
    }

    private class WebSocketHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(501, 0);
            exchange.getResponseBody().close();
        }
    }
}