package org.nmox.studio.cloud.model;

import java.util.Map;
import java.util.List;

public class ContainerSpec {
    private String image;
    private String tag;
    private int replicas;
    private Map<String, String> environment;
    private List<String> ports;
    private Map<String, String> labels;
    private String cpu;
    private String memory;

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public int getReplicas() { return replicas; }
    public void setReplicas(int replicas) { this.replicas = replicas; }

    public Map<String, String> getEnvironment() { return environment; }
    public void setEnvironment(Map<String, String> environment) { this.environment = environment; }

    public List<String> getPorts() { return ports; }
    public void setPorts(List<String> ports) { this.ports = ports; }

    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }

    public String getCpu() { return cpu; }
    public void setCpu(String cpu) { this.cpu = cpu; }

    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory; }
}