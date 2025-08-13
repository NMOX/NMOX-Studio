package org.nmox.studio.core.resources;

import java.io.Closeable;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Comprehensive resource manager for NMOX Studio.
 * Ensures proper cleanup of resources and prevents memory leaks.
 */
public class ResourceManager implements AutoCloseable {
    
    private static final Logger LOGGER = Logger.getLogger(ResourceManager.class.getName());
    private static final ResourceManager INSTANCE = new ResourceManager();
    
    private final Map<String, ManagedResource> resources = new ConcurrentHashMap<>();
    private final Set<AutoCloseable> trackedResources = Collections.newSetFromMap(new WeakHashMap<>());
    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private final Map<PhantomReference<?>, Runnable> cleanupActions = new ConcurrentHashMap<>();
    
    private final AtomicLong resourceIdCounter = new AtomicLong(0);
    private final AtomicLong leakCount = new AtomicLong(0);
    
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "ResourceManager-Cleanup");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    
    private volatile boolean shutdown = false;
    
    private ResourceManager() {
        // Start reference queue processor
        cleanupExecutor.scheduleWithFixedDelay(this::processReferenceQueue, 0, 5, TimeUnit.SECONDS);
        
        // Start periodic resource audit
        cleanupExecutor.scheduleWithFixedDelay(this::auditResources, 30, 30, TimeUnit.SECONDS);
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook, "ResourceManager-Shutdown"));
    }
    
    public static ResourceManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register a resource for management.
     */
    public <T extends AutoCloseable> T register(T resource) {
        return register(resource, "Resource-" + resourceIdCounter.incrementAndGet());
    }
    
    /**
     * Register a named resource for management.
     */
    public <T extends AutoCloseable> T register(T resource, String name) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource cannot be null");
        }
        
        if (shutdown) {
            throw new IllegalStateException("ResourceManager is shutting down");
        }
        
        ManagedResource managed = new ManagedResource(name, resource);
        ManagedResource previous = resources.put(name, managed);
        
        if (previous != null) {
            closeQuietly(previous.resource);
        }
        
        trackedResources.add(resource);
        
        LOGGER.log(Level.FINE, "Registered resource: {0}", name);
        return resource;
    }
    
    /**
     * Register a cleanup action to be executed when an object is garbage collected.
     */
    public void registerCleanup(Object owner, Runnable cleanupAction) {
        if (owner == null || cleanupAction == null) {
            throw new IllegalArgumentException("Owner and cleanup action cannot be null");
        }
        
        PhantomReference<Object> ref = new PhantomReference<>(owner, referenceQueue);
        cleanupActions.put(ref, cleanupAction);
    }
    
    /**
     * Unregister and close a resource.
     */
    public void unregister(String name) {
        ManagedResource managed = resources.remove(name);
        if (managed != null) {
            closeQuietly(managed.resource);
            trackedResources.remove(managed.resource);
            LOGGER.log(Level.FINE, "Unregistered resource: {0}", name);
        }
    }
    
    /**
     * Get a registered resource by name.
     */
    @SuppressWarnings("unchecked")
    public <T extends AutoCloseable> T getResource(String name, Class<T> type) {
        ManagedResource managed = resources.get(name);
        if (managed != null && type.isInstance(managed.resource)) {
            managed.lastAccess = System.currentTimeMillis();
            return (T) managed.resource;
        }
        return null;
    }
    
    /**
     * Check if a resource is registered.
     */
    public boolean hasResource(String name) {
        return resources.containsKey(name);
    }
    
    /**
     * Close all resources matching a pattern.
     */
    public void closeResourcesMatching(String pattern) {
        resources.entrySet().stream()
            .filter(e -> e.getKey().matches(pattern))
            .forEach(e -> unregister(e.getKey()));
    }
    
    /**
     * Get resource statistics.
     */
    public ResourceStats getStats() {
        return new ResourceStats(
            resources.size(),
            trackedResources.size(),
            leakCount.get()
        );
    }
    
    /**
     * Force cleanup of leaked resources.
     */
    public void forceCleanup() {
        processReferenceQueue();
        auditResources();
        System.gc();
        System.runFinalization();
        processReferenceQueue();
    }
    
    private void processReferenceQueue() {
        PhantomReference<?> ref;
        int cleaned = 0;
        
        while ((ref = (PhantomReference<?>) referenceQueue.poll()) != null) {
            Runnable cleanupAction = cleanupActions.remove(ref);
            if (cleanupAction != null) {
                try {
                    cleanupAction.run();
                    cleaned++;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error executing cleanup action", e);
                }
            }
            ref.clear();
        }
        
        if (cleaned > 0) {
            LOGGER.log(Level.FINE, "Executed {0} cleanup actions", cleaned);
        }
    }
    
    private void auditResources() {
        long now = System.currentTimeMillis();
        long staleThreshold = 5 * 60 * 1000; // 5 minutes
        
        resources.values().stream()
            .filter(r -> now - r.created > staleThreshold && r.lastAccess == r.created)
            .forEach(r -> {
                LOGGER.log(Level.WARNING, "Potential resource leak detected: {0} (created {1}ms ago, never accessed)",
                    new Object[]{r.name, now - r.created});
                leakCount.incrementAndGet();
            });
    }
    
    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing resource", e);
            }
        }
    }
    
    private void shutdownHook() {
        LOGGER.info("ResourceManager shutdown hook executing");
        try {
            close();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during ResourceManager shutdown", e);
        }
    }
    
    @Override
    public void close() {
        if (shutdown) {
            return;
        }
        
        shutdown = true;
        LOGGER.info("Shutting down ResourceManager");
        
        // Stop cleanup executor
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Close all managed resources
        int closedCount = 0;
        for (ManagedResource managed : resources.values()) {
            closeQuietly(managed.resource);
            closedCount++;
        }
        
        // Close all tracked resources
        for (AutoCloseable resource : trackedResources) {
            closeQuietly(resource);
            closedCount++;
        }
        
        // Execute remaining cleanup actions
        processReferenceQueue();
        for (Runnable action : cleanupActions.values()) {
            try {
                action.run();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error executing cleanup action during shutdown", e);
            }
        }
        
        resources.clear();
        trackedResources.clear();
        cleanupActions.clear();
        
        LOGGER.log(Level.INFO, "ResourceManager shutdown complete. Closed {0} resources", closedCount);
    }
    
    /**
     * Managed resource wrapper.
     */
    private static class ManagedResource {
        final String name;
        final AutoCloseable resource;
        final long created;
        volatile long lastAccess;
        
        ManagedResource(String name, AutoCloseable resource) {
            this.name = name;
            this.resource = resource;
            this.created = System.currentTimeMillis();
            this.lastAccess = created;
        }
    }
    
    /**
     * Resource statistics.
     */
    public static class ResourceStats {
        public final int managedCount;
        public final int trackedCount;
        public final long leakCount;
        
        private ResourceStats(int managedCount, int trackedCount, long leakCount) {
            this.managedCount = managedCount;
            this.trackedCount = trackedCount;
            this.leakCount = leakCount;
        }
        
        @Override
        public String toString() {
            return String.format("ResourceStats[managed=%d, tracked=%d, leaks=%d]",
                managedCount, trackedCount, leakCount);
        }
    }
    
    /**
     * Resource scope for automatic resource management.
     */
    public static class ResourceScope implements AutoCloseable {
        private final Set<AutoCloseable> scopeResources = Collections.newSetFromMap(new WeakHashMap<>());
        
        public <T extends AutoCloseable> T add(T resource) {
            scopeResources.add(resource);
            return resource;
        }
        
        @Override
        public void close() {
            for (AutoCloseable resource : scopeResources) {
                try {
                    resource.close();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error closing scoped resource", e);
                }
            }
            scopeResources.clear();
        }
    }
}