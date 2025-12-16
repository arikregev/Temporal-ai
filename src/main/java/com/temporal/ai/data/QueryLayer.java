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
}
