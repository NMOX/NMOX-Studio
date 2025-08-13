package org.nmox.studio.core.cache;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class FileCacheTest {
    
    private FileCache cache;
    private Path tempDir;
    private Path testFile;
    
    @BeforeEach
    public void setUp() throws IOException {
        cache = FileCache.getInstance();
        cache.clear();
        
        tempDir = Files.createTempDirectory("filecache-test");
        testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Test content");
    }
    
    @AfterEach
    public void tearDown() throws IOException {
        cache.clear();
        
        if (Files.exists(testFile)) {
            Files.delete(testFile);
        }
        if (Files.exists(tempDir)) {
            Files.delete(tempDir);
        }
    }
    
    @Test
    public void testCacheHit() throws IOException {
        // Get initial stats
        FileCache.CacheStats initialStats = cache.getStats();
        long initialHits = initialStats.hitCount;
        long initialMisses = initialStats.missCount;
        
        // First access - cache miss
        Optional<String> content1 = cache.get(testFile);
        assertTrue(content1.isPresent());
        assertEquals("Test content", content1.get());
        
        FileCache.CacheStats stats1 = cache.getStats();
        assertEquals(initialHits, stats1.hitCount);
        assertEquals(initialMisses + 1, stats1.missCount);
        
        // Second access - cache hit
        Optional<String> content2 = cache.get(testFile);
        assertTrue(content2.isPresent());
        assertEquals("Test content", content2.get());
        
        FileCache.CacheStats stats2 = cache.getStats();
        assertEquals(initialHits + 1, stats2.hitCount);
        assertEquals(initialMisses + 1, stats2.missCount);
    }
    
    @Test
    public void testCacheInvalidation() throws IOException, InterruptedException {
        // Load into cache
        cache.get(testFile);
        
        // Modify file
        Thread.sleep(10); // Ensure different modification time
        Files.writeString(testFile, "Modified content");
        
        // Should detect modification and reload
        Optional<String> content = cache.get(testFile);
        assertTrue(content.isPresent());
        assertEquals("Modified content", content.get());
    }
    
    @Test
    public void testPreload() throws IOException {
        cache.preload(testFile);
        
        FileCache.CacheStats stats = cache.getStats();
        assertEquals(1, stats.entryCount);
        
        // Should be a cache hit
        Optional<String> content = cache.get(testFile);
        assertTrue(content.isPresent());
        assertEquals("Test content", content.get());
        
        stats = cache.getStats();
        assertEquals(1, stats.hitCount);
    }
    
    @Test
    public void testInvalidate() throws IOException {
        cache.get(testFile);
        
        FileCache.CacheStats stats1 = cache.getStats();
        assertEquals(1, stats1.entryCount);
        
        cache.invalidate(testFile);
        
        FileCache.CacheStats stats2 = cache.getStats();
        assertEquals(0, stats2.entryCount);
    }
    
    @Test
    public void testGetBytes() throws IOException {
        Optional<byte[]> bytes = cache.getBytes(testFile);
        assertTrue(bytes.isPresent());
        assertEquals("Test content", new String(bytes.get()));
    }
    
    @Test
    public void testNonExistentFile() {
        Path nonExistent = tempDir.resolve("nonexistent.txt");
        Optional<String> content = cache.get(nonExistent);
        assertFalse(content.isPresent());
    }
    
    @Test
    public void testMultipleFiles() throws IOException {
        Path file2 = tempDir.resolve("test2.txt");
        Path file3 = tempDir.resolve("test3.txt");
        
        Files.writeString(file2, "Content 2");
        Files.writeString(file3, "Content 3");
        
        try {
            cache.get(testFile);
            cache.get(file2);
            cache.get(file3);
            
            FileCache.CacheStats stats = cache.getStats();
            assertEquals(3, stats.entryCount);
            assertTrue(stats.totalSize > 0);
            
        } finally {
            Files.deleteIfExists(file2);
            Files.deleteIfExists(file3);
        }
    }
    
    @Test
    public void testCacheStats() throws IOException {
        // Get initial stats
        FileCache.CacheStats initialStats = cache.getStats();
        long initialHits = initialStats.hitCount;
        long initialMisses = initialStats.missCount;
        int initialEntries = initialStats.entryCount;
        
        cache.get(testFile); // miss
        cache.get(testFile); // hit
        cache.get(testFile); // hit
        
        FileCache.CacheStats stats = cache.getStats();
        assertEquals(initialEntries + 1, stats.entryCount);
        assertEquals(initialHits + 2, stats.hitCount);
        assertEquals(initialMisses + 1, stats.missCount);
        
        // Verify we got the expected number of hits and misses for our operations
        long ourHits = stats.hitCount - initialHits;
        long ourMisses = stats.missCount - initialMisses;
        assertEquals(2, ourHits);
        assertEquals(1, ourMisses);
        
        String statsStr = stats.toString();
        assertTrue(statsStr.contains("entries=" + stats.entryCount));
        assertTrue(statsStr.contains("hits=" + stats.hitCount));
        assertTrue(statsStr.contains("misses=" + stats.missCount));
    }
}