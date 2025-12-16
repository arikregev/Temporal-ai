package com.temporal.ai.query;

import com.temporal.ai.data.QueryLayer;
import com.temporal.ai.data.QueryLayer.CweStatistics;
import com.temporal.ai.data.QueryLayer.ScanComparison;
import com.temporal.ai.data.QueryLayer.ScanDurationAnalysis;
import com.temporal.ai.data.QueryLayer.WorkflowResults;
import com.temporal.ai.data.QueryLayer.FailedActivity;
import com.temporal.ai.knowledge.KnowledgeBaseService.KnowledgeBaseMatch;
import com.temporal.ai.knowledge.KnowledgeBaseService;
import com.temporal.ai.llm.LlmClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class QueryService {
    
    @Inject
    LlmClient llmClient;
    
    @Inject
    QueryLayer queryLayer;
    
    @Inject
    KnowledgeBaseService knowledgeBaseService;
    
    private static final Pattern SCAN_ID_PATTERN = Pattern.compile("scan\\s+([a-zA-Z0-9_:-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORKFLOW_ID_PATTERN = Pattern.compile("workflow\\s+([a-zA-Z0-9_:-]+)", Pattern.CASE_INSENSITIVE);
    // Pattern to match UUIDs or workflow IDs in various formats
    private static final Pattern UUID_PATTERN = Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})", Pattern.CASE_INSENSITIVE);
    // Pattern to match workflow IDs (alphanumeric with dashes/underscores/colons, typically 20+ chars)
    private static final Pattern WORKFLOW_ID_IN_TEXT_PATTERN = Pattern.compile("([a-zA-Z0-9_:-]{20,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEAM_PATTERN = Pattern.compile("team\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)\\s+days?", Pattern.CASE_INSENSITIVE);
    
    /**
     * Fallback intent classification using pattern matching when LLM is unavailable
     */
    private LlmClient.QueryIntent classifyIntentByPattern(String query) {
        String lowerQuery = query.toLowerCase();
        
        // Check if query contains workflow/scan IDs - prioritize workflow queries
        if (WORKFLOW_ID_PATTERN.matcher(query).find() || 
            UUID_PATTERN.matcher(query).find() ||
            WORKFLOW_ID_IN_TEXT_PATTERN.matcher(query).find() ||
            lowerQuery.contains("workflow") || lowerQuery.contains("scan")) {
            // Check for workflow results queries (what happened, why failed, status)
            if (lowerQuery.contains("what happened") || lowerQuery.contains("why did") || 
                lowerQuery.contains("why failed") || lowerQuery.contains("failed") ||
                lowerQuery.contains("last workflow") || lowerQuery.contains("workflow status") ||
                lowerQuery.contains("workflow result") || lowerQuery.contains("what went wrong")) {
                return new LlmClient.QueryIntent(LlmClient.QueryIntent.IntentType.WORKFLOW_RESULTS, query);
            }
            // If it mentions duration/time with workflow, it's a duration query
            if (lowerQuery.contains("duration") || lowerQuery.contains("took") || 
                lowerQuery.contains("time") || lowerQuery.contains("minutes") || 
                lowerQuery.contains("hours") || lowerQuery.contains("long") ||
                lowerQuery.contains("how long")) {
                return new LlmClient.QueryIntent(LlmClient.QueryIntent.IntentType.SCAN_DURATION, query);
            }
            // If it mentions workflow/scan without specific intent, treat as workflow results
            return new LlmClient.QueryIntent(LlmClient.QueryIntent.IntentType.WORKFLOW_RESULTS, query);
        }
        
        if (lowerQuery.contains("duration") || lowerQuery.contains("took") || lowerQuery.contains("time") || 
            lowerQuery.contains("minutes") || lowerQuery.contains("hours") || lowerQuery.contains("long")) {
            return new LlmClient.QueryIntent(LlmClient.QueryIntent.IntentType.SCAN_DURATION, query);
        }
        if (lowerQuery.contains("change") || lowerQuery.contains("different") || lowerQuery.contains("since") || 
            lowerQuery.contains("compare") || lowerQuery.contains("between")) {
            return new LlmClient.QueryIntent(LlmClient.QueryIntent.IntentType.SCAN_CHANGES, query);
        }
        if (lowerQuery.contains("cwe") || lowerQuery.contains("top") || lowerQuery.contains("recurring") || 
            lowerQuery.contains("statistics") || lowerQuery.contains("count") || lowerQuery.contains("trend")) {
            return new LlmClient.QueryIntent(LlmClient.QueryIntent.IntentType.CWE_STATISTICS, query);
        }
        if (lowerQuery.contains("explain") || lowerQuery.contains("what is") || lowerQuery.contains("finding") || 
            lowerQuery.contains("vulnerability") || lowerQuery.contains("issue")) {
            return new LlmClient.QueryIntent(LlmClient.QueryIntent.IntentType.FINDING_EXPLANATION, query);
        }
        if (lowerQuery.contains("policy") || lowerQuery.contains("rule") || lowerQuery.contains("block") || 
            lowerQuery.contains("allow") || lowerQuery.contains("enforce")) {
            return new LlmClient.QueryIntent(LlmClient.QueryIntent.IntentType.POLICY_QUERY, query);
        }
        
        return new LlmClient.QueryIntent(LlmClient.QueryIntent.IntentType.GENERAL, query);
    }
    
    /**
     * Process a natural language query
     */
    public QueryResponse processQuery(String query, String team) {
        Log.info("Processing query: " + query);
        
        // First, check knowledge base for matching answers
        Optional<KnowledgeBaseMatch> kbMatch = knowledgeBaseService.getBestMatch(query, team);
        if (kbMatch.isPresent() && kbMatch.get().similarity() >= 0.8) {
            KnowledgeBaseMatch match = kbMatch.get();
            knowledgeBaseService.incrementUsageCount(match.entry().kbId);
            return new QueryResponse(
                QueryResponse.ResponseSource.KNOWLEDGE_BASE,
                match.entry().answer,
                null,
                match.similarity()
            );
        }
        
        // Try to classify intent using LLM, fallback to pattern matching if LLM unavailable
        LlmClient.QueryIntent intent;
        try {
            intent = llmClient.classifyIntent(query);
        } catch (Exception e) {
            Log.debug("LLM intent classification unavailable, using pattern matching fallback");
            intent = classifyIntentByPattern(query);
        }
        
        // Route to appropriate handler
        return switch (intent.type()) {
            case SCAN_DURATION -> handleScanDurationQuery(query);
            case SCAN_CHANGES -> handleScanChangesQuery(query, team);
            case CWE_STATISTICS -> handleCweStatisticsQuery(query, team);
            case FINDING_EXPLANATION -> handleFindingExplanationQuery(query);
            case POLICY_QUERY -> handlePolicyQuery(query);
            case WORKFLOW_RESULTS -> handleWorkflowResultsQuery(query, team);
            default -> handleGeneralQuery(query, team);
        };
    }
    
    private QueryResponse handleScanDurationQuery(String query) {
        Log.info("Handling scan duration query: " + query);
        
        String workflowId = null;
        String runId = null;
        
        // Try multiple patterns to extract workflow/scan ID
        Matcher workflowMatcher = WORKFLOW_ID_PATTERN.matcher(query);
        Matcher scanMatcher = SCAN_ID_PATTERN.matcher(query);
        Matcher uuidMatcher = UUID_PATTERN.matcher(query);
        Matcher workflowIdMatcher = WORKFLOW_ID_IN_TEXT_PATTERN.matcher(query);
        
        if (workflowMatcher.find()) {
            workflowId = workflowMatcher.group(1);
            Log.info("Extracted workflow ID from 'workflow X' pattern: " + workflowId);
        } else if (scanMatcher.find()) {
            String scanIdStr = scanMatcher.group(1);
            Log.info("Found scan ID pattern: " + scanIdStr);
            // Try to find scan and get workflow ID
            try {
                UUID scanId = UUID.fromString(scanIdStr);
                var scanOpt = queryLayer.getScanById(scanId);
                if (scanOpt.isPresent()) {
                    workflowId = scanOpt.get().workflowId;
                    runId = scanOpt.get().runId;
                    Log.info("Found scan in DB, workflow ID: " + workflowId);
                } else {
                    Log.warn("Scan ID not found in database: " + scanIdStr);
                }
            } catch (IllegalArgumentException e) {
                // Not a UUID, might be a workflow ID
                workflowId = scanIdStr;
                Log.info("Treating scan ID as workflow ID: " + workflowId);
            }
        } else if (uuidMatcher.find()) {
            String uuidStr = uuidMatcher.group(1);
            Log.info("Found UUID pattern: " + uuidStr);
            // Try as scan ID first
            try {
                UUID scanId = UUID.fromString(uuidStr);
                var scanOpt = queryLayer.getScanById(scanId);
                if (scanOpt.isPresent()) {
                    workflowId = scanOpt.get().workflowId;
                    runId = scanOpt.get().runId;
                    Log.info("Found scan in DB by UUID, workflow ID: " + workflowId);
                } else {
                    // Try as workflow ID directly
                    workflowId = uuidStr;
                    Log.info("UUID not found as scan, trying as workflow ID: " + workflowId);
                }
            } catch (IllegalArgumentException e) {
                workflowId = uuidStr;
            }
        } else if (workflowIdMatcher.find()) {
            // Extract potential workflow ID (long alphanumeric string)
            String potentialId = workflowIdMatcher.group(1);
            Log.info("Found potential workflow ID pattern: " + potentialId);
            // Check if it exists in database as workflow ID
            var scanOpt = queryLayer.getScanByWorkflowId(potentialId);
            if (scanOpt.isPresent()) {
                workflowId = potentialId;
                runId = scanOpt.get().runId;
                Log.info("Found workflow ID in database: " + workflowId);
            } else {
                // Try it anyway - might be a workflow ID not yet in DB
                workflowId = potentialId;
                Log.info("Trying potential workflow ID: " + workflowId);
            }
        } else {
            // Try to extract workflow ID using LLM as last resort
            Log.info("No pattern match found, trying LLM extraction");
            try {
                String extracted = llmClient.generate(
                    "Extract the workflow ID or scan ID in format XXXX:XXXX from this query: " + query +
                    "\nRespond with only the ID, nothing else. No comments. If no ID found, respond with 'NONE'."
                );
                String trimmed = extracted.trim();
                if (!trimmed.equalsIgnoreCase("NONE") && !trimmed.isEmpty() && 
                    !trimmed.contains("unavailable") && !trimmed.contains("error")) {
                    workflowId = trimmed;
                    Log.info("LLM extracted workflow ID: " + workflowId);
                }
            } catch (Exception e) {
                Log.debug("LLM extraction failed: " + e.getMessage());
            }
        }
        
        if (workflowId == null || workflowId.isEmpty()) {
            Log.warn("Could not extract workflow ID from query: " + query);
            return new QueryResponse(
                QueryResponse.ResponseSource.LLM,
                "Could not identify workflow or scan ID from the query. Please provide a workflow ID or scan ID.",
                null,
                0.0
            );
        }
        
        Log.info("Querying Temporal for workflow: " + workflowId + ", runId: " + runId);
        ScanDurationAnalysis analysis = queryLayer.analyzeScanDuration(workflowId, runId);
        Log.info("Temporal query completed. Duration: " + analysis.durationMs() + "ms, Analysis: " + analysis.analysis());
        
        // Enhance with LLM explanation if available
        String enhancedAnswer;
        try {
            enhancedAnswer = llmClient.generate(
                String.format(
                    "A security scan (workflow: %s) took %d milliseconds. " +
                    "Analysis: %s. " +
                    "Explain why this scan might have taken this long in simple terms.",
                    workflowId,
                    analysis.durationMs() != null ? analysis.durationMs() : 0,
                    analysis.analysis()
                )
            );
            // Check if LLM returned error
            if (enhancedAnswer.contains("unavailable") || enhancedAnswer.contains("Error:")) {
                enhancedAnswer = String.format(
                    "Workflow %s analysis:\nDuration: %d milliseconds\n%s",
                    workflowId,
                    analysis.durationMs() != null ? analysis.durationMs() : 0,
                    analysis.analysis()
                );
            }
        } catch (Exception e) {
            Log.debug("LLM enhancement failed, using basic response");
            enhancedAnswer = String.format(
                "Workflow %s analysis:\nDuration: %d milliseconds\n%s",
                workflowId,
                analysis.durationMs() != null ? analysis.durationMs() : 0,
                analysis.analysis()
            );
        }
        
        return new QueryResponse(
            QueryResponse.ResponseSource.QUERY_LAYER,
            enhancedAnswer,
            analysis,
            1.0
        );
    }
    
    private QueryResponse handleScanChangesQuery(String query, String team) {
        if (query.toLowerCase().contains("last green run") || query.toLowerCase().contains("last successful")) {
            ScanComparison comparison = queryLayer.getChangesSinceLastGreenRun(team != null ? team : "default");
            String answer = comparison.summary();
            
            // Enhance with LLM
            String enhancedAnswer = llmClient.generate(
                "Explain what changed between security scans: " + answer + 
                "\nProvide a clear, developer-friendly explanation."
            );
            
            return new QueryResponse(
                QueryResponse.ResponseSource.QUERY_LAYER,
                enhancedAnswer,
                comparison,
                1.0
            );
        }
        
        // Extract scan IDs from query
        Matcher scanMatcher = SCAN_ID_PATTERN.matcher(query);
        List<String> scanIds = scanMatcher.results()
            .map(m -> m.group(1))
            .toList();
        
        if (scanIds.size() >= 2) {
            try {
                UUID scanId1 = UUID.fromString(scanIds.get(0));
                UUID scanId2 = UUID.fromString(scanIds.get(1));
                ScanComparison comparison = queryLayer.compareScans(scanId1, scanId2);
                
                String enhancedAnswer = llmClient.generate(
                    "Explain what changed between these security scans: " + comparison.summary() +
                    "\nProvide a clear, developer-friendly explanation."
                );
                
                return new QueryResponse(
                    QueryResponse.ResponseSource.QUERY_LAYER,
                    enhancedAnswer,
                    comparison,
                    1.0
                );
            } catch (IllegalArgumentException e) {
                // Not UUIDs, fall through to general query
            }
        }
        
        return handleGeneralQuery(query, team);
    }
    
    private QueryResponse handleCweStatisticsQuery(String query, String team) {
        // Extract team and time period
        String targetTeam = team;
        Matcher teamMatcher = TEAM_PATTERN.matcher(query);
        if (teamMatcher.find()) {
            targetTeam = teamMatcher.group(1);
        }
        
        int days = 30; // default
        Matcher daysMatcher = DAYS_PATTERN.matcher(query);
        if (daysMatcher.find()) {
            days = Integer.parseInt(daysMatcher.group(1));
        }
        
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(days, ChronoUnit.DAYS);
        
        List<CweStatistics> stats = queryLayer.getTopCwesByTeam(
            targetTeam != null ? targetTeam : "default",
            startTime,
            endTime,
            10
        );
        
        if (stats.isEmpty()) {
            return new QueryResponse(
                QueryResponse.ResponseSource.QUERY_LAYER,
                "No CWE statistics found for the specified criteria.",
                stats,
                1.0
            );
        }
        
        // Format response
        StringBuilder answer = new StringBuilder();
        answer.append(String.format("Top CWEs for team %s in the last %d days:\n\n", targetTeam, days));
        for (int i = 0; i < stats.size(); i++) {
            CweStatistics stat = stats.get(i);
            answer.append(String.format("%d. %s (%s): %d total findings (%d critical, %d high)\n",
                i + 1, stat.name(), stat.cweId(), stat.totalCount(), 
                stat.criticalCount(), stat.highCount()));
        }
        
        return new QueryResponse(
            QueryResponse.ResponseSource.QUERY_LAYER,
            answer.toString(),
            stats,
            1.0
        );
    }
    
    private QueryResponse handleFindingExplanationQuery(String query) {
        // This will be handled by ExplanationService
        return new QueryResponse(
            QueryResponse.ResponseSource.LLM,
            "Finding explanation requested. Please use the explanation endpoint with a finding ID.",
            null,
            1.0
        );
    }
    
    private QueryResponse handlePolicyQuery(String query) {
        // This will be handled by PolicyCompiler
        return new QueryResponse(
            QueryResponse.ResponseSource.LLM,
            "Policy query received. Please use the policy compiler endpoint.",
            null,
            1.0
        );
    }
    
    private QueryResponse handleWorkflowResultsQuery(String query, String team) {
        Log.info("Handling workflow results query: " + query);
        
        String workflowId = null;
        String runId = null;
        
        String lowerQuery = query.toLowerCase();
        
        // Check if asking about "last workflow"
        if (lowerQuery.contains("last workflow") || lowerQuery.contains("last flow")) {
            Log.info("Query is about last workflow");
            
            // Get the latest workflow for the team
            if (team != null && !team.isEmpty()) {
                var latestScan = queryLayer.getLatestScanByTeam(team);
                if (latestScan.isPresent()) {
                    workflowId = latestScan.get().workflowId;
                    runId = latestScan.get().runId;
                    Log.info("Found latest workflow for team " + team + ": " + workflowId);
                } else {
                    return new QueryResponse(
                        QueryResponse.ResponseSource.QUERY_LAYER,
                        "No workflows found for team: " + team,
                        null,
                        0.5
                    );
                }
            } else {
                // Get latest workflow across all teams
                var latestScans = queryLayer.getScansByStatus("COMPLETED", 1);
                if (latestScans.isEmpty()) {
                    latestScans = queryLayer.getScansByStatus("FAILED", 1);
                }
                if (!latestScans.isEmpty()) {
                    workflowId = latestScans.get(0).workflowId;
                    runId = latestScans.get(0).runId;
                    Log.info("Found latest workflow: " + workflowId);
                } else {
                    return new QueryResponse(
                        QueryResponse.ResponseSource.QUERY_LAYER,
                        "No workflows found in the system.",
                        null,
                        0.5
                    );
                }
            }
        } else {
            // Extract workflow ID from query
            Matcher workflowMatcher = WORKFLOW_ID_PATTERN.matcher(query);
            Matcher uuidMatcher = UUID_PATTERN.matcher(query);
            Matcher workflowIdMatcher = WORKFLOW_ID_IN_TEXT_PATTERN.matcher(query);
            
            if (workflowMatcher.find()) {
                workflowId = workflowMatcher.group(1);
                Log.info("Extracted workflow ID from pattern: " + workflowId);
            } else if (uuidMatcher.find()) {
                String uuidStr = uuidMatcher.group(1);
                // Try as scan ID first
                try {
                    UUID scanId = UUID.fromString(uuidStr);
                    var scanOpt = queryLayer.getScanById(scanId);
                    if (scanOpt.isPresent()) {
                        workflowId = scanOpt.get().workflowId;
                        runId = scanOpt.get().runId;
                        Log.info("Found workflow ID from scan UUID: " + workflowId);
                    } else {
                        workflowId = uuidStr;
                    }
                } catch (IllegalArgumentException e) {
                    workflowId = uuidStr;
                }
            } else if (workflowIdMatcher.find()) {
                String potentialId = workflowIdMatcher.group(1);
                var scanOpt = queryLayer.getScanByWorkflowId(potentialId);
                if (scanOpt.isPresent()) {
                    workflowId = potentialId;
                    runId = scanOpt.get().runId;
                } else {
                    workflowId = potentialId;
                }
            } else {
                // Try LLM extraction
                try {
                    String extracted = llmClient.generate(
                            "Extract the workflow ID or scan ID in format XXXX:XXXX from this query: " + query +
                                    "\nRespond with only the ID, nothing else. No comments. If no ID found, respond with 'NONE'."
                    );
                    String trimmed = extracted.trim();
                    if (!trimmed.equalsIgnoreCase("NONE") && !trimmed.isEmpty() && 
                        !trimmed.contains("unavailable") && !trimmed.contains("error")) {
                        workflowId = trimmed;
                    }
                } catch (Exception e) {
                    Log.debug("LLM extraction failed: " + e.getMessage());
                }
            }
        }
        
        if (workflowId == null || workflowId.isEmpty()) {
            return new QueryResponse(
                QueryResponse.ResponseSource.LLM,
                "Could not identify workflow ID from the query. Please provide a workflow ID or ask about 'last workflow'.",
                null,
                0.0
            );
        }
        
        Log.info("Querying Temporal for workflow results: " + workflowId + ", runId: " + runId);
        WorkflowResults results = queryLayer.analyzeWorkflowResults(workflowId, runId);
        
        // Generate answer using LLM with workflow history context
        String answer;
        try {
            String context = String.format(
                "Workflow ID: %s\nRun ID: %s\nStatus: %s\nDuration: %s\n\nWorkflow History Analysis:\n%s\n\nError Details:\n%s\n\n",
                results.workflowId(),
                results.runId() != null ? results.runId() : "N/A",
                results.status(),
                results.durationMs() != null ? (results.durationMs() / 1000.0) + " seconds" : "N/A",
                results.analysis(),
                results.errorMessage() != null ? results.errorMessage() : "No errors"
            );
            
            String prompt = String.format(
                "Based on the following workflow execution details, answer this question: %s\n\n%s\n\n" +
                "Provide a clear, technical explanation of what happened in this workflow. " +
                "If the workflow failed, explain why it failed based on the error details and history.",
                query,
                context
            );
            
            answer = llmClient.generate(prompt);
            
            // Check if LLM returned error
            if (answer.contains("unavailable") || answer.contains("Error:")) {
                answer = formatWorkflowResultsAnswer(results, query);
            }
        } catch (Exception e) {
            Log.debug("LLM generation failed, using formatted answer");
            answer = formatWorkflowResultsAnswer(results, query);
        }
        
        return new QueryResponse(
            QueryResponse.ResponseSource.QUERY_LAYER,
            answer,
            results,
            1.0
        );
    }
    
    private String formatWorkflowResultsAnswer(WorkflowResults results, String query) {
        StringBuilder answer = new StringBuilder();
        
        answer.append(String.format("Workflow: %s\n", results.workflowId()));
        if (results.runId() != null) {
            answer.append(String.format("Run ID: %s\n", results.runId()));
        }
        answer.append(String.format("Status: %s\n", results.status()));
        
        if (results.durationMs() != null) {
            answer.append(String.format("Duration: %.2f seconds\n", results.durationMs() / 1000.0));
        }
        
        answer.append("\n").append(results.analysis());
        
        if (results.errorMessage() != null && !results.errorMessage().isEmpty()) {
            answer.append("\n\nError: ").append(results.errorMessage());
        }
        
        if (results.failedActivities() != null && !results.failedActivities().isEmpty()) {
            answer.append("\n\nFailed Activities:\n");
            results.failedActivities().forEach(activity -> 
                answer.append(String.format("- %s: %s\n", activity.name(), activity.error()))
            );
        }
        
        return answer.toString();
    }
    
    private QueryResponse handleGeneralQuery(String query, String team) {
        // Check if query mentions workflows/scans - try to extract and query Temporal
        String lowerQuery = query.toLowerCase();
        if (lowerQuery.contains("workflow") || lowerQuery.contains("scan") || 
            WORKFLOW_ID_PATTERN.matcher(query).find() ||
            UUID_PATTERN.matcher(query).find() ||
            WORKFLOW_ID_IN_TEXT_PATTERN.matcher(query).find()) {
            
            Log.info("General query mentions workflow/scan, attempting to extract and query Temporal");
            // Try to handle as workflow query
            QueryResponse workflowResponse = handleScanDurationQuery(query);
            if (workflowResponse.confidence() > 0.0) {
                return workflowResponse;
            }
        }
        
        // Use LLM to generate answer, potentially with context from query layer
        String context = "You are a security analyst assistant. Answer the following question: " + query;
        if (team != null) {
            context += "\nContext: Team is " + team;
        }
        
        try {
            String answer = llmClient.generate(context);
            
            // Check if LLM returned an error message
            if (answer.contains("unavailable") || answer.contains("Error:")) {
                // Fallback to a basic response
                return new QueryResponse(
                    QueryResponse.ResponseSource.QUERY_LAYER,
                    "I can help you with security analyst queries. Please try asking about: scan duration, scan changes, CWE statistics, or finding explanations.",
                    null,
                    0.5
                );
            }
            
            return new QueryResponse(
                QueryResponse.ResponseSource.LLM,
                answer,
                null,
                0.8
            );
        } catch (Exception e) {
            Log.debug("LLM generation failed in general query handler, returning fallback response");
            return new QueryResponse(
                QueryResponse.ResponseSource.QUERY_LAYER,
                "I can help you with security analyst queries. Please try asking about: scan duration, scan changes, CWE statistics, or finding explanations.",
                null,
                0.5
            );
        }
    }
    
    public record QueryResponse(
        ResponseSource source,
        String answer,
        Object data,
        double confidence
    ) {
        public enum ResponseSource {
            KNOWLEDGE_BASE,
            QUERY_LAYER,
            LLM
        }
    }
}
