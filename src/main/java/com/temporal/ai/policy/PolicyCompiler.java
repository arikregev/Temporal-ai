package com.temporal.ai.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.temporal.ai.llm.LlmClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

@ApplicationScoped
public class PolicyCompiler {
    
    @Inject
    LlmClient llmClient;
    
    @Inject
    ObjectMapper objectMapper;
    
    /**
     * Compile a natural language policy into executable rules
     */
    public CompiledPolicy compilePolicy(String naturalLanguagePolicy) {
        Log.info("Compiling policy: " + naturalLanguagePolicy);
        
        // Use LLM to extract policy components
        String prompt = String.format("""
            Parse this security policy statement and extract the components:
            
            Policy: %s
            
            Extract:
            1. Action (what to do: BLOCK, WARN, ALLOW, etc.)
            2. Condition (when to apply: severity, CWE, team, environment, etc.)
            3. Scope (what it applies to: builds, scans, findings, etc.)
            4. Parameters (any specific values like severity levels, CWE IDs, etc.)
            
            Format your response as JSON with keys: action, condition, scope, parameters
            """, naturalLanguagePolicy);
        
        String llmResponse = llmClient.generate(prompt);
        
        try {
            Map<String, Object> parsed = objectMapper.readValue(llmResponse, Map.class);
            
            String action = (String) parsed.getOrDefault("action", "WARN");
            String condition = (String) parsed.getOrDefault("condition", "");
            String scope = (String) parsed.getOrDefault("scope", "scans");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) parsed.getOrDefault("parameters", Map.of());
            
            // Generate executable rule
            PolicyRule rule = generateRule(action, condition, scope, parameters);
            
            return new CompiledPolicy(
                naturalLanguagePolicy,
                action,
                condition,
                scope,
                parameters,
                rule,
                generateRuleCode(rule)
            );
        } catch (Exception e) {
            Log.error("Failed to parse policy", e);
            // Fallback: create a simple rule
            PolicyRule fallbackRule = new PolicyRule(
                "WARN",
                "severity == 'CRITICAL'",
                "findings"
            );
            return new CompiledPolicy(
                naturalLanguagePolicy,
                "WARN",
                "Critical findings",
                "findings",
                Map.of(),
                fallbackRule,
                generateRuleCode(fallbackRule)
            );
        }
    }
    
    /**
     * Generate a policy rule from components
     */
    private PolicyRule generateRule(String action, String condition, String scope, Map<String, Object> parameters) {
        // Build condition expression
        StringBuilder conditionExpr = new StringBuilder();
        
        // Parse common patterns
        if (condition.toLowerCase().contains("critical")) {
            conditionExpr.append("severity == 'CRITICAL'");
        } else if (condition.toLowerCase().contains("high")) {
            conditionExpr.append("severity == 'HIGH'");
        }
        
        if (condition.toLowerCase().contains("reachable")) {
            if (conditionExpr.length() > 0) conditionExpr.append(" AND ");
            conditionExpr.append("isReachable == true");
        }
        
        if (condition.toLowerCase().contains("prod") || condition.toLowerCase().contains("production")) {
            if (conditionExpr.length() > 0) conditionExpr.append(" AND ");
            conditionExpr.append("environment == 'production'");
        }
        
        if (parameters.containsKey("cwe")) {
            if (conditionExpr.length() > 0) conditionExpr.append(" AND ");
            conditionExpr.append("cweId == '").append(parameters.get("cwe")).append("'");
        }
        
        if (conditionExpr.length() == 0) {
            conditionExpr.append("true"); // Default: always apply
        }
        
        return new PolicyRule(
            action.toUpperCase(),
            conditionExpr.toString(),
            scope
        );
    }
    
    /**
     * Generate executable code for a rule
     */
    private String generateRuleCode(PolicyRule rule) {
        return String.format("""
            function evaluatePolicy(finding, scan) {
                // Condition: %s
                const condition = %s;
                
                if (condition) {
                    // Action: %s
                    return {
                        action: '%s',
                        message: 'Policy violation detected',
                        finding: finding,
                        scan: scan
                    };
                }
                
                return null; // No violation
            }
            """, rule.condition(), convertConditionToJS(rule.condition()), rule.action(), rule.action());
    }
    
    /**
     * Convert condition expression to JavaScript
     */
    private String convertConditionToJS(String condition) {
        // Simple conversion - in production, use a proper expression parser
        String js = condition
            .replace("==", "===")
            .replace("severity", "finding.severity")
            .replace("isReachable", "finding.isReachable")
            .replace("environment", "scan.environment")
            .replace("cweId", "finding.cweId");
        
        return js;
    }
    
    /**
     * Validate a compiled policy
     */
    public boolean validatePolicy(CompiledPolicy policy) {
        if (policy.action() == null || policy.action().isEmpty()) {
            return false;
        }
        
        List<String> validActions = List.of("BLOCK", "WARN", "ALLOW", "NOTIFY");
        if (!validActions.contains(policy.action().toUpperCase())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Test a policy against a finding
     */
    public PolicyEvaluationResult evaluatePolicy(CompiledPolicy policy, Object finding, Object scan) {
        // In production, this would execute the generated rule code
        // For now, return a simple evaluation
        return new PolicyEvaluationResult(
            policy.action(),
            "Policy evaluation would be performed by executing the rule code",
            false // Would be determined by actual evaluation
        );
    }
    
    public record CompiledPolicy(
        String originalPolicy,
        String action,
        String condition,
        String scope,
        Map<String, Object> parameters,
        PolicyRule rule,
        String ruleCode
    ) {}
    
    public record PolicyRule(
        String action,
        String condition,
        String scope
    ) {}
    
    public record PolicyEvaluationResult(
        String action,
        String message,
        boolean violated
    ) {}
}
