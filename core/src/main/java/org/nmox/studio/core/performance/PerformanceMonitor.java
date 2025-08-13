package org.nmox.studio.core.performance;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performance monitoring service for NMOX Studio.
 * Tracks memory usage, CPU utilization, and operation timings.
 */
public class PerformanceMonitor implements AutoCloseable {
    
    private static final Logger LOGGER = Logger.getLogger(PerformanceMonitor.class.getName());
    private static final PerformanceMonitor INSTANCE = new PerformanceMonitor();
    
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    
    private final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();
    private final Map<String, TimerContext> activeTimers = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PerformanceMonitor");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    
    private volatile boolean monitoring = false;
    private volatile PerformanceListener listener;
    
    private long lastGcCount = 0;
    private long lastGcTime = 0;
    
    private PerformanceMonitor() {
        if (threadBean.isThreadCpuTimeSupported() && !threadBean.isThreadCpuTimeEnabled()) {
            threadBean.setThreadCpuTimeEnabled(true);
        }
    }
    
    public static PerformanceMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Start monitoring performance metrics.
     */
    public void startMonitoring(long intervalSeconds) {
        if (!monitoring) {
            monitoring = true;
            monitorExecutor.scheduleAtFixedRate(this::collectMetrics, 0, intervalSeconds, TimeUnit.SECONDS);
            LOGGER.info("Performance monitoring started with interval: " + intervalSeconds + " seconds");
        }
    }
    
    /**
     * Stop monitoring performance metrics.
     */
    public void stopMonitoring() {
        monitoring = false;
        LOGGER.info("Performance monitoring stopped");
    }
    
    /**
     * Set a listener for performance events.
     */
    public void setListener(PerformanceListener listener) {
        this.listener = listener;
    }
    
    /**
     * Start timing an operation.
     */
    public TimerContext startTimer(String operation) {
        TimerContext context = new TimerContext(operation, System.nanoTime());
        activeTimers.put(operation + Thread.currentThread().getId(), context);
        return context;
    }
    
    /**
     * Stop timing an operation.
     */
    public void stopTimer(TimerContext context) {
        if (context == null) return;
        
        long duration = System.nanoTime() - context.startTime;
        String key = context.operation + Thread.currentThread().getId();
        activeTimers.remove(key);
        
        OperationMetrics metrics = operationMetrics.computeIfAbsent(
            context.operation, k -> new OperationMetrics(k));
        metrics.record(duration);
        
        if (duration > 1_000_000_000L) { // Log operations taking more than 1 second
            LOGGER.log(Level.WARNING, "Slow operation detected: {0} took {1}ms",
                new Object[]{context.operation, duration / 1_000_000});
        }
    }
    
