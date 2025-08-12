package org.nmox.studio.cloud.model;

import java.util.List;
import java.util.Map;

/**
 * Network specifications for creating cloud networks
 */
public class NetworkSpec {
    private String name;
    private String provider;
    private String region;
    private String cidrBlock;
    private boolean enableDnsHostnames = true;
    private boolean enableDnsSupport = true;
    private Map<String, String> tags;
    private List<SubnetSpec> subnets;
    private List<SecurityGroupRule> securityRules;
    
    public static class SubnetSpec {
        private String name;
        private String cidrBlock;
        private String availabilityZone;
        private boolean mapPublicIpOnLaunch = false;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getCidrBlock() { return cidrBlock; }
        public void setCidrBlock(String cidrBlock) { this.cidrBlock = cidrBlock; }
        
        public String getAvailabilityZone() { return availabilityZone; }
        public void setAvailabilityZone(String availabilityZone) { this.availabilityZone = availabilityZone; }
        
        public boolean isMapPublicIpOnLaunch() { return mapPublicIpOnLaunch; }
        public void setMapPublicIpOnLaunch(boolean mapPublicIpOnLaunch) { this.mapPublicIpOnLaunch = mapPublicIpOnLaunch; }
    }
    
    public static class SecurityGroupRule {
        private String protocol;
        private int fromPort;
        private int toPort;
        private String cidrBlock;
        private String direction; // INGRESS or EGRESS
        
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        
        public int getFromPort() { return fromPort; }
        public void setFromPort(int fromPort) { this.fromPort = fromPort; }
        
        public int getToPort() { return toPort; }
        public void setToPort(int toPort) { this.toPort = toPort; }
        
        public String getCidrBlock() { return cidrBlock; }
        public void setCidrBlock(String cidrBlock) { this.cidrBlock = cidrBlock; }
        
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
    }
    
    // Constructors
    public NetworkSpec() {}
    
    public NetworkSpec(String name, String provider, String cidrBlock) {
        this.name = name;
        this.provider = provider;
        this.cidrBlock = cidrBlock;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getCidrBlock() { return cidrBlock; }
    public void setCidrBlock(String cidrBlock) { this.cidrBlock = cidrBlock; }
    
    public boolean isEnableDnsHostnames() { return enableDnsHostnames; }
    public void setEnableDnsHostnames(boolean enableDnsHostnames) { this.enableDnsHostnames = enableDnsHostnames; }
    
    public boolean isEnableDnsSupport() { return enableDnsSupport; }
    public void setEnableDnsSupport(boolean enableDnsSupport) { this.enableDnsSupport = enableDnsSupport; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    
    public List<SubnetSpec> getSubnets() { return subnets; }
    public void setSubnets(List<SubnetSpec> subnets) { this.subnets = subnets; }
    
    public List<SecurityGroupRule> getSecurityRules() { return securityRules; }
    public void setSecurityRules(List<SecurityGroupRule> securityRules) { this.securityRules = securityRules; }
    
    @Override
    public String toString() {
        return String.format("NetworkSpec{name='%s', provider='%s', cidrBlock='%s'}", 
            name, provider, cidrBlock);
    }
}