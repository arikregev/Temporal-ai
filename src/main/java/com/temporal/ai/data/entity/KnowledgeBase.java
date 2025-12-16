package com.temporal.ai.data.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "knowledge_base")
public class KnowledgeBase extends PanacheEntityBase {
    
    @Id
    @Column(name = "kb_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID kbId;
    
    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    public String question;
    
    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    public String answer;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_variations", columnDefinition = "jsonb")
    public List<String> questionVariations;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "context_tags", columnDefinition = "TEXT[]")
    public String[] contextTags;
    
    @Column(name = "team", length = 100)
    public String team;
    
    @Column(name = "project")
    public String project;
    
    @Column(name = "created_by", nullable = false)
    public String createdBy;
    
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
    
    @Column(name = "updated_by")
    public String updatedBy;
    
    @Column(name = "usage_count", nullable = false)
    public Integer usageCount = 0;
    
    @Column(name = "is_active", nullable = false)
    public Boolean isActive = true;
    
    @Column(name = "version", nullable = false)
    public Integer version = 1;
    
    @Column(name = "approval_status", nullable = false, length = 20)
    public String approvalStatus = "PENDING";
    
    @Column(name = "approved_by")
    public String approvedBy;
    
    @Column(name = "approved_at")
    public Instant approvedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