    /**
     * Get current memory usage.
     */
    public MemoryInfo getMemoryInfo() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        return new MemoryInfo(
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            nonHeapUsage.getMax()
        );
    }
    
    /**
     * Get CPU usage percentage.
     */
    public double getCpuUsage() {
        if (!threadBean.isThreadCpuTimeSupported()) {
            return -1;
        }
        
        long totalCpuTime = 0;
        for (long id : threadBean.getAllThreadIds()) {
            long cpuTime = threadBean.getThreadCpuTime(id);
            if (cpuTime > 0) {
                totalCpuTime += cpuTime;
            }
        }
        
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        return (totalCpuTime / 1_000_000_000.0) / availableProcessors * 100;
    }
    
    /**
     * Get garbage collection statistics.
     */
    public GCInfo getGCInfo() {
        long totalGcCount = 0;
        long totalGcTime = 0;
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalGcCount += gcBean.getCollectionCount();
            totalGcTime += gcBean.getCollectionTime();
        }
        
        long gcCountDelta = totalGcCount - lastGcCount;
        long gcTimeDelta = totalGcTime - lastGcTime;
        
        lastGcCount = totalGcCount;
        lastGcTime = totalGcTime;
        
        return new GCInfo(totalGcCount, totalGcTime, gcCountDelta, gcTimeDelta);
    }
    
    /**
     * Get metrics for a specific operation.
     */
    public OperationMetrics getOperationMetrics(String operation) {
        return operationMetrics.get(operation);
    }
    
    /**
     * Clear all operation metrics.
     */
    public void clearMetrics() {
        operationMetrics.clear();
    }
    
    /**
     * Force garbage collection (use sparingly).
     */
    public void requestGC() {
        LOGGER.info("Requesting garbage collection");
        System.gc();
    }
    
    private void collectMetrics() {
        try {
            MemoryInfo memInfo = getMemoryInfo();
            double cpuUsage = getCpuUsage();
            GCInfo gcInfo = getGCInfo();
            
            if (listener != null) {
                listener.onMetricsCollected(memInfo, cpuUsage, gcInfo);
            }
            
            // Log warnings for high resource usage
            double heapUsagePercent = (memInfo.heapUsed * 100.0) / memInfo.heapMax;
            if (heapUsagePercent > 90) {
                LOGGER.log(Level.WARNING, "High heap memory usage: {0}%", 
                    String.format("%.1f", heapUsagePercent));
            }
            
            if (cpuUsage > 80) {
                LOGGER.log(Level.WARNING, "High CPU usage: {0}%", 
                    String.format("%.1f", cpuUsage));
            }
            
            if (gcInfo.gcTimeDelta > 1000) { // More than 1 second in GC
                LOGGER.log(Level.WARNING, "Excessive GC time: {0}ms", gcInfo.gcTimeDelta);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error collecting performance metrics", e);
        }
    }
    
    @Override
    public void close() {
        stopMonitoring();
        monitorExecutor.shutdown();
        try {
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Timer context for tracking operation duration.
     */
    public static class TimerContext implements AutoCloseable {
        private final String operation;
        private final long startTime;
        
        private TimerContext(String operation, long startTime) {
            this.operation = operation;
            this.startTime = startTime;
        }
        
        @Override
        public void close() {
            getInstance().stopTimer(this);
        }
    }
    
    /**
     * Metrics for a specific operation.
     */
    public static class OperationMetrics {
        private final String operation;
        private final LongAdder count = new LongAdder();
        private final LongAdder totalTime = new LongAdder();
        private final AtomicLong minTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTime = new AtomicLong(0);
        
        private OperationMetrics(String operation) {
            this.operation = operation;
        }
        
        private void record(long duration) {
            count.increment();
            totalTime.add(duration);
            updateMin(duration);
            updateMax(duration);
        }
        
        private void updateMin(long duration) {
            long currentMin;
            do {
                currentMin = minTime.get();
            } while (duration < currentMin && !minTime.compareAndSet(currentMin, duration));
        }
        
        private void updateMax(long duration) {
            long currentMax;
            do {
                currentMax = maxTime.get();
            } while (duration > currentMax && !maxTime.compareAndSet(currentMax, duration));
        }
        
        public String getOperation() { return operation; }
        public long getCount() { return count.sum(); }
        public long getTotalTimeNanos() { return totalTime.sum(); }
        public long getMinTimeNanos() { return minTime.get(); }
        public long getMaxTimeNanos() { return maxTime.get(); }
        
        public double getAverageTimeMillis() {
            long c = count.sum();
            return c > 0 ? (totalTime.sum() / 1_000_000.0) / c : 0;
        }
    }
    
    /**
     * Memory information.
     */
    public static class MemoryInfo {
        public final long heapUsed;
        public final long heapMax;
        public final long nonHeapUsed;
        public final long nonHeapMax;
        
        private MemoryInfo(long heapUsed, long heapMax, long nonHeapUsed, long nonHeapMax) {
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapMax = nonHeapMax;
        }
        
        public double getHeapUsagePercent() {
            return heapMax > 0 ? (heapUsed * 100.0) / heapMax : 0;
        }
    }
    
    /**
     * Garbage collection information.
     */
    public static class GCInfo {
        public final long totalGcCount;
        public final long totalGcTime;
        public final long gcCountDelta;
        public final long gcTimeDelta;
        
        private GCInfo(long totalGcCount, long totalGcTime, long gcCountDelta, long gcTimeDelta) {
            this.totalGcCount = totalGcCount;
            this.totalGcTime = totalGcTime;
            this.gcCountDelta = gcCountDelta;
            this.gcTimeDelta = gcTimeDelta;
        }
    }
    
    /**
     * Listener for performance events.
     */
    public interface PerformanceListener {
        void onMetricsCollected(MemoryInfo memoryInfo, double cpuUsage, GCInfo gcInfo);
    }
}