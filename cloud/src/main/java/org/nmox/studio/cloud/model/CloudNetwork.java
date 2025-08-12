package org.nmox.studio.cloud.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Network model for cloud networking resources
 */
public class CloudNetwork {
    private String id;
    private String name;
    private String provider;
    private String region;
    private String cidrBlock;
    private NetworkState state;
    private Instant createdTime;
    private Map<String, String> tags;
    private List<String> subnetIds;
    private String routeTableId;
    private String securityGroupId;
    private boolean isDefault;
    
    public enum NetworkState {
        CREATING,
        AVAILABLE,
        UPDATING,
        DELETING,
        DELETED,
        ERROR
    }
    
    // Constructors
    public CloudNetwork() {}
    
    public CloudNetwork(String id, String name, String provider) {
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
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getCidrBlock() { return cidrBlock; }
    public void setCidrBlock(String cidrBlock) { this.cidrBlock = cidrBlock; }
    
    public NetworkState getState() { return state; }
    public void setState(NetworkState state) { this.state = state; }
    
    public Instant getCreatedTime() { return createdTime; }
    public void setCreatedTime(Instant createdTime) { this.createdTime = createdTime; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    
    public List<String> getSubnetIds() { return subnetIds; }
    public void setSubnetIds(List<String> subnetIds) { this.subnetIds = subnetIds; }
    
    public String getRouteTableId() { return routeTableId; }
    public void setRouteTableId(String routeTableId) { this.routeTableId = routeTableId; }
    
    public String getSecurityGroupId() { return securityGroupId; }
    public void setSecurityGroupId(String securityGroupId) { this.securityGroupId = securityGroupId; }
    
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    
    public boolean isAvailable() {
        return state == NetworkState.AVAILABLE;
    }
    
    @Override
    public String toString() {
        return String.format("CloudNetwork{id='%s', name='%s', cidrBlock='%s', state=%s}", 
            id, name, cidrBlock, state);
    }
}