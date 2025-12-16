-- This script runs automatically when the PostgreSQL container is first created
-- Since POSTGRES_USER=temporal and POSTGRES_DB=temporal_ai are set in docker-compose.yml,
-- PostgreSQL automatically creates them. This script ensures proper permissions.

-- Grant privileges on the public schema
GRANT ALL ON SCHEMA public TO temporal;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO temporal;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO temporal;
