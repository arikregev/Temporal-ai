#!/bin/bash
set -e

# Wait for PostgreSQL to be ready
until psql -U temporal -d temporal_ai -c '\q' 2>/dev/null; do
  >&2 echo "PostgreSQL is unavailable - sleeping"
  sleep 1
done

# Grant additional permissions if needed
psql -U temporal -d temporal_ai <<-EOSQL
    -- Ensure temporal user has all necessary permissions
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO temporal;
    GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO temporal;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO temporal;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO temporal;
EOSQL

echo "Database permissions configured successfully"

