package org.nmox.studio.cloud.model;

import java.util.List;
import java.util.Map;

public class ContainerCluster {
    private String id;
    private String name;
    private String provider;
    private String region;
    private String orchestrator;
    private int nodeCount;
    private String kubernetesVersion;
    private List<String> nodeIds;
    private Map<String, String> labels;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getOrchestrator() { return orchestrator; }
    public void setOrchestrator(String orchestrator) { this.orchestrator = orchestrator; }

    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }

    public String getKubernetesVersion() { return kubernetesVersion; }
    public void setKubernetesVersion(String kubernetesVersion) { this.kubernetesVersion = kubernetesVersion; }

    public List<String> getNodeIds() { return nodeIds; }
    public void setNodeIds(List<String> nodeIds) { this.nodeIds = nodeIds; }

    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
}