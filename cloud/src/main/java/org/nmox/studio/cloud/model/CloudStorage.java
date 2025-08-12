package org.nmox.studio.cloud.model;

import java.time.Instant;
import java.util.Map;

/**
 * Storage model for cloud storage resources
 */
public class CloudStorage {
    private String id;
    private String name;
    private String provider;
    private String region;
    private StorageType type;
    private StorageState state;
    private long sizeInGB;
    private long usedSpaceInGB;
    private String mountPoint;
    private String attachedInstanceId;
    private Instant createdTime;
    private Map<String, String> tags;
    private boolean encrypted;
    private String encryptionKey;
    private int iops;
    private String throughputMode;
    
    public enum StorageType {
        BLOCK,
        OBJECT,
        FILE,
        ARCHIVE
    }
    
    public enum StorageState {
        CREATING,
        AVAILABLE,
        IN_USE,
        DELETING,
        DELETED,
        ERROR
    }
    
    // Constructors
    public CloudStorage() {}
    
    public CloudStorage(String id, String name, String provider, StorageType type) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.type = type;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public StorageType getType() { return type; }
    public void setType(StorageType type) { this.type = type; }
    
    public StorageState getState() { return state; }
    public void setState(StorageState state) { this.state = state; }
    
    public long getSizeInGB() { return sizeInGB; }
    public void setSizeInGB(long sizeInGB) { this.sizeInGB = sizeInGB; }
    
    public long getUsedSpaceInGB() { return usedSpaceInGB; }
    public void setUsedSpaceInGB(long usedSpaceInGB) { this.usedSpaceInGB = usedSpaceInGB; }
    
    public String getMountPoint() { return mountPoint; }
    public void setMountPoint(String mountPoint) { this.mountPoint = mountPoint; }
    
    public String getAttachedInstanceId() { return attachedInstanceId; }
    public void setAttachedInstanceId(String attachedInstanceId) { this.attachedInstanceId = attachedInstanceId; }
    
    public Instant getCreatedTime() { return createdTime; }
    public void setCreatedTime(Instant createdTime) { this.createdTime = createdTime; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    
    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
    
    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
    
    public int getIops() { return iops; }
    public void setIops(int iops) { this.iops = iops; }
    
    public String getThroughputMode() { return throughputMode; }
    public void setThroughputMode(String throughputMode) { this.throughputMode = throughputMode; }
    
    public boolean isAvailable() {
        return state == StorageState.AVAILABLE;
    }
    
    public boolean isAttached() {
        return attachedInstanceId != null && state == StorageState.IN_USE;
    }
    
    public double getUsagePercentage() {
        return sizeInGB > 0 ? (double) usedSpaceInGB / sizeInGB * 100 : 0;
    }
    
    @Override
    public String toString() {
        return String.format("CloudStorage{id='%s', name='%s', type=%s, size=%dGB, state=%s}", 
            id, name, type, sizeInGB, state);
    }
}