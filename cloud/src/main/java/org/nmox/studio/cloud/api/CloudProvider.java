package org.nmox.studio.cloud.api;

import org.nmox.studio.cloud.model.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Core interface for cloud provider implementations.
 * Provides abstraction over AWS, Azure, GCP, and other cloud platforms.
 */
public interface CloudProvider {
    
    /**
     * Get the provider name (AWS, Azure, GCP, etc.)
     */
    String getName();
    
    /**
     * Get the provider icon path for UI display
     */
    String getIconPath();
    
    /**
     * Test connection to the cloud provider
     */
    CompletableFuture<Boolean> testConnection();
    
    /**
     * Configure provider with credentials
     */
    void configure(CloudCredentials credentials);
    
    // Instance Management
    
    /**
     * List all instances/VMs
     */
    CompletableFuture<List<CloudInstance>> listInstances();
    
    /**
     * Create a new instance
     */
    CompletableFuture<CloudInstance> createInstance(InstanceSpec spec);
    
    /**
     * Start an instance
     */
    CompletableFuture<Void> startInstance(String instanceId);
    
    /**
     * Stop an instance
     */
    CompletableFuture<Void> stopInstance(String instanceId);
    
    /**
     * Terminate an instance
     */
    CompletableFuture<Void> terminateInstance(String instanceId);
    
    /**
     * Get instance details
     */
    CompletableFuture<CloudInstance> getInstance(String instanceId);
    
    // Container Management
    
    /**
     * List container clusters
     */
    CompletableFuture<List<ContainerCluster>> listClusters();
    
    /**
     * Deploy a container
     */
    CompletableFuture<ContainerDeployment> deployContainer(ContainerSpec spec);
    
    /**
     * List running containers
     */
    CompletableFuture<List<Container>> listContainers(String clusterId);
    
    // Application Deployment
    
    /**
     * Deploy an application
     */
    CompletableFuture<Deployment> deployApplication(ApplicationSpec spec);
    
    /**
     * List deployments
     */
    CompletableFuture<List<Deployment>> listDeployments();
    
    /**
     * Get deployment status
     */
    CompletableFuture<DeploymentStatus> getDeploymentStatus(String deploymentId);
    
    /**
     * Rollback a deployment
     */
    CompletableFuture<Void> rollbackDeployment(String deploymentId);
    
    // Monitoring
    
    /**
     * Get metrics for a resource
     */
    CompletableFuture<CloudMetrics> getMetrics(String resourceId, MetricType type);
    
    /**
     * Get logs for a resource
     */
    CompletableFuture<List<LogEntry>> getLogs(String resourceId, LogQuery query);
    
    // Networking
    
    /**
     * List networks/VPCs
     */
    CompletableFuture<List<CloudNetwork>> listNetworks();
    
    /**
     * Create a network
     */
    CompletableFuture<CloudNetwork> createNetwork(NetworkSpec spec);
    
    // Storage
    
    /**
     * List storage buckets/containers
     */
    CompletableFuture<List<CloudStorage>> listStorage();
    
    /**
     * Create storage
     */
    CompletableFuture<CloudStorage> createStorage(StorageSpec spec);
    
    // Cost Management
    
    /**
     * Get current costs
     */
    CompletableFuture<CostReport> getCostReport(CostQuery query);
    
    /**
     * Get cost forecast
     */
    CompletableFuture<CostForecast> getCostForecast();
}