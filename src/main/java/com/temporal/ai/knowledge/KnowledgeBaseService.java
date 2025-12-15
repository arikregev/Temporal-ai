package com.temporal.ai.knowledge;

import com.temporal.ai.data.entity.KnowledgeBase;
import com.temporal.ai.llm.LlmClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class KnowledgeBaseService {
    
    @Inject
    LlmClient llmClient;
    
    private static final double SIMILARITY_THRESHOLD = 0.7;
    
    /**
     * Create a new Q&A pair
     */
    @Transactional
    public KnowledgeBase createQAPair(String question, String answer, String createdBy, 
                                      String team, String project, List<String> contextTags) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.kbId = UUID.randomUUID();
        kb.question = question;
        kb.answer = answer;
        kb.createdBy = createdBy;
        kb.team = team;
        kb.project = project;
        kb.contextTags = contextTags != null ? contextTags.toArray(new String[0]) : null;
        kb.isActive = true;
        kb.approvalStatus = "PENDING";
        kb.version = 1;
        kb.createdAt = Instant.now();
        kb.updatedAt = Instant.now();
        
        kb.persist();
        
        // Generate and store embedding
        generateAndStoreEmbedding(kb);
        
        return kb;
    }
    
    /**
     * Update an existing Q&A pair
     */
    @Transactional
    public KnowledgeBase updateQAPair(UUID kbId, String question, String answer, 
                                      String updatedBy, List<String> contextTags) {
        KnowledgeBase kb = KnowledgeBase.findById(kbId);
        if (kb == null) {
            throw new IllegalArgumentException("Knowledge base entry not found: " + kbId);
        }
        
        kb.question = question;
        kb.answer = answer;
        kb.updatedBy = updatedBy;
        kb.contextTags = contextTags != null ? contextTags.toArray(new String[0]) : kb.contextTags;
        kb.version = kb.version + 1;
        kb.updatedAt = Instant.now();
        
        kb.persist();
        
        // Regenerate embedding
        generateAndStoreEmbedding(kb);
        
        return kb;
    }
    
    /**
     * Delete a Q&A pair
     */
    @Transactional
    public void deleteQAPair(UUID kbId) {
        KnowledgeBase kb = KnowledgeBase.findById(kbId);
        if (kb != null) {
            kb.delete();
        }
    }
    
    /**
     * Find matching Q&A pairs using semantic search
     */
    public List<KnowledgeBaseMatch> findMatchingAnswers(String query, String team, int limit) {
        // Generate embedding for the query
        List<Double> queryEmbedding = llmClient.generateEmbedding(query);
        
        // Get all active knowledge base entries
        List<KnowledgeBase> allEntries = KnowledgeBase.find("isActive = true").list();
        
        // Filter by team if specified
        if (team != null && !team.isEmpty()) {
            allEntries = allEntries.stream()
                .filter(kb -> team.equals(kb.team) || kb.team == null)
                .collect(Collectors.toList());
        }
        
        // Calculate similarity scores
        List<KnowledgeBaseMatch> matches = new ArrayList<>();
        for (KnowledgeBase kb : allEntries) {
            List<Double> kbEmbedding = getEmbeddingForKb(kb);
            if (kbEmbedding != null && !kbEmbedding.isEmpty()) {
                double similarity = cosineSimilarity(queryEmbedding, kbEmbedding);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    matches.add(new KnowledgeBaseMatch(kb, similarity));
                }
            }
        }
        
        // Sort by similarity descending and limit
        return matches.stream()
            .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Get best matching answer for a query
     */
    public Optional<KnowledgeBaseMatch> getBestMatch(String query, String team) {
        List<KnowledgeBaseMatch> matches = findMatchingAnswers(query, team, 1);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }
    
    /**
     * Increment usage count for a knowledge base entry
     */
    @Transactional
    public void incrementUsageCount(UUID kbId) {
        KnowledgeBase kb = KnowledgeBase.findById(kbId);
        if (kb != null) {
            kb.usageCount = (kb.usageCount != null ? kb.usageCount : 0) + 1;
            kb.persist();
        }
    }
    
    /**
     * Approve a knowledge base entry
     */
    @Transactional
    public void approveEntry(UUID kbId, String approvedBy) {
        KnowledgeBase kb = KnowledgeBase.findById(kbId);
        if (kb != null) {
            kb.approvalStatus = "APPROVED";
            kb.approvedBy = approvedBy;
            kb.approvedAt = Instant.now();
            kb.persist();
        }
    }
    
    /**
     * Get all knowledge base entries
     */
    public List<KnowledgeBase> getAllEntries(String team, Boolean isActive) {
        if (team != null && isActive != null) {
            return KnowledgeBase.find("team = ?1 AND isActive = ?2", team, isActive).list();
        } else if (team != null) {
            return KnowledgeBase.find("team = ?1", team).list();
        } else if (isActive != null) {
            return KnowledgeBase.find("isActive = ?1", isActive).list();
        } else {
            return KnowledgeBase.listAll();
        }
    }
    
    /**
     * Get knowledge base entry by ID
     */
    public Optional<KnowledgeBase> getById(UUID kbId) {
        return Optional.ofNullable(KnowledgeBase.findById(kbId));
    }
    
    /**
     * Generate and store embedding for a knowledge base entry
     */
    private void generateAndStoreEmbedding(KnowledgeBase kb) {
        try {
            // Generate embedding for the question
            List<Double> embedding = llmClient.generateEmbedding(kb.question);
            
            // Store embedding (simplified - in production, store in knowledge_base_embeddings table)
            // For now, we'll regenerate on each search
            Log.debug("Generated embedding for KB entry: " + kb.kbId);
        } catch (Exception e) {
            Log.warn("Failed to generate embedding for KB entry: " + kb.kbId, e);
        }
    }
    
    /**
     * Get embedding for a knowledge base entry
     * In production, this would query the knowledge_base_embeddings table
     */
    private List<Double> getEmbeddingForKb(KnowledgeBase kb) {
        try {
            return llmClient.generateEmbedding(kb.question);
        } catch (Exception e) {
            Log.warn("Failed to get embedding for KB entry: " + kb.kbId, e);
            return null;
        }
    }
    
    /**
     * Calculate cosine similarity between two embeddings
     */
    private double cosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1.size() != vec2.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    public record KnowledgeBaseMatch(KnowledgeBase entry, double similarity) {}
}
