package org.nmox.studio.editor.index;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * High-performance code indexing service for NMOX Studio.
 * Provides fast symbol search, go-to-definition, and find-all-references capabilities.
 */
public class CodeIndexService implements AutoCloseable {
    
    private static final Logger LOGGER = Logger.getLogger(CodeIndexService.class.getName());
    private static final CodeIndexService INSTANCE = new CodeIndexService();
    
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".java", ".js", ".ts", ".jsx", ".tsx", ".css", ".html", ".xml", ".json", 
        ".py", ".go", ".rs", ".cpp", ".c", ".h", ".hpp"
    );
    
    private final ConcurrentHashMap<Path, FileIndex> fileIndices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Symbol>> symbolTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Reference>> referenceTable = new ConcurrentHashMap<>();
    
    private final ExecutorService indexingExecutor = new ThreadPoolExecutor(
        2, 4, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        r -> {
            Thread t = new Thread(r, "CodeIndex-Worker");
            t.setDaemon(true);
            return t;
        }
    );
    
    private final ScheduledExecutorService watcherExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CodeIndex-Watcher");
        t.setDaemon(true);
        return t;
    });
    
    private final AtomicInteger indexedFiles = new AtomicInteger(0);
    private final AtomicLong indexingTime = new AtomicLong(0);
    private WatchService watchService;
    private volatile boolean watching = false;
    
    private CodeIndexService() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create watch service", e);
        }
    }
    
    public static CodeIndexService getInstance() {
        return INSTANCE;
    }
    
    /**
     * Index a project directory.
     */
    public CompletableFuture<IndexResult> indexProject(Path projectPath) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            AtomicInteger fileCount = new AtomicInteger(0);
            AtomicInteger symbolCount = new AtomicInteger(0);
            
            try {
                Files.walkFileTree(projectPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (shouldIndex(file)) {
                            try {
                                FileIndex index = indexFile(file);
                                fileCount.incrementAndGet();
                                symbolCount.addAndGet(index.symbols.size());
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Failed to index file: " + file, e);
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                
                // Start watching for changes
                if (!watching) {
                    startWatching(projectPath);
                }
                
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to walk project tree", e);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            indexingTime.addAndGet(duration);
            
            return new IndexResult(fileCount.get(), symbolCount.get(), duration);
        }, indexingExecutor);
    }
    
    /**
     * Index a single file.
     */
    public FileIndex indexFile(Path file) throws IOException {
        String content = Files.readString(file);
        String fileName = file.getFileName().toString();
        
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> references = new ArrayList<>();
        
        // Extract symbols based on file type
        String extension = getFileExtension(fileName);
        switch (extension) {
            case ".java":
                indexJavaFile(file, content, symbols, references);
                break;
            case ".js":
            case ".jsx":
            case ".ts":
            case ".tsx":
                indexJavaScriptFile(file, content, symbols, references);
                break;
            case ".py":
                indexPythonFile(file, content, symbols, references);
                break;
            default:
                indexGenericFile(file, content, symbols, references);
        }
        
        FileIndex index = new FileIndex(file, symbols, references);
        fileIndices.put(file, index);
        
        // Update symbol and reference tables
        for (Symbol symbol : symbols) {
            symbolTable.computeIfAbsent(symbol.name, k -> ConcurrentHashMap.newKeySet()).add(symbol);
        }
        
        for (Reference ref : references) {
            referenceTable.computeIfAbsent(ref.symbol, k -> ConcurrentHashMap.newKeySet()).add(ref);
        }
        
        indexedFiles.incrementAndGet();
        return index;
    }
    
    /**
     * Find symbol definition.
     */
    public Optional<Symbol> findDefinition(String symbolName) {
        Set<Symbol> symbols = symbolTable.get(symbolName);
        if (symbols != null && !symbols.isEmpty()) {
            // Return the first definition (preferably non-reference)
            return symbols.stream()
                .filter(s -> s.type != SymbolType.REFERENCE)
                .findFirst()
                .or(() -> symbols.stream().findFirst());
        }
        return Optional.empty();
    }
    
    /**
     * Find all references to a symbol.
     */
    public List<Reference> findReferences(String symbolName) {
        Set<Reference> refs = referenceTable.get(symbolName);
        return refs != null ? new ArrayList<>(refs) : Collections.emptyList();
    }
    
    /**
     * Search symbols by pattern.
     */
    public List<Symbol> searchSymbols(String pattern, int maxResults) {
        Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        
        return symbolTable.values().stream()
            .flatMap(Set::stream)
            .filter(s -> regex.matcher(s.name).find())
            .limit(maxResults)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all symbols in a file.
     */
    public List<Symbol> getFileSymbols(Path file) {
        FileIndex index = fileIndices.get(file);
        return index != null ? new ArrayList<>(index.symbols) : Collections.emptyList();
    }
    
    /**
     * Clear index for a file.
     */
    public void clearFileIndex(Path file) {
        FileIndex index = fileIndices.remove(file);
        if (index != null) {
            // Remove symbols from symbol table
            for (Symbol symbol : index.symbols) {
                Set<Symbol> symbols = symbolTable.get(symbol.name);
                if (symbols != null) {
                    symbols.remove(symbol);
                    if (symbols.isEmpty()) {
                        symbolTable.remove(symbol.name);
                    }
                }
            }
            
            // Remove references
            for (Reference ref : index.references) {
                Set<Reference> refs = referenceTable.get(ref.symbol);
                if (refs != null) {
                    refs.remove(ref);
                    if (refs.isEmpty()) {
                        referenceTable.remove(ref.symbol);
                    }
                }
            }
        }
    }
    
    /**
     * Get indexing statistics.
     */
    public IndexStats getStats() {
        return new IndexStats(
            indexedFiles.get(),
            symbolTable.size(),
            referenceTable.size(),
            indexingTime.get()
        );
    }
    
    private void indexJavaFile(Path file, String content, List<Symbol> symbols, List<Reference> references) {
        // Class definitions
        Pattern classPattern = Pattern.compile("(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?class\\s+(\\w+)");
        Matcher classMatcher = classPattern.matcher(content);
        while (classMatcher.find()) {
            symbols.add(new Symbol(classMatcher.group(1), SymbolType.CLASS, file, 
                getLineNumber(content, classMatcher.start())));
        }
        
        // Method definitions
        Pattern methodPattern = Pattern.compile("(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:\\w+(?:<[^>]+>)?\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*\\{");
        Matcher methodMatcher = methodPattern.matcher(content);
        while (methodMatcher.find()) {
            symbols.add(new Symbol(methodMatcher.group(1), SymbolType.METHOD, file,
                getLineNumber(content, methodMatcher.start())));
        }
        
        // Field definitions
        Pattern fieldPattern = Pattern.compile("(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:\\w+(?:<[^>]+>)?\\s+)(\\w+)\\s*[=;]");
        Matcher fieldMatcher = fieldPattern.matcher(content);
        while (fieldMatcher.find()) {
            symbols.add(new Symbol(fieldMatcher.group(1), SymbolType.FIELD, file,
                getLineNumber(content, fieldMatcher.start())));
        }
    }
    
    private void indexJavaScriptFile(Path file, String content, List<Symbol> symbols, List<Reference> references) {
        // Function definitions
        Pattern funcPattern = Pattern.compile("(?:function\\s+(\\w+)|const\\s+(\\w+)\\s*=\\s*(?:async\\s+)?(?:\\([^)]*\\)|\\w+)\\s*=>)");
        Matcher funcMatcher = funcPattern.matcher(content);
        while (funcMatcher.find()) {
            String name = funcMatcher.group(1) != null ? funcMatcher.group(1) : funcMatcher.group(2);
            symbols.add(new Symbol(name, SymbolType.FUNCTION, file,
                getLineNumber(content, funcMatcher.start())));
        }
        
        // Class definitions
        Pattern classPattern = Pattern.compile("class\\s+(\\w+)");
        Matcher classMatcher = classPattern.matcher(content);
        while (classMatcher.find()) {
            symbols.add(new Symbol(classMatcher.group(1), SymbolType.CLASS, file,
                getLineNumber(content, classMatcher.start())));
        }
        
        // Variable definitions
        Pattern varPattern = Pattern.compile("(?:let|const|var)\\s+(\\w+)\\s*=");
        Matcher varMatcher = varPattern.matcher(content);
        while (varMatcher.find()) {
            symbols.add(new Symbol(varMatcher.group(1), SymbolType.VARIABLE, file,
                getLineNumber(content, varMatcher.start())));
        }
    }
    
    private void indexPythonFile(Path file, String content, List<Symbol> symbols, List<Reference> references) {
        // Class definitions
        Pattern classPattern = Pattern.compile("^\\s*class\\s+(\\w+)", Pattern.MULTILINE);
        Matcher classMatcher = classPattern.matcher(content);
        while (classMatcher.find()) {
            symbols.add(new Symbol(classMatcher.group(1), SymbolType.CLASS, file,
                getLineNumber(content, classMatcher.start())));
        }
        
        // Function definitions (including methods)
        Pattern funcPattern = Pattern.compile("^\\s*def\\s+(\\w+)", Pattern.MULTILINE);
        Matcher funcMatcher = funcPattern.matcher(content);
        while (funcMatcher.find()) {
            symbols.add(new Symbol(funcMatcher.group(1), SymbolType.FUNCTION, file,
                getLineNumber(content, funcMatcher.start())));
        }
    }
    
    private void indexGenericFile(Path file, String content, List<Symbol> symbols, List<Reference> references) {
        // Basic pattern matching for common programming constructs
        Pattern pattern = Pattern.compile("\\b(class|function|def|interface|struct|enum)\\s+(\\w+)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            SymbolType symbolType = switch (type) {
                case "class" -> SymbolType.CLASS;
                case "function", "def" -> SymbolType.FUNCTION;
                case "interface" -> SymbolType.INTERFACE;
                case "struct" -> SymbolType.STRUCT;
                case "enum" -> SymbolType.ENUM;
                default -> SymbolType.OTHER;
            };
            symbols.add(new Symbol(name, symbolType, file, getLineNumber(content, matcher.start())));
        }
    }
    
    private int getLineNumber(String content, int position) {
        int line = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
    
    private boolean shouldIndex(Path file) {
        String fileName = file.getFileName().toString();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(lastDot) : "";
    }
    
    private void startWatching(Path path) {
        if (watchService == null || watching) {
            return;
        }
        
        watching = true;
        watcherExecutor.submit(() -> {
            try {
                path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
                
                while (watching) {
                    WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                    if (key != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path eventPath = path.resolve((Path) event.context());
                            
                            if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                clearFileIndex(eventPath);
                            } else if (shouldIndex(eventPath)) {
                                indexingExecutor.submit(() -> {
                                    try {
                                        indexFile(eventPath);
                                    } catch (Exception e) {
                                        LOGGER.log(Level.WARNING, "Failed to reindex file: " + eventPath, e);
                                    }
                                });
                            }
                        }
                        key.reset();
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Watch service error", e);
            }
        });
    }
    
    @Override
    public void close() {
        watching = false;
        
        indexingExecutor.shutdown();
        watcherExecutor.shutdown();
        
        try {
            if (!indexingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                indexingExecutor.shutdownNow();
            }
            if (!watcherExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                watcherExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            indexingExecutor.shutdownNow();
            watcherExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing watch service", e);
            }
        }
    }
    
    /**
     * Symbol representation.
     */
    public static class Symbol {
        public final String name;
        public final SymbolType type;
        public final Path file;
        public final int line;
        
        public Symbol(String name, SymbolType type, Path file, int line) {
            this.name = name;
            this.type = type;
            this.file = file;
            this.line = line;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Symbol symbol = (Symbol) o;
            return line == symbol.line && 
                   Objects.equals(name, symbol.name) && 
                   type == symbol.type && 
                   Objects.equals(file, symbol.file);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(name, type, file, line);
        }
    }
    
    /**
     * Reference to a symbol.
     */
    public static class Reference {
        public final String symbol;
        public final Path file;
        public final int line;
        public final int column;
        
        public Reference(String symbol, Path file, int line, int column) {
            this.symbol = symbol;
            this.file = file;
            this.line = line;
            this.column = column;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Reference reference = (Reference) o;
            return line == reference.line && 
                   column == reference.column && 
                   Objects.equals(symbol, reference.symbol) && 
                   Objects.equals(file, reference.file);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(symbol, file, line, column);
        }
    }
    
    /**
     * File index containing symbols and references.
     */
    public static class FileIndex {
        public final Path file;
        public final List<Symbol> symbols;
        public final List<Reference> references;
        
        public FileIndex(Path file, List<Symbol> symbols, List<Reference> references) {
            this.file = file;
            this.symbols = Collections.unmodifiableList(symbols);
            this.references = Collections.unmodifiableList(references);
        }
    }
    
    /**
     * Symbol types.
     */
    public enum SymbolType {
        CLASS, INTERFACE, ENUM, STRUCT,
        METHOD, FUNCTION,
        FIELD, VARIABLE, CONSTANT,
        REFERENCE, OTHER
    }
    
    /**
     * Index result.
     */
    public static class IndexResult {
        public final int filesIndexed;
        public final int symbolsFound;
        public final long timeMillis;
        
        public IndexResult(int filesIndexed, int symbolsFound, long timeMillis) {
            this.filesIndexed = filesIndexed;
            this.symbolsFound = symbolsFound;
            this.timeMillis = timeMillis;
        }
    }
    
    /**
     * Index statistics.
     */
    public static class IndexStats {
        public final int indexedFiles;
        public final int uniqueSymbols;
        public final int totalReferences;
        public final long totalIndexingTime;
        
        public IndexStats(int indexedFiles, int uniqueSymbols, int totalReferences, long totalIndexingTime) {
            this.indexedFiles = indexedFiles;
            this.uniqueSymbols = uniqueSymbols;
            this.totalReferences = totalReferences;
            this.totalIndexingTime = totalIndexingTime;
        }
    }
}