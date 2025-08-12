package org.nmox.studio.cloud.model;

import java.time.Instant;
import java.util.List;

/**
 * Cost query model for retrieving cost and billing information
 */
public class CostQuery {
    private Instant startDate;
    private Instant endDate;
    private String provider;
    private String region;
    private List<String> services;
    private List<String> resourceIds;
    private String granularity; // DAILY, MONTHLY, HOURLY
    private String groupBy; // SERVICE, REGION, RESOURCE_ID
    private String currency = "USD";
    private boolean includeTax = false;
    private List<String> costMetrics;
    
    // Constructors
    public CostQuery() {}
    
    public CostQuery(Instant startDate, Instant endDate, String provider) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.provider = provider;
        this.granularity = "DAILY";
    }
    
    // Getters and Setters
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }
    
    public List<String> getResourceIds() { return resourceIds; }
    public void setResourceIds(List<String> resourceIds) { this.resourceIds = resourceIds; }
    
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    
    public String getGroupBy() { return groupBy; }
    public void setGroupBy(String groupBy) { this.groupBy = groupBy; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public boolean isIncludeTax() { return includeTax; }
    public void setIncludeTax(boolean includeTax) { this.includeTax = includeTax; }
    
    public List<String> getCostMetrics() { return costMetrics; }
    public void setCostMetrics(List<String> costMetrics) { this.costMetrics = costMetrics; }
    
    @Override
    public String toString() {
        return String.format("CostQuery{provider='%s', startDate=%s, endDate=%s, granularity='%s'}", 
            provider, startDate, endDate, granularity);
    }
}