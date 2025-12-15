package com.temporal.ai.data.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "findings")
public class Finding extends PanacheEntityBase {
    
    @Id
    @Column(name = "finding_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID findingId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", nullable = false)
    public Scan scan;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cwe_id", nullable = false)
    public CWE cwe;
    
    @Column(name = "severity", nullable = false, length = 20)
    public String severity;
    
    @Column(name = "title", nullable = false, length = 500)
    public String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    public String description;
    
    @Column(name = "file_path", length = 1000)
    public String filePath;
    
    @Column(name = "line_number")
    public Integer lineNumber;
    
    @Column(name = "column_number")
    public Integer columnNumber;
    
    @Column(name = "tool", length = 100)
    public String tool;
    
    @Column(name = "tool_finding_id", length = 255)
    public String toolFindingId;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_output", columnDefinition = "jsonb")
    public Map<String, Object> rawOutput;
    
    @Column(name = "is_reachable")
    public Boolean isReachable = false;
    
    @Column(name = "is_false_positive")
    public Boolean isFalsePositive = false;
    
    @Column(name = "status", nullable = false, length = 20)
    public String status = "OPEN";
    
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
