package org.nmox.studio.cloud.model;

import java.time.Instant;
import java.util.List;

/**
 * Query model for logs
 */
public class LogQuery {
    private String resourceId;
    private String logGroup;
    private String logStream;
    private Instant startTime;
    private Instant endTime;
    private String filterPattern;
    private List<String> fields;
    private int maxResults = 100;
    private String sortOrder = "DESC";
    private String nextToken;
    private LogLevel minLevel;
    
    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }
    
    // Constructors
    public LogQuery() {}
    
    public LogQuery(String resourceId, Instant startTime, Instant endTime) {
        this.resourceId = resourceId;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    // Getters and Setters
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    
    public String getLogGroup() { return logGroup; }
    public void setLogGroup(String logGroup) { this.logGroup = logGroup; }
    
    public String getLogStream() { return logStream; }
    public void setLogStream(String logStream) { this.logStream = logStream; }
    
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    
    public String getFilterPattern() { return filterPattern; }
    public void setFilterPattern(String filterPattern) { this.filterPattern = filterPattern; }
    
    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }
    
    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
    
    public String getNextToken() { return nextToken; }
    public void setNextToken(String nextToken) { this.nextToken = nextToken; }
    
    public LogLevel getMinLevel() { return minLevel; }
    public void setMinLevel(LogLevel minLevel) { this.minLevel = minLevel; }
    
    @Override
    public String toString() {
        return String.format("LogQuery{resourceId='%s', startTime=%s, endTime=%s}", 
            resourceId, startTime, endTime);
    }
}