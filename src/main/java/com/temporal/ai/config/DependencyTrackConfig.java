package com.temporal.ai.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "dependencytrack")
public interface DependencyTrackConfig {
    @WithName("baseUrl")
    String baseUrl();
    
    @WithName("apiKey")
    String apiKey();
    
    @WithName("timeout")
    long timeout(); // in milliseconds
}

