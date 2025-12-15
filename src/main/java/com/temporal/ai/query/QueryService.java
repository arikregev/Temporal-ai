package com.temporal.ai.query;

import com.temporal.ai.data.QueryLayer;
import com.temporal.ai.data.QueryLayer.CweStatistics;
import com.temporal.ai.data.QueryLayer.ScanComparison;
import com.temporal.ai.data.QueryLayer.ScanDurationAnalysis;
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
    
    private static final Pattern SCAN_ID_PATTERN = Pattern.compile("scan\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORKFLOW_ID_PATTERN = Pattern.compile("workflow\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEAM_PATTERN = Pattern.compile("team\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)\\s+days?", Pattern.CASE_INSENSITIVE);
    
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
        
        // Classify intent using LLM
        LlmClient.QueryIntent intent = llmClient.classifyIntent(query);
        
        // Route to appropriate handler
        return switch (intent.type()) {
            case SCAN_DURATION -> handleScanDurationQuery(query);
            case SCAN_CHANGES -> handleScanChangesQuery(query, team);
            case CWE_STATISTICS -> handleCweStatisticsQuery(query, team);
            case FINDING_EXPLANATION -> handleFindingExplanationQuery(query);
            case POLICY_QUERY -> handlePolicyQuery(query);
            default -> handleGeneralQuery(query, team);
        };
    }
    
    private QueryResponse handleScanDurationQuery(String query) {
        Matcher workflowMatcher = WORKFLOW_ID_PATTERN.matcher(query);
        Matcher scanMatcher = SCAN_ID_PATTERN.matcher(query);
        
        String workflowId = null;
        String runId = null;
        
        if (workflowMatcher.find()) {
            workflowId = workflowMatcher.group(1);
        } else if (scanMatcher.find()) {
            String scanIdStr = scanMatcher.group(1);
            // Try to find scan and get workflow ID
            try {
                UUID scanId = UUID.fromString(scanIdStr);
                var scanOpt = queryLayer.getScanById(scanId);
                if (scanOpt.isPresent()) {
                    workflowId = scanOpt.get().workflowId;
                    runId = scanOpt.get().runId;
                }
            } catch (IllegalArgumentException e) {
                // Not a UUID, treat as workflow ID
                workflowId = scanIdStr;
            }
        } else {
            // Extract workflow ID from query using LLM
            String extracted = llmClient.generate(
                "Extract the workflow ID or scan ID from this query: " + query + 
                "\nRespond with only the ID, nothing else."
            );
            workflowId = extracted.trim();
        }
        
        if (workflowId == null) {
            return new QueryResponse(
                QueryResponse.ResponseSource.LLM,
                "Could not identify workflow or scan ID from the query.",
                null,
                0.0
            );
        }
        
        ScanDurationAnalysis analysis = queryLayer.analyzeScanDuration(workflowId, runId);
        
        // Enhance with LLM explanation
        String enhancedAnswer = llmClient.generate(
            String.format(
                "A security scan (workflow: %s) took %d milliseconds. " +
                "Analysis: %s. " +
                "Explain why this scan might have taken this long in simple terms.",
                workflowId,
                analysis.durationMs() != null ? analysis.durationMs() : 0,
                analysis.analysis()
            )
        );
        
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
    
    private QueryResponse handleGeneralQuery(String query, String team) {
        // Use LLM to generate answer, potentially with context from query layer
        String context = "You are a security analyst assistant. Answer the following question: " + query;
        if (team != null) {
            context += "\nContext: Team is " + team;
        }
        
        String answer = llmClient.generate(context);
        
        return new QueryResponse(
            QueryResponse.ResponseSource.LLM,
            answer,
            null,
            0.8
        );
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
