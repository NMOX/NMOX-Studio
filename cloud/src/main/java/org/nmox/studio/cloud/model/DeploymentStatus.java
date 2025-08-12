package org.nmox.studio.cloud.model;

/**
 * Enum for deployment statuses
 */
public enum DeploymentStatus {
    PENDING("Deployment is being prepared"),
    CREATING("Deployment is being created"),
    RUNNING("Deployment is running successfully"),
    UPDATING("Deployment is being updated"),
    STOPPING("Deployment is being stopped"),
    STOPPED("Deployment has been stopped"),
    DELETING("Deployment is being deleted"),
    DELETED("Deployment has been deleted"),
    ERROR("Deployment encountered an error"),
    UNKNOWN("Deployment status is unknown");
    
    private final String description;
    
    DeploymentStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isTerminal() {
        return this == DELETED || this == ERROR;
    }
    
    public boolean isActive() {
        return this == RUNNING || this == UPDATING;
    }
}