package org.nmox.studio.cloud.model;

import java.util.Map;
import java.util.HashMap;

public class CloudMetrics {
    private double cpuUsage;
    private double memoryUsage;
    private double networkIn;
    private double networkOut;
    private double diskUsage;
    private Map<String, Double> customMetrics = new HashMap<>();

    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }

    public double getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }

    public double getNetworkIn() { return networkIn; }
    public void setNetworkIn(double networkIn) { this.networkIn = networkIn; }

    public double getNetworkOut() { return networkOut; }
    public void setNetworkOut(double networkOut) { this.networkOut = networkOut; }

    public double getDiskUsage() { return diskUsage; }
    public void setDiskUsage(double diskUsage) { this.diskUsage = diskUsage; }

    public Map<String, Double> getCustomMetrics() { return customMetrics; }
    public void setCustomMetrics(Map<String, Double> customMetrics) { this.customMetrics = customMetrics; }
}