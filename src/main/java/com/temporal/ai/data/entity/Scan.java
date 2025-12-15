package com.temporal.ai.data.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scans")
public class Scan extends PanacheEntityBase {
    
    @Id
    @Column(name = "scan_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID scanId;
    
    @Column(name = "workflow_id", nullable = false)
    public String workflowId;
    
    @Column(name = "run_id")
    public String runId;
    
    @Column(name = "team", nullable = false, length = 100)
    public String team;
    
    @Column(name = "project")
    public String project;
    
    @Column(name = "scan_type", nullable = false, length = 50)
    public String scanType;
    
    @Column(name = "status", nullable = false, length = 20)
    public String status;
    
    @Column(name = "started_at", nullable = false)
    public Instant startedAt;
    
    @Column(name = "completed_at")
    public Instant completedAt;
    
    @Column(name = "duration_ms")
    public Long durationMs;
    
    @Column(name = "total_findings")
    public Integer totalFindings = 0;
    
    @Column(name = "critical_findings")
    public Integer criticalFindings = 0;
    
    @Column(name = "high_findings")
    public Integer highFindings = 0;
    
    @Column(name = "medium_findings")
    public Integer mediumFindings = 0;
    
    @Column(name = "low_findings")
    public Integer lowFindings = 0;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    public Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
    
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
