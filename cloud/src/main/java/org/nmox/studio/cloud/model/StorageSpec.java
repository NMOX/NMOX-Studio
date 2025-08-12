package org.nmox.studio.cloud.model;

import java.util.Map;

/**
 * Storage specifications for creating cloud storage
 */
public class StorageSpec {
    private String name;
    private String provider;
    private String region;
    private String availabilityZone;
    private CloudStorage.StorageType type;
    private long sizeInGB;
    private String volumeType;
    private int iops;
    private String throughputMode;
    private boolean encrypted = false;
    private String encryptionKey;
    private String snapshotId;
    private Map<String, String> tags;
    private boolean deleteOnTermination = false;
    
    // Constructors
    public StorageSpec() {}
    
    public StorageSpec(String name, String provider, CloudStorage.StorageType type, long sizeInGB) {
        this.name = name;
        this.provider = provider;
        this.type = type;
        this.sizeInGB = sizeInGB;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getAvailabilityZone() { return availabilityZone; }
    public void setAvailabilityZone(String availabilityZone) { this.availabilityZone = availabilityZone; }
    
    public CloudStorage.StorageType getType() { return type; }
    public void setType(CloudStorage.StorageType type) { this.type = type; }
    
    public long getSizeInGB() { return sizeInGB; }
    public void setSizeInGB(long sizeInGB) { this.sizeInGB = sizeInGB; }
    
    public String getVolumeType() { return volumeType; }
    public void setVolumeType(String volumeType) { this.volumeType = volumeType; }
    
    public int getIops() { return iops; }
    public void setIops(int iops) { this.iops = iops; }
    
    public String getThroughputMode() { return throughputMode; }
    public void setThroughputMode(String throughputMode) { this.throughputMode = throughputMode; }
    
    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
    
    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
    
    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    
    public boolean isDeleteOnTermination() { return deleteOnTermination; }
    public void setDeleteOnTermination(boolean deleteOnTermination) { this.deleteOnTermination = deleteOnTermination; }
    
    @Override
    public String toString() {
        return String.format("StorageSpec{name='%s', provider='%s', type=%s, size=%dGB}", 
            name, provider, type, sizeInGB);
    }
}