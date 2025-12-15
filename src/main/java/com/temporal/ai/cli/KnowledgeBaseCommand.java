package com.temporal.ai.cli;

import com.temporal.ai.data.entity.KnowledgeBase;
import com.temporal.ai.knowledge.KnowledgeBaseService;
import com.temporal.ai.knowledge.KnowledgeBaseService.KnowledgeBaseMatch;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.List;
import java.util.UUID;

@CommandLine.Command(name = "kb", description = "Manage knowledge base entries")
public class KnowledgeBaseCommand implements Runnable {
    
    @Inject
    KnowledgeBaseService knowledgeBaseService;
    
    @CommandLine.Option(names = {"-c", "--create"}, description = "Create a new Q&A pair")
    boolean create;
    
    @CommandLine.Option(names = {"-q", "--question"}, description = "Question")
    String question;
    
    @CommandLine.Option(names = {"-a", "--answer"}, description = "Answer")
    String answer;
    
    @CommandLine.Option(names = {"-u", "--user"}, description = "User/creator")
    String user;
    
    @CommandLine.Option(names = {"-t", "--team"}, description = "Team")
    String team;
    
    @CommandLine.Option(names = {"-s", "--search"}, description = "Search for matching answers")
    String searchQuery;
    
    @CommandLine.Option(names = {"-l", "--list"}, description = "List all entries")
    boolean list;
    
    @CommandLine.Option(names = {"-d", "--delete"}, description = "Delete entry by ID")
    String deleteId;
    
    @Override
    public void run() {
        if (create) {
            if (question == null || answer == null || user == null) {
                System.err.println("Error: --question, --answer, and --user are required for creation");
                return;
            }
            KnowledgeBase kb = knowledgeBaseService.createQAPair(
                question, answer, user, team, null, null
            );
            System.out.println("Created knowledge base entry: " + kb.kbId);
        } else if (searchQuery != null) {
            List<KnowledgeBaseMatch> matches = knowledgeBaseService.findMatchingAnswers(
                searchQuery, team, 10
            );
            System.out.println("Found " + matches.size() + " matches:");
            for (KnowledgeBaseMatch match : matches) {
                System.out.println("\nSimilarity: " + match.similarity());
                System.out.println("Question: " + match.entry().question);
                System.out.println("Answer: " + match.entry().answer);
            }
        } else if (list) {
            List<KnowledgeBase> entries = knowledgeBaseService.getAllEntries(team, true);
            System.out.println("Knowledge base entries (" + entries.size() + "):");
            for (KnowledgeBase kb : entries) {
                System.out.println("\nID: " + kb.kbId);
                System.out.println("Question: " + kb.question);
                System.out.println("Answer: " + kb.answer);
                System.out.println("Team: " + kb.team);
                System.out.println("Usage: " + kb.usageCount);
            }
        } else if (deleteId != null) {
            try {
                UUID kbId = UUID.fromString(deleteId);
                knowledgeBaseService.deleteQAPair(kbId);
                System.out.println("Deleted knowledge base entry: " + kbId);
            } catch (IllegalArgumentException e) {
                System.err.println("Error: Invalid UUID: " + deleteId);
            }
        } else {
            System.out.println("Use --create, --search, --list, or --delete");
        }
    }
}
