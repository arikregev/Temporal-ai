package com.temporal.ai.explanation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.temporal.ai.data.QueryLayer;
import com.temporal.ai.data.entity.Finding;
import com.temporal.ai.llm.LlmClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ExplanationService {
    
    @Inject
    LlmClient llmClient;
    
    @Inject
    QueryLayer queryLayer;
    
    @Inject
    ObjectMapper objectMapper;
    
    // Simple in-memory cache (in production, use Redis or similar)
    private final Map<UUID, FindingExplanation> explanationCache = new ConcurrentHashMap<>();
    
    /**
     * Explain a finding in developer-friendly terms
     */
    public FindingExplanation explainFinding(UUID findingId) {
        // Check cache first
        if (explanationCache.containsKey(findingId)) {
            return explanationCache.get(findingId);
        }
        
        Optional<Finding> findingOpt = Optional.ofNullable(Finding.findById(findingId));
        if (findingOpt.isEmpty()) {
            throw new IllegalArgumentException("Finding not found: " + findingId);
        }
        
        Finding finding = findingOpt.get();
        FindingExplanation explanation = generateExplanation(finding);
        
        // Cache the explanation
        explanationCache.put(findingId, explanation);
        
        return explanation;
    }
    
    /**
     * Explain a finding from raw tool output
     */
    public FindingExplanation explainFromRawOutput(Map<String, Object> rawOutput, String tool) {
        FindingExplanation explanation = generateExplanationFromRaw(rawOutput, tool);
        return explanation;
    }
    
    private FindingExplanation generateExplanation(Finding finding) {
        String prompt = buildExplanationPrompt(finding);
        String llmResponse = llmClient.generate(prompt);
        
        // Parse LLM response to extract structured information
        return parseExplanationResponse(llmResponse, finding);
    }
    
    private FindingExplanation generateExplanationFromRaw(Map<String, Object> rawOutput, String tool) {
        String prompt = String.format("""
            Explain this security finding from %s tool in developer-friendly terms.
            
            Raw Output:
            %s
            
            Provide:
            1. A short, actionable explanation (2-3 sentences)
            2. Steps to reproduce the issue
            3. Code pointers (file paths and line numbers if available)
            4. Potential impact
            5. Recommended fix
            
            Format your response as JSON with keys: explanation, stepsToReproduce, codePointers, impact, recommendedFix
            """, tool, formatRawOutput(rawOutput));
        
        String llmResponse = llmClient.generate(prompt);
        
        try {
            // Try to parse as JSON first
            Map<String, Object> parsed = objectMapper.readValue(llmResponse, Map.class);
            return new FindingExplanation(
                (String) parsed.getOrDefault("explanation", llmResponse),
                (String) parsed.getOrDefault("stepsToReproduce", ""),
                (String) parsed.getOrDefault("codePointers", ""),
                (String) parsed.getOrDefault("impact", ""),
                (String) parsed.getOrDefault("recommendedFix", "")
            );
        } catch (Exception e) {
            // Fallback to plain text
            Log.warn("Failed to parse LLM response as JSON, using plain text", e);
            return new FindingExplanation(
                llmResponse,
                "",
                "",
                "",
                ""
            );
        }
    }
    
    private String buildExplanationPrompt(Finding finding) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Explain this security finding in developer-friendly terms:\n\n");
        prompt.append("Title: ").append(finding.title).append("\n");
        prompt.append("CWE: ").append(finding.cwe.cweId).append(" - ").append(finding.cwe.name).append("\n");
        prompt.append("Severity: ").append(finding.severity).append("\n");
        
        if (finding.description != null) {
            prompt.append("Description: ").append(finding.description).append("\n");
        }
        
        if (finding.filePath != null) {
            prompt.append("File: ").append(finding.filePath);
            if (finding.lineNumber != null) {
                prompt.append(":").append(finding.lineNumber);
            }
            prompt.append("\n");
        }
        
        if (finding.rawOutput != null) {
            prompt.append("Raw Tool Output:\n").append(formatRawOutput(finding.rawOutput)).append("\n");
        }
        
        prompt.append("""
            
            Provide:
            1. A short, actionable explanation (2-3 sentences)
            2. Steps to reproduce the issue
            3. Code pointers (file paths and line numbers)
            4. Potential impact
            5. Recommended fix
            
            Format your response as JSON with keys: explanation, stepsToReproduce, codePointers, impact, recommendedFix
            """);
        
        return prompt.toString();
    }
    
    private FindingExplanation parseExplanationResponse(String response, Finding finding) {
        try {
            // Try to parse as JSON
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            return new FindingExplanation(
                (String) parsed.getOrDefault("explanation", response),
                (String) parsed.getOrDefault("stepsToReproduce", ""),
                (String) parsed.getOrDefault("codePointers", 
                    finding.filePath != null ? finding.filePath + (finding.lineNumber != null ? ":" + finding.lineNumber : "") : ""),
                (String) parsed.getOrDefault("impact", ""),
                (String) parsed.getOrDefault("recommendedFix", "")
            );
        } catch (Exception e) {
            // Fallback: create explanation from plain text
            Log.warn("Failed to parse explanation response as JSON", e);
            return new FindingExplanation(
                response,
                "",
                finding.filePath != null ? finding.filePath + (finding.lineNumber != null ? ":" + finding.lineNumber : "") : "",
                "",
                ""
            );
        }
    }
    
    private String formatRawOutput(Map<String, Object> rawOutput) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rawOutput);
        } catch (Exception e) {
            return rawOutput.toString();
        }
    }
    
    /**
     * Clear explanation cache (useful for testing or when findings are updated)
     */
    public void clearCache() {
        explanationCache.clear();
    }
    
    /**
     * Clear cache for a specific finding
     */
    public void clearCache(UUID findingId) {
        explanationCache.remove(findingId);
    }
    
    public record FindingExplanation(
        String explanation,
        String stepsToReproduce,
        String codePointers,
        String impact,
        String recommendedFix
    ) {}
}
