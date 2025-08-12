package org.nmox.studio.cloud.model;

import java.util.Map;
import java.util.List;

public class ApplicationSpec {
    private String name;
    private String type;
    private String runtime;
    private String sourceUrl;
    private Map<String, String> environment;
    private List<String> services;
    private Map<String, String> configuration;
    private int instances;
    private String memory;
    private String cpu;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public Map<String, String> getEnvironment() { return environment; }
    public void setEnvironment(Map<String, String> environment) { this.environment = environment; }

    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }

    public Map<String, String> getConfiguration() { return configuration; }
    public void setConfiguration(Map<String, String> configuration) { this.configuration = configuration; }

    public int getInstances() { return instances; }
    public void setInstances(int instances) { this.instances = instances; }

    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory; }

    public String getCpu() { return cpu; }
    public void setCpu(String cpu) { this.cpu = cpu; }
}