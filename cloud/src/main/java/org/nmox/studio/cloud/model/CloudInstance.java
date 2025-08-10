package org.nmox.studio.cloud.model;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a cloud compute instance (VM/EC2/etc.)
 */
public class CloudInstance {
    private String id;
    private String name;
    private String provider;
    private InstanceState state;
    private String instanceType;
    private String region;
    private String availabilityZone;
    private String publicIp;
    private String privateIp;
    private String imageId;
    private Instant launchTime;
    private Map<String, String> tags;
    private CloudMetrics metrics;
    
    public enum InstanceState {
        PENDING,
        RUNNING,
        STOPPING,
        STOPPED,
        TERMINATING,
        TERMINATED,
        UNKNOWN
    }
    
    // Constructors
    public CloudInstance() {}
    
    public CloudInstance(String id, String name, String provider) {
        this.id = id;
        this.name = name;
        this.provider = provider;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public InstanceState getState() { return state; }
    public void setState(InstanceState state) { this.state = state; }
    
    public String getInstanceType() { return instanceType; }
    public void setInstanceType(String instanceType) { this.instanceType = instanceType; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getAvailabilityZone() { return availabilityZone; }
    public void setAvailabilityZone(String availabilityZone) { this.availabilityZone = availabilityZone; }
    
    public String getPublicIp() { return publicIp; }
    public void setPublicIp(String publicIp) { this.publicIp = publicIp; }
    
    public String getPrivateIp() { return privateIp; }
    public void setPrivateIp(String privateIp) { this.privateIp = privateIp; }
    
    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    
    public Instant getLaunchTime() { return launchTime; }
    public void setLaunchTime(Instant launchTime) { this.launchTime = launchTime; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    
    public CloudMetrics getMetrics() { return metrics; }
    public void setMetrics(CloudMetrics metrics) { this.metrics = metrics; }
    
    public boolean isRunning() {
        return state == InstanceState.RUNNING;
    }
    
    public boolean isStopped() {
        return state == InstanceState.STOPPED;
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s) - %s [%s]", name, id, instanceType, state);
    }
}