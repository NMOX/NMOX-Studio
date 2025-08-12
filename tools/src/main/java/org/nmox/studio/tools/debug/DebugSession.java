package org.nmox.studio.tools.debug;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an active debug session.
 */
public interface DebugSession {
    
    /**
     * Gets the session ID.
     */
    String getId();
    
    /**
     * Gets the session name.
     */
    String getName();
    
    /**
     * Gets the session state.
     */
    DebugState getState();
    
    /**
     * Sets a breakpoint.
     */
    CompletableFuture<Breakpoint> setBreakpoint(String file, int line);
    
    /**
     * Sets a conditional breakpoint.
     */
    CompletableFuture<Breakpoint> setBreakpoint(String file, int line, String condition);
    
    /**
     * Removes a breakpoint.
     */
    CompletableFuture<Void> removeBreakpoint(String breakpointId);
    
    /**
     * Gets all breakpoints.
     */
    List<Breakpoint> getBreakpoints();
    
    /**
     * Continues execution.
     */
    CompletableFuture<Void> resume();
    
    /**
     * Steps over the current line.
     */
    CompletableFuture<Void> stepOver();
    
    /**
     * Steps into the function.
     */
    CompletableFuture<Void> stepInto();
    
    /**
     * Steps out of the current function.
     */
    CompletableFuture<Void> stepOut();
    
    /**
     * Pauses execution.
     */
    CompletableFuture<Void> pause();
    
    /**
     * Evaluates an expression in the current context.
     */
    CompletableFuture<EvaluationResult> evaluate(String expression);
    
    /**
     * Gets the current call stack.
     */
    List<StackFrame> getCallStack();
    
    /**
     * Gets variables in the current scope.
     */
    Map<String, Variable> getVariables();
    
    /**
     * Watches an expression.
     */
    void addWatch(String expression);
    
    /**
     * Removes a watch expression.
     */
    void removeWatch(String expression);
    
    /**
     * Gets all watch expressions.
     */
    List<WatchExpression> getWatches();
    
    /**
     * Disconnects the debug session.
     */
    void disconnect();
    
    /**
     * Adds a debug event listener.
     */
    void addListener(DebugEventListener listener);
    
    /**
     * Removes a debug event listener.
     */
    void removeListener(DebugEventListener listener);
    
    /**
     * Debug states.
     */
    enum DebugState {
        CONNECTING,
        CONNECTED,
        RUNNING,
        PAUSED,
        TERMINATED,
        DISCONNECTED
    }
    
    /**
     * Represents a breakpoint.
     */
    class Breakpoint {
        private final String id;
        private final String file;
        private final int line;
        private final String condition;
        private boolean verified;
        private boolean enabled;
        
        public Breakpoint(String id, String file, int line, String condition) {
            this.id = id;
            this.file = file;
            this.line = line;
            this.condition = condition;
            this.verified = false;
            this.enabled = true;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getFile() { return file; }
        public int getLine() { return line; }
        public String getCondition() { return condition; }
        public boolean isVerified() { return verified; }
        public void setVerified(boolean verified) { this.verified = verified; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    
    /**
     * Represents a stack frame.
     */
    class StackFrame {
        private final String id;
        private final String name;
        private final String source;
        private final int line;
        private final int column;
        private final Map<String, Variable> locals;
        
        public StackFrame(String id, String name, String source, int line, int column, Map<String, Variable> locals) {
            this.id = id;
            this.name = name;
            this.source = source;
            this.line = line;
            this.column = column;
            this.locals = locals;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getSource() { return source; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
        public Map<String, Variable> getLocals() { return locals; }
    }
    
    /**
     * Represents a variable.
     */
    class Variable {
        private final String name;
        private final String value;
        private final String type;
        private final List<Variable> properties;
        
        public Variable(String name, String value, String type, List<Variable> properties) {
            this.name = name;
            this.value = value;
            this.type = type;
            this.properties = properties;
        }
        
        // Getters
        public String getName() { return name; }
        public String getValue() { return value; }
        public String getType() { return type; }
        public List<Variable> getProperties() { return properties; }
    }
    
    /**
     * Result of expression evaluation.
     */
    class EvaluationResult {
        private final String value;
        private final String type;
        private final boolean success;
        private final String error;
        
        public EvaluationResult(String value, String type, boolean success, String error) {
            this.value = value;
            this.type = type;
            this.success = success;
            this.error = error;
        }
        
        // Getters
        public String getValue() { return value; }
        public String getType() { return type; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    /**
     * Watch expression.
     */
    class WatchExpression {
        private final String expression;
        private String value;
        private String type;
        private boolean error;
        
        public WatchExpression(String expression) {
            this.expression = expression;
        }
        
        // Getters and setters
        public String getExpression() { return expression; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isError() { return error; }
        public void setError(boolean error) { this.error = error; }
    }
    
    /**
     * Debug event listener.
     */
    interface DebugEventListener {
        void onStateChanged(DebugState newState);
        void onBreakpointHit(Breakpoint breakpoint);
        void onException(String message, String stack);
        void onConsoleOutput(String message, String level);
        void onSourceMapped(String original, String mapped);
    }
}