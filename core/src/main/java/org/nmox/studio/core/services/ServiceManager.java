package org.nmox.studio.core.services;

import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central service manager for NMOX Studio.
 * Provides registration, discovery, and lifecycle management of services.
 */
public class ServiceManager {
    
    private static final Logger LOGGER = Logger.getLogger(ServiceManager.class.getName());
    private static volatile ServiceManager instance;
    
    private final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> namedServices = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ServiceListener> listeners = new CopyOnWriteArrayList<>();
    private final Lookup.Result<Service> serviceResult;
    private final ExecutorService notificationExecutor = Executors.newCachedThreadPool(
            r -> {
                Thread t = new Thread(r, "ServiceManager-Notification");
                t.setDaemon(true);
                return t;
            });
    private volatile boolean shutdown = false;
    
    private ServiceManager() {
        serviceResult = Lookup.getDefault().lookupResult(Service.class);
        serviceResult.addLookupListener(new ServiceLookupListener());
        discoverServices();
    }
    
    public static ServiceManager getInstance() {
        ServiceManager localInstance = instance;
        if (localInstance == null) {
            synchronized (ServiceManager.class) {
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = new ServiceManager();
                }
            }
        }
        return localInstance;
    }
    
    /**
     * Register a service instance.
     * @throws IllegalArgumentException if serviceClass or serviceInstance is null
     * @throws IllegalStateException if the service manager has been shut down
     */
    public <T> void registerService(Class<T> serviceClass, T serviceInstance) {
        Objects.requireNonNull(serviceClass, "Service class cannot be null");
        Objects.requireNonNull(serviceInstance, "Service instance cannot be null");
        
        if (shutdown) {
            throw new IllegalStateException("ServiceManager has been shut down");
        }
        
        T previousService = (T) services.put(serviceClass, serviceInstance);
        if (previousService != null && previousService instanceof AutoCloseable) {
            try {
                ((AutoCloseable) previousService).close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing previous service instance: " + serviceClass.getName(), e);
            }
        }
        
        LOGGER.log(Level.INFO, "Registered service: {0}", serviceClass.getName());
        notifyServiceRegistered(serviceClass, serviceInstance);
    }
    
    /**
     * Register a named service instance.
     */
    public void registerService(String name, Object serviceInstance) {
        Objects.requireNonNull(name, "Service name cannot be null");
        Objects.requireNonNull(serviceInstance, "Service instance cannot be null");
        
        if (shutdown) {
            throw new IllegalStateException("ServiceManager has been shut down");
        }
        
        Object previousService = namedServices.put(name, serviceInstance);
        if (previousService != null && previousService instanceof AutoCloseable) {
            try {
                ((AutoCloseable) previousService).close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing previous named service: " + name, e);
            }
        }
        
        LOGGER.log(Level.INFO, "Registered named service: {0}", name);
        notifyServiceRegistered(serviceInstance.getClass(), serviceInstance);
    }
    
    /**
     * Get a service instance by class.
     * @return the service instance, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        Objects.requireNonNull(serviceClass, "Service class cannot be null");
        return (T) services.get(serviceClass);
    }
    
    /**
     * Get a service instance by class, wrapped in Optional.
     */
    public <T> Optional<T> getServiceOptional(Class<T> serviceClass) {
        return Optional.ofNullable(getService(serviceClass));
    }
    
    /**
     * Get a named service instance.
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(String name, Class<T> expectedType) {
        Objects.requireNonNull(name, "Service name cannot be null");
        Objects.requireNonNull(expectedType, "Expected type cannot be null");
        
        Object service = namedServices.get(name);
        if (service != null && !expectedType.isInstance(service)) {
            throw new ClassCastException("Service '" + name + "' is not of type " + expectedType.getName());
        }
        return (T) service;
    }
    
    /**
     * Check if a service is registered.
     */
    public boolean hasService(Class<?> serviceClass) {
        return services.containsKey(serviceClass);
    }
    
    /**
     * Check if a named service is registered.
     */
    public boolean hasService(String name) {
        return namedServices.containsKey(name);
    }
    
    /**
     * Unregister a service.
     */
    public <T> void unregisterService(Class<T> serviceClass) {
        Objects.requireNonNull(serviceClass, "Service class cannot be null");
        
        Object service = services.remove(serviceClass);
        if (service != null) {
            if (service instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) service).close();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error closing service: " + serviceClass.getName(), e);
                }
            }
            LOGGER.log(Level.INFO, "Unregistered service: {0}", serviceClass.getName());
            notifyServiceUnregistered(serviceClass, service);
        }
    }
    
    /**
     * Unregister a named service.
     */
    public void unregisterService(String name) {
        Objects.requireNonNull(name, "Service name cannot be null");
        
        Object service = namedServices.remove(name);
        if (service != null) {
            if (service instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) service).close();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error closing named service: " + name, e);
                }
            }
            LOGGER.log(Level.INFO, "Unregistered named service: {0}", name);
            notifyServiceUnregistered(service.getClass(), service);
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
            @SuppressWarnings("unchecked")
            Class<Service> serviceClass = (Class<Service>) service.getClass();
            registerService(serviceClass, service);
        }
    }
    
    private void notifyServiceRegistered(Class<?> serviceClass, Object service) {
        if (shutdown || listeners.isEmpty()) {
            return;
        }
        
        notificationExecutor.execute(() -> {
            for (ServiceListener listener : listeners) {
                try {
                    listener.serviceRegistered(serviceClass, service);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error notifying service listener", e);
                }
            }
        });
    }
    
    private void notifyServiceUnregistered(Class<?> serviceClass, Object service) {
        if (shutdown || listeners.isEmpty()) {
            return;
        }
        
        notificationExecutor.execute(() -> {
            for (ServiceListener listener : listeners) {
                try {
                    listener.serviceUnregistered(serviceClass, service);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error notifying service listener", e);
                }
            }
        });
    }
    
    private class ServiceLookupListener implements LookupListener {
        @Override
        public void resultChanged(LookupEvent ev) {
            discoverServices();
        }
    }
    
    /**
     * Shutdown the service manager and cleanup resources.
     */
    public void shutdown() {
        if (shutdown) {
            return;
        }
        
        shutdown = true;
        
        LOGGER.info("Shutting down ServiceManager...");
        
        notificationExecutor.shutdown();
        try {
            if (!notificationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                notificationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            notificationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        for (Object service : services.values()) {
            if (service instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) service).close();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error closing service during shutdown", e);
                }
            }
        }
        
        for (Object service : namedServices.values()) {
            if (service instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) service).close();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error closing named service during shutdown", e);
                }
            }
        }
        
        services.clear();
        namedServices.clear();
        listeners.clear();
        
        LOGGER.info("ServiceManager shutdown complete");
    }
    
    /**
     * Get the number of registered services.
     */
    public int getServiceCount() {
        return services.size() + namedServices.size();
    }
    
    /**
     * Listener interface for service registration events.
     */
    public interface ServiceListener {
        void serviceRegistered(Class<?> serviceClass, Object service);
        void serviceUnregistered(Class<?> serviceClass, Object service);
    }
}