package org.nmox.studio.cloud.model;

public class ContainerDeployment {
    private String id;
    private String name;
    private String clusterId;
    private ContainerSpec spec;
    private String status;
    private int runningInstances;
    private int desiredInstances;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public ContainerSpec getSpec() { return spec; }
    public void setSpec(ContainerSpec spec) { this.spec = spec; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRunningInstances() { return runningInstances; }
    public void setRunningInstances(int runningInstances) { this.runningInstances = runningInstances; }

    public int getDesiredInstances() { return desiredInstances; }
    public void setDesiredInstances(int desiredInstances) { this.desiredInstances = desiredInstances; }
}