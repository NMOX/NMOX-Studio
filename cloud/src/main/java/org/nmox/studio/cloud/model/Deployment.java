package org.nmox.studio.cloud.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a deployment of cloud resources
 */
public class Deployment {
    private String id;
    private String name;
    private String description;
    private DeploymentStatus status;
    private String provider;
    private String region;
    private Instant createdTime;
    private Instant lastModifiedTime;
    private Map<String, String> tags;
    private List<String> instanceIds;
    private String networkId;
    private String storageId;
    private String configurationHash;
    
    // Constructors
    public Deployment() {}
    
    public Deployment(String id, String name, String provider) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.status = DeploymentStatus.PENDING;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public DeploymentStatus getStatus() { return status; }
    public void setStatus(DeploymentStatus status) { this.status = status; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public Instant getCreatedTime() { return createdTime; }
    public void setCreatedTime(Instant createdTime) { this.createdTime = createdTime; }
    
    public Instant getLastModifiedTime() { return lastModifiedTime; }
    public void setLastModifiedTime(Instant lastModifiedTime) { this.lastModifiedTime = lastModifiedTime; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    
    public List<String> getInstanceIds() { return instanceIds; }
    public void setInstanceIds(List<String> instanceIds) { this.instanceIds = instanceIds; }
    
    public String getNetworkId() { return networkId; }
    public void setNetworkId(String networkId) { this.networkId = networkId; }
    
    public String getStorageId() { return storageId; }
    public void setStorageId(String storageId) { this.storageId = storageId; }
    
    public String getConfigurationHash() { return configurationHash; }
    public void setConfigurationHash(String configurationHash) { this.configurationHash = configurationHash; }
    
    public boolean isActive() {
        return status == DeploymentStatus.RUNNING || status == DeploymentStatus.UPDATING;
    }
    
    @Override
    public String toString() {
        return String.format("Deployment{id='%s', name='%s', status=%s}", id, name, status);
    }
}