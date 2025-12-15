package com.temporal.ai.cli;

import com.temporal.ai.query.QueryService;
import com.temporal.ai.query.QueryService.QueryResponse;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(name = "query", description = "Process natural language queries")
public class QueryCommand implements Runnable {
    
    @Inject
    QueryService queryService;
    
    @CommandLine.Parameters(index = "0", description = "The query to process")
    String query;
    
    @CommandLine.Option(names = {"-t", "--team"}, description = "Team context")
    String team;
    
    @Override
    public void run() {
        QueryResponse response = queryService.processQuery(query, team);
        System.out.println("Source: " + response.source());
        System.out.println("Confidence: " + response.confidence());
        System.out.println("\nAnswer:");
        System.out.println(response.answer());
        if (response.data() != null) {
            System.out.println("\nData: " + response.data());
        }
    }
}
