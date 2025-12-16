# Database Setup - Java-Based

This application uses **Liquibase** (Java-based) to automatically create and manage database tables. No shell scripts are required.

## How It Works

1. **Liquibase Migrations**: On application startup, Liquibase automatically runs migrations defined in `src/main/resources/db/changelog/`
2. **Table Creation**: All required tables are created automatically if they don't exist
3. **Verification**: The `DatabaseInitializer` service verifies all tables exist after startup

## Required Tables

The application automatically creates these tables:
- `scans` - Security scan records
- `cwes` - Common Weakness Enumeration entries
- `findings` - Security findings from scans
- `knowledge_base` - Q&A knowledge base entries
- `knowledge_base_embeddings` - Vector embeddings for semantic search

## Database Requirements

**Before running the application**, ensure:

1. **PostgreSQL is running** (port 5432)
2. **Database exists**: `temporal_ai`
3. **User exists**: `temporal` with password `temporal`
4. **User has permissions**: CREATE, SELECT, INSERT, UPDATE, DELETE on database `temporal_ai`

## Quick Setup

### Using Docker Compose (Recommended)

```bash
docker-compose up -d postgres
```

This automatically creates:
- Database: `temporal_ai`
- User: `temporal`
- Password: `temporal`

### Manual Setup

If using an existing PostgreSQL instance:

```sql
-- Connect as postgres superuser
CREATE DATABASE temporal_ai;
CREATE USER temporal WITH PASSWORD 'temporal';
GRANT ALL PRIVILEGES ON DATABASE temporal_ai TO temporal;

-- Connect to temporal_ai database
\c temporal_ai
GRANT ALL ON SCHEMA public TO temporal;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO temporal;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO temporal;
```

## Application Startup

When you run `mvn quarkus:dev`, the application will:

1. **Connect to database** using credentials from `application.properties`
2. **Run Liquibase migrations** automatically (creates all tables)
3. **Verify tables exist** via `DatabaseInitializer`
4. **Start the application**

## Troubleshooting

### Error: "role temporal does not exist"

**Solution**: Create the database user first (see Manual Setup above)

### Error: "database temporal_ai does not exist"

**Solution**: Create the database:
```sql
CREATE DATABASE temporal_ai;
```

### Error: "Liquibase changelog table not found"

**Possible causes**:
- Database connection failed
- User doesn't have CREATE TABLE permissions
- Database doesn't exist

**Solution**: Check database connection and permissions

### Tables Not Created

If tables are missing after startup:

1. Check application logs for Liquibase errors
2. Verify database connection in `application.properties`
3. Ensure user has CREATE TABLE permissions
4. Check that `quarkus.liquibase.migrate-at-start=true` in `application.properties`

## Configuration

Database settings in `src/main/resources/application.properties`:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USERNAME:temporal}
quarkus.datasource.password=${DB_PASSWORD:temporal}
quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://localhost:5432/temporal_ai}

# Liquibase automatically creates tables
quarkus.liquibase.migrate-at-start=true
quarkus.liquibase.change-log=db/changelog/db.changelog-master.xml
```

## Migration Files

All migration files are in `src/main/resources/db/changelog/changes/`:
- `001-create-scans-table.xml`
- `002-create-cwes-table.xml`
- `003-create-findings-table.xml`
- `004-create-knowledge-base-table.xml`
- `005-create-knowledge-base-embeddings-table.xml`

These migrations are **idempotent** - they check if tables exist before creating them, so they're safe to run multiple times.

