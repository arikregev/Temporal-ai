package com.temporal.ai.dependencytrack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.logging.Log;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DependencyTrackClient {
    
    @ConfigProperty(name = "dependencytrack.base-url", defaultValue = "http://localhost:8080")
    String baseUrl;
    
    @ConfigProperty(name = "dependencytrack.api-key")
    Optional<String> apiKey;
    
    @ConfigProperty(name = "dependencytrack.timeout", defaultValue = "30000")
    int timeout;
    
    private DependencyTrackApi dtApi;
    
    @jakarta.annotation.PostConstruct
    void init() {
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        Log.info("Initializing Dependency Track REST client - Base URL: " + cleanBaseUrl);
        
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
            .baseUri(URI.create(cleanBaseUrl))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS);
        
        // Add API key header if configured
        String apiKeyValue = getApiKeyHeader();
        if (apiKeyValue != null && !apiKeyValue.isEmpty()) {
            builder.register(new jakarta.ws.rs.client.ClientRequestFilter() {
                @Override
                public void filter(jakarta.ws.rs.client.ClientRequestContext requestContext) {
                    requestContext.getHeaders().add("X-Api-Key", apiKeyValue);
                }
            });
        }
        
        dtApi = builder.build(DependencyTrackApi.class);
    }
    
    /**
     * Get API key header value for authentication
     */
    private String getApiKeyHeader() {
        return apiKey.orElse("");
    }
    
    /**
     * Lookup project by name and version
     */
    public Optional<Project> lookupProject(String name, String version) {
        try {
            Log.debug("Looking up project: " + name + " version: " + version);
            Project project = dtApi.lookupProject(name, version);
            if (project != null && project.uuid() != null) {
                Log.info("Found project: " + project.uuid() + " (" + project.name() + ":" + project.version() + ")");
                return Optional.of(project);
            }
        } catch (Exception e) {
            Log.warn("Error looking up project " + name + ":" + version + " - " + e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Get project details by UUID
     */
    public Optional<Project> getProject(UUID projectUuid) {
        try {
            Log.debug("Getting project details for UUID: " + projectUuid);
            Project project = dtApi.getProject(projectUuid);
            if (project != null) {
                return Optional.of(project);
            }
        } catch (Exception e) {
            Log.warn("Error getting project " + projectUuid + " - " + e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Get current project metrics (vulnerability counts, severity breakdown)
     */
    public Optional<ProjectMetrics> getProjectMetrics(UUID projectUuid) {
        try {
            Log.debug("Getting metrics for project: " + projectUuid);
            ProjectMetrics metrics = dtApi.getProjectMetrics(projectUuid);
            if (metrics != null) {
                Log.info("Retrieved metrics for project " + projectUuid + " - Vulnerabilities: " + 
                    metrics.critical() + " critical, " + metrics.high() + " high");
                return Optional.of(metrics);
            }
        } catch (Exception e) {
            Log.warn("Error getting metrics for project " + projectUuid + " - " + e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Get SBOM upload history for a project
     */
    public List<BomUpload> getBomHistory(UUID projectUuid) {
        try {
            Log.debug("Getting BOM history for project: " + projectUuid);
            List<BomUpload> boms = dtApi.getBomHistory(projectUuid);
            Log.info("Retrieved " + boms.size() + " BOM uploads for project " + projectUuid);
            return boms;
        } catch (Exception e) {
            Log.warn("Error getting BOM history for project " + projectUuid + " - " + e.getMessage());
            return List.of();
        }
    }
    
    @Path("/api/v1")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public interface DependencyTrackApi {
        @GET
        @Path("/project/lookup")
        Project lookupProject(
            @QueryParam("name") String name,
            @QueryParam("version") String version
        );
        
        @GET
        @Path("/project/{uuid}")
        Project getProject(@PathParam("uuid") UUID uuid);
        
        @GET
        @Path("/metrics/project/{uuid}/current")
        ProjectMetrics getProjectMetrics(@PathParam("uuid") UUID uuid);
        
        @GET
        @Path("/bom")
        List<BomUpload> getBomHistory(@QueryParam("project") UUID projectUuid);
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(
        @JsonProperty("uuid") UUID uuid,
        @JsonProperty("name") String name,
        @JsonProperty("version") String version,
        @JsonProperty("description") String description,
        @JsonProperty("active") Boolean active,
        @JsonProperty("lastBomImport") String lastBomImport,
        @JsonProperty("lastBomImportFormat") String lastBomImportFormat
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectMetrics(
        @JsonProperty("critical") Integer critical,
        @JsonProperty("high") Integer high,
        @JsonProperty("medium") Integer medium,
        @JsonProperty("low") Integer low,
        @JsonProperty("unassigned") Integer unassigned,
        @JsonProperty("vulnerabilities") Integer vulnerabilities,
        @JsonProperty("vulnerableComponents") Integer vulnerableComponents,
        @JsonProperty("components") Integer components,
        @JsonProperty("suppressed") Integer suppressed,
        @JsonProperty("findingsTotal") Integer findingsTotal,
        @JsonProperty("findingsAudited") Integer findingsAudited,
        @JsonProperty("findingsUnaudited") Integer findingsUnaudited,
        @JsonProperty("policyViolationsTotal") Integer policyViolationsTotal,
        @JsonProperty("policyViolationsFail") Integer policyViolationsFail,
        @JsonProperty("policyViolationsWarn") Integer policyViolationsWarn,
        @JsonProperty("policyViolationsInfo") Integer policyViolationsInfo,
        @JsonProperty("lastMeasurement") Long lastMeasurement
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BomUpload(
        @JsonProperty("uuid") UUID uuid,
        @JsonProperty("project") UUID project,
        @JsonProperty("bom") String bom,
        @JsonProperty("bomFormat") String bomFormat,
        @JsonProperty("specVersion") String specVersion,
        @JsonProperty("imported") String imported,
        @JsonProperty("importedBy") String importedBy
    ) {}
}

