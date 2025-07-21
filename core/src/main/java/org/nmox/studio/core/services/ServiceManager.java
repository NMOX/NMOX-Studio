package org.nmox.studio.core.services;

import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Central service manager for NMOX Studio.
 * Provides registration, discovery, and lifecycle management of services.
 */
public class ServiceManager {
    
    private static final Logger LOGGER = Logger.getLogger(ServiceManager.class.getName());
    private static ServiceManager instance;
    
    private final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ServiceListener> listeners = new CopyOnWriteArrayList<>();
    private final Lookup.Result<Service> serviceResult;
    
    private ServiceManager() {
        serviceResult = Lookup.getDefault().lookupResult(Service.class);
        serviceResult.addLookupListener(new ServiceLookupListener());
        discoverServices();
    }
    
    public static synchronized ServiceManager getInstance() {
        if (instance == null) {
            instance = new ServiceManager();
        }
        return instance;
    }
    
    /**
     * Register a service instance.
     */
    public <T> void registerService(Class<T> serviceClass, T serviceInstance) {
        services.put(serviceClass, serviceInstance);
        LOGGER.info("Registered service: " + serviceClass.getName());
        notifyServiceRegistered(serviceClass, serviceInstance);
    }
    
    /**
     * Get a service instance by class.
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        return (T) services.get(serviceClass);
    }
    
    /**
     * Unregister a service.
     */
    public <T> void unregisterService(Class<T> serviceClass) {
        Object service = services.remove(serviceClass);
        if (service != null) {
            LOGGER.info("Unregistered service: " + serviceClass.getName());
            notifyServiceUnregistered(serviceClass, service);
        }
    }
    
    /**
     * Add a service listener.
     */
    public void addServiceListener(ServiceListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove a service listener.
     */
    public void removeServiceListener(ServiceListener listener) {
        listeners.remove(listener);
    }
    
    private void discoverServices() {
        Collection<? extends Service> discoveredServices = serviceResult.allInstances();
        for (Service service : discoveredServices) {
            registerService(service.getClass(), service);
        }
    }
    
    private void notifyServiceRegistered(Class<?> serviceClass, Object service) {
        for (ServiceListener listener : listeners) {
            try {
                listener.serviceRegistered(serviceClass, service);
            } catch (Exception e) {
                LOGGER.warning("Error notifying service listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyServiceUnregistered(Class<?> serviceClass, Object service) {
        for (ServiceListener listener : listeners) {
            try {
                listener.serviceUnregistered(serviceClass, service);
            } catch (Exception e) {
                LOGGER.warning("Error notifying service listener: " + e.getMessage());
            }
        }
    }
    
    private class ServiceLookupListener implements LookupListener {
        @Override
        public void resultChanged(LookupEvent ev) {
            discoverServices();
        }
    }
    
    /**
     * Listener interface for service registration events.
     */
    public interface ServiceListener {
        void serviceRegistered(Class<?> serviceClass, Object service);
        void serviceUnregistered(Class<?> serviceClass, Object service);
    }
}