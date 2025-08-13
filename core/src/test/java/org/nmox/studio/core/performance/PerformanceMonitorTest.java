package org.nmox.studio.core.performance;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PerformanceMonitorTest {
    
    private PerformanceMonitor monitor;
    
    @BeforeEach
    public void setUp() {
        monitor = PerformanceMonitor.getInstance();
    }
    
    @AfterEach
    public void tearDown() {
        monitor.clearMetrics();
    }
    
    @Test
    public void testTimerTracking() throws Exception {
        String operation = "test-operation";
        
        try (PerformanceMonitor.TimerContext timer = monitor.startTimer(operation)) {
            Thread.sleep(50);
        }
        
        PerformanceMonitor.OperationMetrics metrics = monitor.getOperationMetrics(operation);
        assertNotNull(metrics);
        assertEquals(1, metrics.getCount());
        assertTrue(metrics.getTotalTimeNanos() > 0);
        assertTrue(metrics.getAverageTimeMillis() >= 50);
    }
    
    @Test
    public void testMemoryInfo() {
        PerformanceMonitor.MemoryInfo memInfo = monitor.getMemoryInfo();
        
        assertNotNull(memInfo);
        assertTrue(memInfo.heapUsed > 0);
        assertTrue(memInfo.heapMax > 0);
        assertTrue(memInfo.getHeapUsagePercent() > 0);
        assertTrue(memInfo.getHeapUsagePercent() <= 100);
    }
    
    @Test
    public void testCpuUsage() {
        double cpuUsage = monitor.getCpuUsage();
        
        assertTrue(cpuUsage >= -1);
        if (cpuUsage >= 0) {
            assertTrue(cpuUsage <= 100);
        }
    }
    
    @Test
    public void testGCInfo() {
        PerformanceMonitor.GCInfo gcInfo = monitor.getGCInfo();
        
        assertNotNull(gcInfo);
        assertTrue(gcInfo.totalGcCount >= 0);
        assertTrue(gcInfo.totalGcTime >= 0);
    }
    
    @Test
    public void testMultipleOperations() throws Exception {
        for (int i = 0; i < 5; i++) {
            try (PerformanceMonitor.TimerContext timer = monitor.startTimer("op-" + i)) {
                Thread.sleep(10);
            }
        }
        
        for (int i = 0; i < 5; i++) {
            PerformanceMonitor.OperationMetrics metrics = monitor.getOperationMetrics("op-" + i);
            assertNotNull(metrics);
            assertEquals(1, metrics.getCount());
        }
    }
    
    @Test
    public void testConcurrentTimers() throws Exception {
        String operation = "concurrent-op";
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try (PerformanceMonitor.TimerContext timer = monitor.startTimer(operation)) {
                    Thread.sleep(10);
                } catch (Exception e) {
                    fail("Timer failed: " + e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        PerformanceMonitor.OperationMetrics metrics = monitor.getOperationMetrics(operation);
        assertNotNull(metrics);
        assertEquals(threadCount, metrics.getCount());
    }
    
    @Test
    public void testPerformanceListener() throws Exception {
        AtomicReference<PerformanceMonitor.MemoryInfo> capturedMemInfo = new AtomicReference<>();
        AtomicReference<Double> capturedCpuUsage = new AtomicReference<>();
        AtomicReference<PerformanceMonitor.GCInfo> capturedGcInfo = new AtomicReference<>();
        CountDownLatch listenerLatch = new CountDownLatch(1);
        
        monitor.setListener((memInfo, cpuUsage, gcInfo) -> {
            capturedMemInfo.set(memInfo);
            capturedCpuUsage.set(cpuUsage);
            capturedGcInfo.set(gcInfo);
            listenerLatch.countDown();
        });
        
        monitor.startMonitoring(1);
        
        assertTrue(listenerLatch.await(2, TimeUnit.SECONDS));
        
        assertNotNull(capturedMemInfo.get());
        assertNotNull(capturedCpuUsage.get());
        assertNotNull(capturedGcInfo.get());
        
        monitor.stopMonitoring();
    }
}