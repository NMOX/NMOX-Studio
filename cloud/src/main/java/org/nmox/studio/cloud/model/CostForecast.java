package org.nmox.studio.cloud.model;

import java.time.Instant;
import java.util.List;

/**
 * Cost forecast model for predicting future costs
 */
public class CostForecast {
    private Instant forecastStartDate;
    private Instant forecastEndDate;
    private String provider;
    private String currency;
    private double forecastedCost;
    private double confidenceLevel;
    private List<ForecastDataPoint> dataPoints;
    private String forecastMethod; // LINEAR, EXPONENTIAL, MACHINE_LEARNING
    private Instant generatedAt;
    private String forecastId;
    
    public static class ForecastDataPoint {
        private Instant date;
        private double predictedCost;
        private double lowerBound;
        private double upperBound;
        private double confidence;
        
        public Instant getDate() { return date; }
        public void setDate(Instant date) { this.date = date; }
        
        public double getPredictedCost() { return predictedCost; }
        public void setPredictedCost(double predictedCost) { this.predictedCost = predictedCost; }
        
        public double getLowerBound() { return lowerBound; }
        public void setLowerBound(double lowerBound) { this.lowerBound = lowerBound; }
        
        public double getUpperBound() { return upperBound; }
        public void setUpperBound(double upperBound) { this.upperBound = upperBound; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }
    
    // Constructors
    public CostForecast() {}
    
    public CostForecast(String provider, String currency, Instant forecastStartDate, Instant forecastEndDate) {
        this.provider = provider;
        this.currency = currency;
        this.forecastStartDate = forecastStartDate;
        this.forecastEndDate = forecastEndDate;
        this.generatedAt = Instant.now();
        this.forecastMethod = "LINEAR";
    }
    
    // Getters and Setters
    public Instant getForecastStartDate() { return forecastStartDate; }
    public void setForecastStartDate(Instant forecastStartDate) { this.forecastStartDate = forecastStartDate; }
    
    public Instant getForecastEndDate() { return forecastEndDate; }
    public void setForecastEndDate(Instant forecastEndDate) { this.forecastEndDate = forecastEndDate; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public double getForecastedCost() { return forecastedCost; }
    public void setForecastedCost(double forecastedCost) { this.forecastedCost = forecastedCost; }
    
    public double getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(double confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    
    public List<ForecastDataPoint> getDataPoints() { return dataPoints; }
    public void setDataPoints(List<ForecastDataPoint> dataPoints) { this.dataPoints = dataPoints; }
    
    public String getForecastMethod() { return forecastMethod; }
    public void setForecastMethod(String forecastMethod) { this.forecastMethod = forecastMethod; }
    
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    
    public String getForecastId() { return forecastId; }
    public void setForecastId(String forecastId) { this.forecastId = forecastId; }
    
    public String getFormattedForecastedCost() {
        return String.format("%.2f %s", forecastedCost, currency);
    }
    
    public boolean isHighConfidence() {
        return confidenceLevel >= 0.8;
    }
    
    @Override
    public String toString() {
        return String.format("CostForecast{provider='%s', forecastedCost=%.2f %s, confidence=%.1f%%}", 
            provider, forecastedCost, currency, confidenceLevel * 100);
    }
}