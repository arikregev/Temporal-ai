package com.temporal.ai.data.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "cwes")
public class CWE extends PanacheEntityBase {
    
    @Id
    @Column(name = "cwe_id", length = 20)
    public String cweId;
    
    @Column(name = "name", nullable = false)
    public String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    public String description;
    
    @Column(name = "category", length = 100)
    public String category;
    
    @Column(name = "severity", length = 20)
    public String severity;
    
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
