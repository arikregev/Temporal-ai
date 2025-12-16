package com.temporal.ai.data.temporal;

import io.quarkus.logging.Log;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class TemporalService {
    
    @ConfigProperty(name = "temporal.server.address", defaultValue = "localhost:7233")
    String serverAddress;
    
    @ConfigProperty(name = "temporal.namespace", defaultValue = "default")
    String namespace;
    
    private WorkflowServiceStubs serviceStubs;
    private WorkflowClient workflowClient;
    
    @PostConstruct
    void init() {
        String[] addressParts = serverAddress.split(":");
        String host = addressParts[0];
        int port = addressParts.length > 1 ? Integer.parseInt(addressParts[1]) : 7233;
        
        serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(host + ":" + port)
                .build()
        );
        
        workflowClient = WorkflowClient.newInstance(
            serviceStubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build()
        );
        
        Log.info("Temporal client initialized for namespace: " + namespace);
    }
    
    @PreDestroy
    void cleanup() {
        if (serviceStubs != null) {
            serviceStubs.shutdown();
        }
    }
    
    public WorkflowClient getWorkflowClient() {
        return workflowClient;
    }
    
    /**
     * Get workflow execution details
     */
    public Optional<WorkflowExecutionInfo> getWorkflowExecution(String workflowId, String runId) {
        try {
            DescribeWorkflowExecutionRequest request = DescribeWorkflowExecutionRequest.newBuilder()
                .setNamespace(namespace)
                .setExecution(io.temporal.api.common.v1.WorkflowExecution.newBuilder()
                    .setWorkflowId(workflowId)
                    .setRunId(runId != null ? runId : "")
                    .build())
                .build();
            
            DescribeWorkflowExecutionResponse response = serviceStubs.blockingStub()
                .describeWorkflowExecution(request);
            
            if (response.hasWorkflowExecutionInfo()) {
                var executionInfo = response.getWorkflowExecutionInfo();
                var status = executionInfo.getStatus();
                
                Instant startTime = Instant.ofEpochSecond(
                    executionInfo.getStartTime().getSeconds(),
                    executionInfo.getStartTime().getNanos()
                );
                
                Instant closeTime = null;
                Duration duration = null;
                if (executionInfo.hasCloseTime()) {
                    closeTime = Instant.ofEpochSecond(
                        executionInfo.getCloseTime().getSeconds(),
                        executionInfo.getCloseTime().getNanos()
                    );
                    duration = Duration.between(startTime, closeTime);
                }
                
                // Convert Payload maps to Object maps
                Map<String, Object> memoMap = new java.util.HashMap<>();
                executionInfo.getMemo().getFieldsMap().forEach((k, v) -> 
                    memoMap.put(k, v.getData().toStringUtf8())
                );
                
                Map<String, Object> searchAttrMap = new java.util.HashMap<>();
                executionInfo.getSearchAttributes().getIndexedFieldsMap().forEach((k, v) -> 
                    searchAttrMap.put(k, v.getData().toStringUtf8())
                );
                
                return Optional.of(new WorkflowExecutionInfo(
                    workflowId,
                    executionInfo.getExecution().getRunId(),
                    status.name(),
                    startTime,
                    closeTime,
                    duration != null ? duration.toMillis() : null,
                    memoMap,
                    searchAttrMap
                ));
            }
        } catch (Exception e) {
            Log.error("Error getting workflow execution for " + workflowId, e);
        }
        return Optional.empty();
    }
    
    /**
     * Get workflow execution history
     */
    public Optional<WorkflowHistory> getWorkflowHistory(String workflowId, String runId) {
        try {
            GetWorkflowExecutionHistoryRequest request = GetWorkflowExecutionHistoryRequest.newBuilder()
                .setNamespace(namespace)
                .setExecution(io.temporal.api.common.v1.WorkflowExecution.newBuilder()
                    .setWorkflowId(workflowId)
                    .setRunId(runId != null ? runId : "")
                    .build())
                .build();
            
            var response = serviceStubs.blockingStub().getWorkflowExecutionHistory(request);
            
            return Optional.of(new WorkflowHistory(
                workflowId,
                runId,
                response.getHistory().getEventsList()
            ));
        } catch (Exception e) {
            Log.error("Error getting workflow history for " + workflowId, e);
        }
        return Optional.empty();
    }
    
    /**
     * Analyze workflow execution to extract scan metadata
     */
    public ScanMetadata extractScanMetadata(String workflowId, String runId) {
        Log.info("Extracting scan metadata for workflow: " + workflowId + ", runId: " + runId);
        
        try {
            Optional<WorkflowExecutionInfo> executionInfo = getWorkflowExecution(workflowId, runId);
            
            if (executionInfo.isEmpty()) {
                Log.warn("Workflow execution not found: " + workflowId);
                return new ScanMetadata(workflowId, runId, null, null, null, null, null);
            }
            
            WorkflowExecutionInfo info = executionInfo.get();
            Log.info("Workflow execution found. Status: " + info.status() + ", Duration: " + info.durationMs() + "ms");
            
            Map<String, Object> memo = info.memo();
            Map<String, Object> searchAttributes = info.searchAttributes();
            
            String team = extractString(memo, "team");
            String project = extractString(memo, "project");
            String scanType = extractString(memo, "scanType");
            String status = mapTemporalStatus(info.status());
            
            Log.info("Extracted metadata - Team: " + team + ", Project: " + project + ", ScanType: " + scanType);
            
            return new ScanMetadata(
                workflowId,
                info.runId(),
                team,
                project,
                scanType,
                status,
                info.durationMs()
            );
        } catch (Exception e) {
            Log.error("Error extracting scan metadata for workflow " + workflowId, e);
            return new ScanMetadata(workflowId, runId, null, null, null, null, null);
        }
    }
    
    private String extractString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    private String mapTemporalStatus(String temporalStatus) {
        return switch (temporalStatus) {
            case "WORKFLOW_EXECUTION_STATUS_COMPLETED" -> "COMPLETED";
            case "WORKFLOW_EXECUTION_STATUS_FAILED" -> "FAILED";
            case "WORKFLOW_EXECUTION_STATUS_RUNNING" -> "RUNNING";
            case "WORKFLOW_EXECUTION_STATUS_CANCELED" -> "CANCELED";
            case "WORKFLOW_EXECUTION_STATUS_TERMINATED" -> "TERMINATED";
            case "WORKFLOW_EXECUTION_STATUS_TIMED_OUT" -> "TIMED_OUT";
            default -> "UNKNOWN";
        };
    }
    
    public record WorkflowExecutionInfo(
        String workflowId,
        String runId,
        String status,
        Instant startTime,
        Instant closeTime,
        Long durationMs,
        Map<String, Object> memo,
        Map<String, Object> searchAttributes
    ) {}
    
    public record WorkflowHistory(
        String workflowId,
        String runId,
        java.util.List<io.temporal.api.history.v1.HistoryEvent> events
    ) {}
    
    public record ScanMetadata(
        String workflowId,
        String runId,
        String team,
        String project,
        String scanType,
        String status,
        Long durationMs
    ) {}
}
