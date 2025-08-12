package org.nmox.studio.cloud.model;

import java.time.Instant;
import java.util.Map;

/**
 * Log entry model
 */
public class LogEntry {
    private String id;
    private Instant timestamp;
    private String level;
    private String message;
    private String source;
    private String logGroup;
    private String logStream;
    private String resourceId;
    private Map<String, Object> fields;
    private String rawMessage;
    
    // Constructors
    public LogEntry() {}
    
    public LogEntry(Instant timestamp, String level, String message) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public String getLogGroup() { return logGroup; }
    public void setLogGroup(String logGroup) { this.logGroup = logGroup; }
    
    public String getLogStream() { return logStream; }
    public void setLogStream(String logStream) { this.logStream = logStream; }
    
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    
    public Map<String, Object> getFields() { return fields; }
    public void setFields(Map<String, Object> fields) { this.fields = fields; }
    
    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }
    
    public boolean isError() {
        return "ERROR".equalsIgnoreCase(level) || "FATAL".equalsIgnoreCase(level);
    }
    
    public boolean isWarning() {
        return "WARN".equalsIgnoreCase(level) || "WARNING".equalsIgnoreCase(level);
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s - %s", timestamp, level, message);
    }
}