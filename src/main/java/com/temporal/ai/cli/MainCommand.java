package com.temporal.ai.cli;

import picocli.CommandLine;

@CommandLine.Command(
    name = "temporal-ai",
    description = "Temporal Security Analyst Interface CLI",
    subcommands = {
        QueryCommand.class,
        KnowledgeBaseCommand.class,
        PolicyCommand.class
    }
)
public class MainCommand implements Runnable {
    
    @Override
    public void run() {
        System.out.println("Temporal Security Analyst Interface");
        System.out.println("Use --help for usage information");
    }
}
