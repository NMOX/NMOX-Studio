package org.nmox.studio.containers.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import org.openide.util.lookup.ServiceProvider;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service for managing Docker containers and images.
 */
@ServiceProvider(service = DockerService.class)
public class DockerService {
    
    private static final Logger LOGGER = Logger.getLogger(DockerService.class.getName());
    private DockerClient dockerClient;
    private boolean connected = false;
    
    public DockerService() {
        initialize();
    }
    
    private void initialize() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();
            
            dockerClient = DockerClientBuilder.getInstance(config).build();
            
            // Test connection
            dockerClient.pingCmd().exec();
            connected = true;
            LOGGER.info("Connected to Docker daemon");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to connect to Docker daemon", e);
            connected = false;
        }
    }
    
    /**
     * Check if Docker daemon is connected
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * List all containers
     */
    public CompletableFuture<List<Container>> listContainers(boolean showAll) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return new ArrayList<>();
            }
            
            try {
                return dockerClient.listContainersCmd()
                    .withShowAll(showAll)
                    .exec();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to list containers", e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * List all images
     */
    public CompletableFuture<List<Image>> listImages() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return new ArrayList<>();
            }
            
            try {
                return dockerClient.listImagesCmd().exec();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to list images", e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Start a container
     */
    public CompletableFuture<Void> startContainer(String containerId) {
        return CompletableFuture.runAsync(() -> {
            if (!connected) {
                throw new IllegalStateException("Docker not connected");
            }
            
            try {
                dockerClient.startContainerCmd(containerId).exec();
                LOGGER.info("Started container: " + containerId);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to start container: " + containerId, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Stop a container
     */
    public CompletableFuture<Void> stopContainer(String containerId) {
        return CompletableFuture.runAsync(() -> {
            if (!connected) {
                throw new IllegalStateException("Docker not connected");
            }
            
            try {
                dockerClient.stopContainerCmd(containerId).exec();
                LOGGER.info("Stopped container: " + containerId);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to stop container: " + containerId, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Remove a container
     */
    public CompletableFuture<Void> removeContainer(String containerId, boolean force) {
        return CompletableFuture.runAsync(() -> {
            if (!connected) {
                throw new IllegalStateException("Docker not connected");
            }
            
            try {
                dockerClient.removeContainerCmd(containerId)
                    .withForce(force)
                    .exec();
                LOGGER.info("Removed container: " + containerId);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to remove container: " + containerId, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Pull an image from registry
     */
    public CompletableFuture<Void> pullImage(String imageName) {
        return CompletableFuture.runAsync(() -> {
            if (!connected) {
                throw new IllegalStateException("Docker not connected");
            }
            
            try {
                dockerClient.pullImageCmd(imageName)
                    .start()
                    .awaitCompletion();
                LOGGER.info("Pulled image: " + imageName);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to pull image: " + imageName, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Create and run a container
     */
    public CompletableFuture<String> runContainer(String imageName, String containerName, 
                                                  List<String> env, List<String> ports) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                throw new IllegalStateException("Docker not connected");
            }
            
            try {
                CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                    .withName(containerName)
                    .withEnv(env)
                    .exec();
                
                String containerId = container.getId();
                dockerClient.startContainerCmd(containerId).exec();
                
                LOGGER.info("Created and started container: " + containerName);
                return containerId;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to run container", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Get container logs
     */
    public CompletableFuture<String> getContainerLogs(String containerId, int lines) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                throw new IllegalStateException("Docker not connected");
            }
            
            try {
                StringBuilder logs = new StringBuilder();
                dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(lines)
                    .exec(new LogCallback(logs));
                
                return logs.toString();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to get container logs", e);
                return "Error fetching logs: " + e.getMessage();
            }
        });
    }
    
    /**
     * Get container stats
     */
    public CompletableFuture<Statistics> getContainerStats(String containerId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                throw new IllegalStateException("Docker not connected");
            }
            
            try {
                return dockerClient.statsCmd(containerId)
                    .withNoStream(true)
                    .exec(new StatsCallback())
                    .awaitResult();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to get container stats", e);
                return null;
            }
        });
    }
    
    /**
     * Build image from Dockerfile
     */
    public CompletableFuture<String> buildImage(String dockerfilePath, String tag) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                throw new IllegalStateException("Docker not connected");
            }
            
            try {
                String imageId = dockerClient.buildImageCmd()
                    .withDockerfile(new java.io.File(dockerfilePath))
                    .withTags(java.util.Set.of(tag))
                    .start()
                    .awaitImageId();
                
                LOGGER.info("Built image: " + tag);
                return imageId;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to build image", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    // Helper classes for callbacks
    private static class LogCallback extends com.github.dockerjava.api.async.ResultCallback.Adapter<Frame> {
        private final StringBuilder logs;
        
        public LogCallback(StringBuilder logs) {
            this.logs = logs;
        }
        
        @Override
        public void onNext(Frame frame) {
            logs.append(new String(frame.getPayload()));
        }
    }
    
    private static class StatsCallback extends com.github.dockerjava.api.async.ResultCallback.Adapter<Statistics> {
        private Statistics stats;
        
        @Override
        public void onNext(Statistics statistics) {
            this.stats = statistics;
        }
        
        public Statistics awaitResult() throws InterruptedException {
            awaitCompletion();
            return stats;
        }
    }
}