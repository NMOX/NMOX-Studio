package org.nmox.studio.cloud.model;

/**
 * Cloud provider credentials
 */
public class CloudCredentials {
    private String provider;
    private String accessKey;
    private String secretKey;
    private String region;
    private String tenantId;  // For Azure
    private String subscriptionId;  // For Azure
    private String projectId;  // For GCP
    private String jsonKey;  // For GCP service account
    
    public CloudCredentials() {}
    
    public CloudCredentials(String provider) {
        this.provider = provider;
    }
    
    // AWS credentials
    public static CloudCredentials aws(String accessKey, String secretKey, String region) {
        CloudCredentials creds = new CloudCredentials("AWS");
        creds.setAccessKey(accessKey);
        creds.setSecretKey(secretKey);
        creds.setRegion(region);
        return creds;
    }
    
    // Azure credentials
    public static CloudCredentials azure(String tenantId, String subscriptionId, String accessKey, String secretKey) {
        CloudCredentials creds = new CloudCredentials("Azure");
        creds.setTenantId(tenantId);
        creds.setSubscriptionId(subscriptionId);
        creds.setAccessKey(accessKey);
        creds.setSecretKey(secretKey);
        return creds;
    }
    
    // GCP credentials
    public static CloudCredentials gcp(String projectId, String jsonKey) {
        CloudCredentials creds = new CloudCredentials("GCP");
        creds.setProjectId(projectId);
        creds.setJsonKey(jsonKey);
        return creds;
    }
    
    // Getters and setters
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    
    public String getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    
    public String getJsonKey() { return jsonKey; }
    public void setJsonKey(String jsonKey) { this.jsonKey = jsonKey; }
}