package com.temporal.ai.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Database initialization service that verifies tables exist and creates them if needed.
 * This runs after Liquibase migrations to ensure everything is in place.
 */
@ApplicationScoped
public class DatabaseInitializer {
    
    @Inject
    EntityManager entityManager;
    
    private static final List<String> REQUIRED_TABLES = List.of(
        "scans",
        "cwes",
        "findings",
        "knowledge_base",
        "knowledge_base_embeddings"
    );
    
    void onStart(@Observes StartupEvent ev) {
        Log.info("Verifying database schema...");
        try {
            verifyTables();
            Log.info("✓ Database schema verification complete - all tables exist");
        } catch (Exception e) {
            Log.error("✗ Database schema verification failed", e);
            // Don't fail startup - Liquibase should have handled table creation
            // This is just a verification step
        }
    }
    
    @Transactional
    public void verifyTables() {
        try {
            // Use native SQL query to check for tables (works with Quarkus/Hibernate)
            List<String> existingTables = getExistingTables();
            List<String> missingTables = new ArrayList<>();
            
            for (String table : REQUIRED_TABLES) {
                if (!existingTables.contains(table.toLowerCase())) {
                    missingTables.add(table);
                }
            }
            
            if (!missingTables.isEmpty()) {
                Log.error("✗ Missing tables detected: " + missingTables);
                Log.error("Liquibase migrations should have created these tables automatically.");
                
                // Check if Liquibase changelog table exists
                if (!existingTables.contains("databasechangelog")) {
                    Log.error("Liquibase changelog table not found. This indicates:");
                    Log.error("  1. Liquibase migrations did not run");
                    Log.error("  2. Database connection may have failed");
                    Log.error("  3. Check application logs above for connection errors");
                    Log.error("");
                    Log.error("Common issues:");
                    Log.error("  - Database user 'temporal' does not exist");
                    Log.error("  - Database 'temporal_ai' does not exist");
                    Log.error("  - Incorrect database credentials in application.properties");
                    Log.error("  - PostgreSQL server is not running");
                    throw new IllegalStateException(
                        "Database tables are missing and Liquibase did not run. " +
                        "Please check database connection and credentials."
                    );
                } else {
                    Log.warn("Liquibase changelog table exists but tables are missing.");
                    Log.warn("This may indicate a migration failure. Check Liquibase logs above.");
                }
            } else {
                Log.info("✓ All required tables verified: " + REQUIRED_TABLES);
            }
            
        } catch (Exception e) {
            Log.error("Error verifying database tables", e);
            throw new RuntimeException("Database initialization failed: " + e.getMessage(), e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<String> getExistingTables() {
        // Use native SQL query to get table names from PostgreSQL
        String sql = "SELECT tablename FROM pg_tables WHERE schemaname = 'public'";
        List<Object> results = entityManager.createNativeQuery(sql).getResultList();
        List<String> tables = new ArrayList<>();
        for (Object row : results) {
            if (row != null) {
                tables.add(row.toString().toLowerCase());
            }
        }
        return tables;
    }
    
    /**
     * Check if a specific table exists
     */
    public boolean tableExists(String tableName) {
        try {
            List<String> existingTables = getExistingTables();
            return existingTables.contains(tableName.toLowerCase());
        } catch (Exception e) {
            Log.error("Error checking if table exists: " + tableName, e);
            return false;
        }
    }
}

