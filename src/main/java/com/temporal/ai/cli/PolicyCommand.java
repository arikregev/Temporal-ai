package com.temporal.ai.cli;

import com.temporal.ai.policy.PolicyCompiler;
import com.temporal.ai.policy.PolicyCompiler.CompiledPolicy;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(name = "policy", description = "Compile and manage policies")
public class PolicyCommand implements Runnable {
    
    @Inject
    PolicyCompiler policyCompiler;
    
    @CommandLine.Option(names = {"-c", "--compile"}, description = "Policy statement to compile")
    String policy;
    
    @Override
    public void run() {
        if (policy == null) {
            System.err.println("Error: --compile requires a policy statement");
            return;
        }
        
        CompiledPolicy compiled = policyCompiler.compilePolicy(policy);
        
        System.out.println("Original Policy: " + compiled.originalPolicy());
        System.out.println("\nCompiled Policy:");
        System.out.println("Action: " + compiled.action());
        System.out.println("Condition: " + compiled.condition());
        System.out.println("Scope: " + compiled.scope());
        System.out.println("\nRule Code:");
        System.out.println(compiled.ruleCode());
    }
}
