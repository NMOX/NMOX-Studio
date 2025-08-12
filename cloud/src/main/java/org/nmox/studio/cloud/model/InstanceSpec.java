package org.nmox.studio.cloud.model;

import java.util.Map;

/**
 * Specifications for creating cloud instances
 */
public class InstanceSpec {
    private String name;
    private String provider;
    private String instanceType;
    private String region;
    private String availabilityZone;
    private String imageId;
    private String securityGroup;
    private String keyPair;
    private int minInstances = 1;
    private int maxInstances = 1;
    private Map<String, String> tags;
    private String userData;
    
    // Constructors
    public InstanceSpec() {}
    
    public InstanceSpec(String name, String provider, String instanceType) {
        this.name = name;
        this.provider = provider;
        this.instanceType = instanceType;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getInstanceType() { return instanceType; }
    public void setInstanceType(String instanceType) { this.instanceType = instanceType; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getAvailabilityZone() { return availabilityZone; }
    public void setAvailabilityZone(String availabilityZone) { this.availabilityZone = availabilityZone; }
    
    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    
    public String getSecurityGroup() { return securityGroup; }
    public void setSecurityGroup(String securityGroup) { this.securityGroup = securityGroup; }
    
    public String getKeyPair() { return keyPair; }
    public void setKeyPair(String keyPair) { this.keyPair = keyPair; }
    
    public int getMinInstances() { return minInstances; }
    public void setMinInstances(int minInstances) { this.minInstances = minInstances; }
    
    public int getMaxInstances() { return maxInstances; }
    public void setMaxInstances(int maxInstances) { this.maxInstances = maxInstances; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    
    public String getUserData() { return userData; }
    public void setUserData(String userData) { this.userData = userData; }
    
    @Override
    public String toString() {
        return String.format("InstanceSpec{name='%s', provider='%s', instanceType='%s'}", 
            name, provider, instanceType);
    }
}