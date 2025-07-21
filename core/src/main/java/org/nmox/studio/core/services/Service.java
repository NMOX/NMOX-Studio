package org.nmox.studio.core.services;

/**
 * Marker interface for NMOX Studio services.
 * All services should implement this interface to be discoverable
 * by the ServiceManager through the NetBeans Lookup system.
 */
public interface Service {
    
    /**
     * Initialize the service.
     * Called when the service is first registered.
     */
    default void initialize() {
        // Default implementation does nothing
    }
    
    /**
     * Shutdown the service.
     * Called when the service is being unregistered or the application is closing.
     */
    default void shutdown() {
        // Default implementation does nothing
    }
    
    /**
     * Get the service name for identification purposes.
     */
    default String getServiceName() {
        return this.getClass().getSimpleName();
    }
}