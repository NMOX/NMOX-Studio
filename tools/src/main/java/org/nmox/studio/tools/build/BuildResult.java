package org.nmox.studio.tools.build;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a build operation.
 */
public class BuildResult {
    
    private final boolean success;
    private final String output;
    private final String errorOutput;
    private final long duration;
    private final List<BuildMessage> messages;
    private final BuildStatistics statistics;
    
    public BuildResult(boolean success, String output, String errorOutput, long duration) {
        this.success = success;
        this.output = output;
        this.errorOutput = errorOutput;
        this.duration = duration;
        this.messages = new ArrayList<>();
        this.statistics = new BuildStatistics();
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getOutput() {
        return output;
    }
    
    public String getErrorOutput() {
        return errorOutput;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public List<BuildMessage> getMessages() {
        return messages;
    }
    
    public BuildStatistics getStatistics() {
        return statistics;
    }
    
    public void addMessage(BuildMessage message) {
        messages.add(message);
    }
    
    /**
     * Represents a build message (error, warning, info).
     */
    public static class BuildMessage {
        public enum Type {
            ERROR, WARNING, INFO, SUCCESS
        }
        
        private final Type type;
        private final String message;
        private final String file;
        private final int line;
        private final int column;
        
        public BuildMessage(Type type, String message, String file, int line, int column) {
            this.type = type;
            this.message = message;
            this.file = file;
            this.line = line;
            this.column = column;
        }
        
        public BuildMessage(Type type, String message) {
            this(type, message, null, 0, 0);
        }
        
        public Type getType() { return type; }
        public String getMessage() { return message; }
        public String getFile() { return file; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
    }
    
    /**
     * Build statistics.
     */
    public static class BuildStatistics {
        private int filesProcessed;
        private int errors;
        private int warnings;
        private long outputSize;
        private long originalSize;
        private double compressionRatio;
        
        public int getFilesProcessed() { return filesProcessed; }
        public void setFilesProcessed(int filesProcessed) { this.filesProcessed = filesProcessed; }
        
        public int getErrors() { return errors; }
        public void setErrors(int errors) { this.errors = errors; }
        
        public int getWarnings() { return warnings; }
        public void setWarnings(int warnings) { this.warnings = warnings; }
        
        public long getOutputSize() { return outputSize; }
        public void setOutputSize(long outputSize) { this.outputSize = outputSize; }
        
        public long getOriginalSize() { return originalSize; }
        public void setOriginalSize(long originalSize) { this.originalSize = originalSize; }
        
        public double getCompressionRatio() { return compressionRatio; }
        public void setCompressionRatio(double compressionRatio) { this.compressionRatio = compressionRatio; }
    }
}