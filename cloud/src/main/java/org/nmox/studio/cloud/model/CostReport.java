package org.nmox.studio.cloud.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Cost report model containing cost and billing information
 */
public class CostReport {
    private Instant reportDate;
    private String provider;
    private String currency;
    private double totalCost;
    private double totalUsage;
    private List<CostItem> costItems;
    private Map<String, Double> costByService;
    private Map<String, Double> costByRegion;
    private Map<String, Double> costByResource;
    private String reportId;
    private Instant generatedAt;
    
    public static class CostItem {
        private String service;
        private String resource;
        private String region;
        private double cost;
        private double usage;
        private String usageUnit;
        private Instant date;
        
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        
        public double getCost() { return cost; }
        public void setCost(double cost) { this.cost = cost; }
        
        public double getUsage() { return usage; }
        public void setUsage(double usage) { this.usage = usage; }
        
        public String getUsageUnit() { return usageUnit; }
        public void setUsageUnit(String usageUnit) { this.usageUnit = usageUnit; }
        
        public Instant getDate() { return date; }
        public void setDate(Instant date) { this.date = date; }
    }
    
    // Constructors
    public CostReport() {}
    
    public CostReport(String provider, String currency) {
        this.provider = provider;
        this.currency = currency;
        this.reportDate = Instant.now();
        this.generatedAt = Instant.now();
    }
    
    // Getters and Setters
    public Instant getReportDate() { return reportDate; }
    public void setReportDate(Instant reportDate) { this.reportDate = reportDate; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
    
    public double getTotalUsage() { return totalUsage; }
    public void setTotalUsage(double totalUsage) { this.totalUsage = totalUsage; }
    
    public List<CostItem> getCostItems() { return costItems; }
    public void setCostItems(List<CostItem> costItems) { this.costItems = costItems; }
    
    public Map<String, Double> getCostByService() { return costByService; }
    public void setCostByService(Map<String, Double> costByService) { this.costByService = costByService; }
    
    public Map<String, Double> getCostByRegion() { return costByRegion; }
    public void setCostByRegion(Map<String, Double> costByRegion) { this.costByRegion = costByRegion; }
    
    public Map<String, Double> getCostByResource() { return costByResource; }
    public void setCostByResource(Map<String, Double> costByResource) { this.costByResource = costByResource; }
    
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    
    public String getFormattedTotalCost() {
        return String.format("%.2f %s", totalCost, currency);
    }
    
    @Override
    public String toString() {
        return String.format("CostReport{provider='%s', totalCost=%.2f %s, reportDate=%s}", 
            provider, totalCost, currency, reportDate);
    }
}