package org.nmox.studio.core.cache;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance file cache for NMOX Studio.
 * Caches file contents to reduce disk I/O and improve editor responsiveness.
 */
public class FileCache implements AutoCloseable {
    
    private static final Logger LOGGER = Logger.getLogger(FileCache.class.getName());
    private static final FileCache INSTANCE = new FileCache();
    
    private static final long MAX_CACHE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;   // 10MB per file
    private static final long EVICTION_INTERVAL = 60;             // 60 seconds
    private static final long STALE_THRESHOLD = 5 * 60 * 1000;    // 5 minutes
    
    private final Map<Path, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong totalSize = new AtomicLong(0);
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FileCache-Cleanup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    
    private FileCache() {
        cleanupExecutor.scheduleAtFixedRate(this::evictStaleEntries, 
            EVICTION_INTERVAL, EVICTION_INTERVAL, TimeUnit.SECONDS);
    }
    
    public static FileCache getInstance() {
        return INSTANCE;
    }
    
    /**
     * Get file contents from cache or load from disk.
     */
    public Optional<String> get(Path path) {
        Objects.requireNonNull(path, "Path cannot be null");
        
        CacheEntry entry = cache.get(path);
        
        if (entry != null) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                FileTime currentModified = attrs.lastModifiedTime();
                
                if (currentModified.equals(entry.lastModified)) {
                    entry.lastAccess = System.currentTimeMillis();
                    entry.accessCount++;
                    hitCount.incrementAndGet();
                    return Optional.of(entry.content);
                } else {
                    // File has been modified, invalidate cache
                    invalidate(path);
                }
            } catch (IOException e) {
                // File may have been deleted
                invalidate(path);
            }
        }
        
        missCount.incrementAndGet();
        return loadAndCache(path);
    }
    
    /**
     * Get file contents as byte array from cache or load from disk.
     */
    public Optional<byte[]> getBytes(Path path) {
        Objects.requireNonNull(path, "Path cannot be null");
        
        Optional<String> content = get(path);
        return content.map(s -> s.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Preload a file into cache.
     */
    public void preload(Path path) {
        loadAndCache(path);
    }
    
    /**
     * Invalidate a cached file.
     */
    public void invalidate(Path path) {
        CacheEntry removed = cache.remove(path);
        if (removed != null) {
            totalSize.addAndGet(-removed.size);
            LOGGER.log(Level.FINE, "Invalidated cache for: {0}", path);
        }
    }
    
    /**
     * Clear all cached files.
     */
    public void clear() {
        cache.clear();
        totalSize.set(0);
        LOGGER.info("File cache cleared");
    }
    
    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0;
        
        return new CacheStats(
            cache.size(),
            totalSize.get(),
            hits,
            misses,
            hitRate
        );
    }
    
    private Optional<String> loadAndCache(Path path) {
        try {
            File file = path.toFile();
            if (!file.exists() || !file.isFile()) {
                return Optional.empty();
            }
            
            long fileSize = file.length();
            if (fileSize > MAX_FILE_SIZE) {
                LOGGER.log(Level.FINE, "File too large for cache: {0} ({1} bytes)", 
                    new Object[]{path, fileSize});
                return Optional.empty();
            }
            
            // Check if adding this file would exceed cache size limit
            if (totalSize.get() + fileSize > MAX_CACHE_SIZE) {
                evictLRU(fileSize);
            }
            
            String content = Files.readString(path, StandardCharsets.UTF_8);
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            
            CacheEntry entry = new CacheEntry(
                content,
                attrs.lastModifiedTime(),
                fileSize
            );
            
            CacheEntry previous = cache.put(path, entry);
            if (previous != null) {
                totalSize.addAndGet(-previous.size);
            }
            totalSize.addAndGet(fileSize);
            
            LOGGER.log(Level.FINE, "Cached file: {0} ({1} bytes)", 
                new Object[]{path, fileSize});
            
            return Optional.of(content);
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load file: " + path, e);
            return Optional.empty();
        }
    }
    
    private void evictStaleEntries() {
        long now = System.currentTimeMillis();
        long evictedCount = 0;
        long evictedSize = 0;
        
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            CacheEntry cacheEntry = entry.getValue();
            
            if (now - cacheEntry.lastAccess > STALE_THRESHOLD) {
                iterator.remove();
                evictedCount++;
                evictedSize += cacheEntry.size;
            }
        }
        
        if (evictedCount > 0) {
            totalSize.addAndGet(-evictedSize);
            LOGGER.log(Level.FINE, "Evicted {0} stale entries ({1} bytes)", 
                new Object[]{evictedCount, evictedSize});
        }
    }
    
    private void evictLRU(long requiredSpace) {
        // Sort entries by last access time and evict oldest until we have enough space
        cache.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e1.getValue().lastAccess, e2.getValue().lastAccess))
            .takeWhile(e -> totalSize.get() + requiredSpace > MAX_CACHE_SIZE)
            .forEach(e -> {
                cache.remove(e.getKey());
                totalSize.addAndGet(-e.getValue().size);
            });
    }
    
    @Override
    public void close() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        clear();
    }
    
    /**
     * Cache entry holding file content and metadata.
     */
    private static class CacheEntry {
        final String content;
        final FileTime lastModified;
        final long size;
        volatile long lastAccess;
        volatile int accessCount;
        
        CacheEntry(String content, FileTime lastModified, long size) {
            this.content = content;
            this.lastModified = lastModified;
            this.size = size;
            this.lastAccess = System.currentTimeMillis();
            this.accessCount = 0;
        }
    }
    
    /**
     * Cache statistics.
     */
    public static class CacheStats {
        public final int entryCount;
        public final long totalSize;
        public final long hitCount;
        public final long missCount;
        public final double hitRate;
        
        private CacheStats(int entryCount, long totalSize, long hitCount, long missCount, double hitRate) {
            this.entryCount = entryCount;
            this.totalSize = totalSize;
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats[entries=%d, size=%s, hits=%d, misses=%d, hitRate=%.2f%%]",
                entryCount, formatBytes(totalSize), hitCount, missCount, hitRate * 100);
        }
        
        private static String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
    }
}