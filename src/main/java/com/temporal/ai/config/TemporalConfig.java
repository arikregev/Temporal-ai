package com.temporal.ai.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.RUN_TIME, name = "temporal")
public class TemporalConfig {
    
    public String serverAddress = "localhost:7233";
    public String namespace = "default";
    
    public String getServerAddress() {
        return serverAddress;
    }
    
    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
