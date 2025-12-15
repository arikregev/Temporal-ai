package com.temporal.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.logging.Log;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class LlmClient {
    
    @ConfigProperty(name = "llm.base-url", defaultValue = "http://localhost:11434")
    String baseUrl;
    
    @ConfigProperty(name = "llm.model", defaultValue = "llama2")
    String model;
    
    @ConfigProperty(name = "llm.timeout", defaultValue = "30000")
    int timeout;
    
    private OllamaApi ollamaApi;
    
    @jakarta.annotation.PostConstruct
    void init() {
        ollamaApi = QuarkusRestClientBuilder.newBuilder()
            .baseUri(URI.create(baseUrl))
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build(OllamaApi.class);
    }
    
    /**
     * Generate a completion/response from the LLM
     */
    public String generate(String prompt) {
        return generate(prompt, Map.of());
    }
    
    /**
     * Generate a completion with custom parameters
     */
    public String generate(String prompt, Map<String, Object> parameters) {
        try {
            OllamaRequest request = new OllamaRequest();
            request.model = model;
            request.prompt = prompt;
            request.stream = false;
            
            // Apply custom parameters
            if (parameters.containsKey("temperature")) {
                request.options = Map.of("temperature", parameters.get("temperature"));
            }
            
            OllamaResponse response = ollamaApi.generate(request);
            return response.response != null ? response.response : "";
        } catch (Exception e) {
            Log.error("Error generating LLM response", e);
            throw new LlmException("Failed to generate LLM response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate embeddings for semantic search
     */
    public List<Double> generateEmbedding(String text) {
        try {
            OllamaEmbeddingRequest request = new OllamaEmbeddingRequest();
            request.model = model;
            request.prompt = text;
            
            OllamaEmbeddingResponse response = ollamaApi.generateEmbedding(request);
            return response.embedding != null ? response.embedding : List.of();
        } catch (Exception e) {
            Log.error("Error generating embedding", e);
            throw new LlmException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    /**
     * Classify query intent
     */
    public QueryIntent classifyIntent(String query) {
        String prompt = String.format("""
            Classify the following security analyst query into one of these categories:
            - SCAN_DURATION: Questions about why a scan took a certain amount of time
            - SCAN_CHANGES: Questions about what changed between scans
            - CWE_STATISTICS: Questions about CWE counts, trends, or statistics
            - FINDING_EXPLANATION: Questions asking to explain a specific finding
            - POLICY_QUERY: Questions about policies or policy creation
            - GENERAL: Other questions
            
            Query: %s
            
            Respond with only the category name.
            """, query);
        
        String response = generate(prompt);
        String category = response.trim().toUpperCase();
        
        // Map response to intent
        QueryIntent.IntentType intentType = switch (category) {
            case "SCAN_DURATION" -> QueryIntent.IntentType.SCAN_DURATION;
            case "SCAN_CHANGES" -> QueryIntent.IntentType.SCAN_CHANGES;
            case "CWE_STATISTICS" -> QueryIntent.IntentType.CWE_STATISTICS;
            case "FINDING_EXPLANATION" -> QueryIntent.IntentType.FINDING_EXPLANATION;
            case "POLICY_QUERY" -> QueryIntent.IntentType.POLICY_QUERY;
            default -> QueryIntent.IntentType.GENERAL;
        };
        
        return new QueryIntent(intentType, query);
    }
    
    @Path("/api/generate")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public interface OllamaApi {
        @POST
        OllamaResponse generate(OllamaRequest request);
        
        @POST
        @Path("/embed")
        OllamaEmbeddingResponse generateEmbedding(OllamaEmbeddingRequest request);
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OllamaRequest {
        public String model;
        public String prompt;
        public boolean stream = false;
        public Map<String, Object> options;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OllamaResponse {
        public String response;
        @JsonProperty("done")
        public boolean done;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OllamaEmbeddingRequest {
        public String model;
        public String prompt;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OllamaEmbeddingResponse {
        public List<Double> embedding;
    }
    
    public record QueryIntent(IntentType type, String originalQuery) {
        public enum IntentType {
            SCAN_DURATION,
            SCAN_CHANGES,
            CWE_STATISTICS,
            FINDING_EXPLANATION,
            POLICY_QUERY,
            GENERAL
        }
    }
    
    public static class LlmException extends RuntimeException {
        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
