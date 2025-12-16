package com.temporal.ai.data;

import com.temporal.ai.data.entity.CWE;
import com.temporal.ai.data.entity.Finding;
import com.temporal.ai.data.entity.Scan;
import com.temporal.ai.data.temporal.TemporalService.ScanMetadata;
import com.temporal.ai.data.temporal.TemporalService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class QueryLayer {
    
    @Inject
    EntityManager entityManager;
    
    @Inject
    TemporalService temporalService;
    
    /**
     * Get scan by ID
     */
    public Optional<Scan> getScanById(UUID scanId) {
        return Optional.ofNullable(Scan.findById(scanId));
    }
    
    /**
     * Get scan by workflow ID
     */
    public Optional<Scan> getScanByWorkflowId(String workflowId) {
        return Scan.find("workflowId", workflowId).firstResultOptional();
    }
    
    /**
     * Get scans for a team within a time range
     */
    public List<Scan> getScansByTeam(String team, Instant startTime, Instant endTime) {
        return Scan.find(
            "team = ?1 AND startedAt >= ?2 AND startedAt <= ?3 ORDER BY startedAt DESC",
            team, startTime, endTime
        ).list();
    }
    
    /**
     * Get latest scan for a team
     */
    public Optional<Scan> getLatestScanByTeam(String team) {
        return Scan.find(
            "team = ?1 ORDER BY startedAt DESC",
            team
        ).firstResultOptional();
    }
    
    /**
     * Get scans with status
     */
    public List<Scan> getScansByStatus(String status, int limit) {
        return Scan.find(
            "status = ?1 ORDER BY startedAt DESC",
            status
        ).page(0, limit).list();
    }
    
    /**
     * Get findings for a scan
     */
    public List<Finding> getFindingsByScanId(UUID scanId) {
        return Finding.find("scan.scanId", scanId).list();
    }
    
    /**
     * Get findings by CWE ID
     */
    public List<Finding> getFindingsByCweId(String cweId, Instant startTime, Instant endTime) {
        return Finding.find(
            "cwe.cweId = ?1 AND scan.startedAt >= ?2 AND scan.startedAt <= ?3",
            cweId, startTime, endTime
        ).list();
    }
    
    /**
     * Get top CWEs for a team in a time period
     */
    public List<CweStatistics> getTopCwesByTeam(String team, Instant startTime, Instant endTime, int limit) {
        String query = """
            SELECT f.cwe_id, c.name, COUNT(*) as count, 
                   COUNT(CASE WHEN f.severity = 'CRITICAL' THEN 1 END) as critical_count,
                   COUNT(CASE WHEN f.severity = 'HIGH' THEN 1 END) as high_count
            FROM findings f
            JOIN scans s ON f.scan_id = s.scan_id
            JOIN cwes c ON f.cwe_id = c.cwe_id
            WHERE s.team = ?1 AND s.started_at >= ?2 AND s.started_at <= ?3
            GROUP BY f.cwe_id, c.name
            ORDER BY count DESC
            LIMIT ?4
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(query)
            .setParameter(1, team)
            .setParameter(2, startTime)
            .setParameter(3, endTime)
            .setParameter(4, limit)
            .getResultList();
        
        return results.stream()
            .map(row -> new CweStatistics(
                (String) row[0],
                (String) row[1],
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue()
            ))
            .toList();
    }
    
    /**
     * Analyze scan duration from Temporal workflow history
     */
    public ScanDurationAnalysis analyzeScanDuration(String workflowId, String runId) {
        Log.info("Analyzing scan duration for workflow: " + workflowId + ", runId: " + runId);
        
        try {
            Log.info("Calling TemporalService.extractScanMetadata...");
            ScanMetadata metadata = temporalService.extractScanMetadata(workflowId, runId);
            
            if (metadata.workflowId() == null || metadata.status() == null) {
                Log.warn("Workflow not found in Temporal: " + workflowId);
                return new ScanDurationAnalysis(workflowId, runId, null, "Workflow not found in Temporal. Check if workflow ID is correct and Temporal server is accessible.");
            }
            
            Log.info("Workflow found in Temporal. Status: " + metadata.status() + ", Duration: " + metadata.durationMs() + "ms");
            
            Long durationMs = metadata.durationMs();
            
            // Get workflow history for detailed analysis
            Log.info("Fetching workflow history for detailed analysis...");
            var historyOpt = temporalService.getWorkflowHistory(workflowId, runId);
            
            String analysis = String.format(
                "Workflow Status: %s. Duration: %.2f seconds",
                metadata.status(),
                durationMs != null ? durationMs / 1000.0 : 0.0
            );
            
            if (historyOpt.isPresent()) {
                var history = historyOpt.get();
                long activityCount = history.events().stream()
                    .filter(e -> e.getEventType() == io.temporal.api.enums.v1.EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED 
                        || e.getEventType() == io.temporal.api.enums.v1.EventType.EVENT_TYPE_ACTIVITY_TASK_COMPLETED)
                    .count();
                analysis += String.format(". Executed %d activities.", activityCount);
                Log.info("Workflow history analyzed. Activity count: " + activityCount);
            } else {
                Log.warn("Could not retrieve workflow history");
                analysis += ". History details unavailable.";
            }
            
            // Add team/project info if available
            if (metadata.team() != null) {
                analysis += " Team: " + metadata.team();
            }
            if (metadata.project() != null) {
                analysis += ", Project: " + metadata.project();
            }
            if (metadata.scanType() != null) {
                analysis += ", Scan Type: " + metadata.scanType();
            }
            
            return new ScanDurationAnalysis(workflowId, runId, durationMs, analysis);
        } catch (Exception e) {
            Log.error("Error analyzing scan duration for workflow " + workflowId, e);
            return new ScanDurationAnalysis(
                workflowId, 
                runId, 
                null, 
                "Error querying Temporal: " + e.getMessage() + ". Check Temporal server connection."
            );
        }
    }
    
    /**
     * Compare two scans to find changes
     */
    public ScanComparison compareScans(UUID scanId1, UUID scanId2) {
        Optional<Scan> scan1Opt = getScanById(scanId1);
        Optional<Scan> scan2Opt = getScanById(scanId2);
        
        if (scan1Opt.isEmpty() || scan2Opt.isEmpty()) {
            return new ScanComparison(null, null, "One or both scans not found");
        }
        
        Scan scan1 = scan1Opt.get();
        Scan scan2 = scan2Opt.get();
        
        List<Finding> findings1 = getFindingsByScanId(scanId1);
        List<Finding> findings2 = getFindingsByScanId(scanId2);
        
        long newFindings = findings2.stream()
            .filter(f -> findings1.stream().noneMatch(f1 -> f1.findingId.equals(f.findingId)))
            .count();
        
        long resolvedFindings = findings1.stream()
            .filter(f -> findings2.stream().noneMatch(f2 -> f2.findingId.equals(f.findingId)))
            .count();
        
        return new ScanComparison(
            scanId1,
            scanId2,
            String.format("Found %d new findings and %d resolved findings between scans", 
                newFindings, resolvedFindings)
        );
    }
    
    /**
     * Get what changed since last successful scan
     */
    public ScanComparison getChangesSinceLastGreenRun(String team) {
        Optional<Scan> lastGreenScan = Scan.find(
            "team = ?1 AND status = 'COMPLETED' ORDER BY startedAt DESC",
            team
        ).firstResultOptional();
        
        Optional<Scan> latestScan = getLatestScanByTeam(team);
        
        if (lastGreenScan.isEmpty() || latestScan.isEmpty()) {
            return new ScanComparison(null, null, "No scans found for comparison");
        }
        
        return compareScans(lastGreenScan.get().scanId, latestScan.get().scanId);
    }
    
    public record CweStatistics(
        String cweId,
        String name,
        Long totalCount,
        Long criticalCount,
        Long highCount
    ) {}
    
    public record ScanDurationAnalysis(
        String workflowId,
        String runId,
        Long durationMs,
        String analysis
    ) {}
    
    public record ScanComparison(
        UUID scanId1,
        UUID scanId2,
        String summary
    ) {}
    
    /**
     * Analyze workflow results including history, errors, and status
     */
    public WorkflowResults analyzeWorkflowResults(String workflowId, String runId) {
        Log.info("Analyzing workflow results for: " + workflowId + ", runId: " + runId);
        
        try {
            // Get workflow execution info
            ScanMetadata metadata = temporalService.extractScanMetadata(workflowId, runId);
            
            if (metadata.workflowId() == null || metadata.status() == null) {
                Log.warn("Workflow not found in Temporal: " + workflowId);
                return new WorkflowResults(
                    workflowId,
                    runId,
                    "NOT_FOUND",
                    null,
                    "Workflow not found in Temporal. Check if workflow ID is correct.",
                    null,
                    null
                );
            }
            
            // Get workflow history for detailed analysis
            var historyOpt = temporalService.getWorkflowHistory(workflowId, runId);
            
            StringBuilder analysis = new StringBuilder();
            List<FailedActivity> failedActivities = new java.util.ArrayList<>();
            String errorMessage = null;
            
            // Analyze workflow status
            String status = metadata.status();
            analysis.append(String.format("Workflow Status: %s\n", status));
            
            if (historyOpt.isPresent()) {
                var history = historyOpt.get();
                var events = history.events();
                
                // Count different event types
                long activityScheduled = events.stream()
                    .filter(e -> e.getEventType() == io.temporal.api.enums.v1.EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED)
                    .count();
                long activityCompleted = events.stream()
                    .filter(e -> e.getEventType() == io.temporal.api.enums.v1.EventType.EVENT_TYPE_ACTIVITY_TASK_COMPLETED)
                    .count();
                long activityFailed = events.stream()
                    .filter(e -> e.getEventType() == io.temporal.api.enums.v1.EventType.EVENT_TYPE_ACTIVITY_TASK_FAILED)
                    .count();
                long workflowFailed = events.stream()
                    .filter(e -> e.getEventType() == io.temporal.api.enums.v1.EventType.EVENT_TYPE_WORKFLOW_EXECUTION_FAILED)
                    .count();
                
                analysis.append(String.format("Activities: %d scheduled, %d completed, %d failed\n", 
                    activityScheduled, activityCompleted, activityFailed));
                
                // Extract failed activities
                for (var event : events) {
                    if (event.getEventType() == io.temporal.api.enums.v1.EventType.EVENT_TYPE_ACTIVITY_TASK_FAILED) {
                        var failedEvent = event.getActivityTaskFailedEventAttributes();
                        if (failedEvent != null) {
                            // Get activity type from scheduled event (need to look back)
                            String activityName = "Unknown Activity";
                            // Try to find the corresponding scheduled event
                            for (var prevEvent : events) {
                                if (prevEvent.getEventId() < event.getEventId() &&
                                    prevEvent.getEventType() == io.temporal.api.enums.v1.EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED) {
                                    var scheduledAttrs = prevEvent.getActivityTaskScheduledEventAttributes();
                                    if (scheduledAttrs != null && scheduledAttrs.getActivityType() != null) {
                                        activityName = scheduledAttrs.getActivityType().getName();
                                        break;
                                    }
                                }
                            }
                            
                            String error = failedEvent.getFailure() != null ? 
                                (failedEvent.getFailure().getMessage() != null ? 
                                    failedEvent.getFailure().getMessage() : 
                                    failedEvent.getFailure().toString()) : "Unknown error";
                            failedActivities.add(new FailedActivity(activityName, error));
                        }
                    }
                    
                    if (event.getEventType() == io.temporal.api.enums.v1.EventType.EVENT_TYPE_WORKFLOW_EXECUTION_FAILED) {
                        var failedEvent = event.getWorkflowExecutionFailedEventAttributes();
                        if (failedEvent != null && failedEvent.getFailure() != null) {
                            errorMessage = failedEvent.getFailure().getMessage();
                            analysis.append(String.format("Workflow Failure: %s\n", errorMessage));
                        }
                    }
                }
                
                // Analyze timeline
                if (!events.isEmpty()) {
                    var firstEvent = events.get(0);
                    var lastEvent = events.get(events.size() - 1);
                    
                    long startTime = firstEvent.getEventTime().getSeconds();
                    long endTime = lastEvent.getEventTime().getSeconds();
                    long totalTime = endTime - startTime;
                    
                    analysis.append(String.format("Total execution time: %d seconds\n", totalTime));
                    analysis.append(String.format("Total events: %d\n", events.size()));
                }
            } else {
                analysis.append("Workflow history unavailable.\n");
            }
            
            // Add metadata info
            if (metadata.team() != null) {
                analysis.append(String.format("Team: %s\n", metadata.team()));
            }
            if (metadata.project() != null) {
                analysis.append(String.format("Project: %s\n", metadata.project()));
            }
            if (metadata.scanType() != null) {
                analysis.append(String.format("Scan Type: %s\n", metadata.scanType()));
            }
            
            return new WorkflowResults(
                workflowId,
                metadata.runId(),
                status,
                metadata.durationMs(),
                analysis.toString(),
                errorMessage,
                failedActivities.isEmpty() ? null : failedActivities
            );
        } catch (Exception e) {
            Log.error("Error analyzing workflow results for " + workflowId, e);
            return new WorkflowResults(
                workflowId,
                runId,
                "ERROR",
                null,
                "Error analyzing workflow: " + e.getMessage(),
                e.getMessage(),
                null
            );
        }
    }
    
    public record WorkflowResults(
        String workflowId,
        String runId,
        String status,
        Long durationMs,
        String analysis,
        String errorMessage,
        List<FailedActivity> failedActivities
    ) {}
    
    public record FailedActivity(
        String name,
        String error
    ) {}
}
