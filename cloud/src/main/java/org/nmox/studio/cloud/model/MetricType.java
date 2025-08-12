package org.nmox.studio.cloud.model;

/**
 * Enum for metric types
 */
public enum MetricType {
    CPU_USAGE("CPU Usage Percentage", "%"),
    MEMORY_USAGE("Memory Usage Percentage", "%"),
    DISK_USAGE("Disk Usage Percentage", "%"),
    NETWORK_IN("Network Bytes In", "bytes"),
    NETWORK_OUT("Network Bytes Out", "bytes"),
    DISK_READ("Disk Read Operations", "ops"),
    DISK_WRITE("Disk Write Operations", "ops"),
    REQUEST_COUNT("Request Count", "count"),
    RESPONSE_TIME("Response Time", "ms"),
    ERROR_RATE("Error Rate", "%"),
    THROUGHPUT("Throughput", "ops/sec"),
    LATENCY("Latency", "ms"),
    BANDWIDTH("Bandwidth", "bps"),
    CONNECTION_COUNT("Connection Count", "count"),
    QUEUE_DEPTH("Queue Depth", "count"),
    CUSTOM("Custom Metric", "unknown");
    
    private final String displayName;
    private final String unit;
    
    MetricType(String displayName, String unit) {
        this.displayName = displayName;
        this.unit = unit;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getUnit() {
        return unit;
    }
    
    public boolean isPercentage() {
        return "%".equals(unit);
    }
    
    public boolean isNetworkMetric() {
        return this == NETWORK_IN || this == NETWORK_OUT || this == BANDWIDTH;
    }
    
    public boolean isDiskMetric() {
        return this == DISK_USAGE || this == DISK_READ || this == DISK_WRITE;
    }
}