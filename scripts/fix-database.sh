#!/bin/bash
# Script to fix database user if it doesn't exist
# Run this if you're getting "role temporal does not exist" errors

set -e

DB_NAME="${DB_NAME:temporal_ai}"
DB_USER="${DB_USER:temporal}"
DB_PASSWORD="${DB_PASSWORD:temporal}"
DB_HOST="${DB_HOST:localhost}"
DB_PORT="${DB_PORT:5432}"

echo "Fixing PostgreSQL database configuration..."
echo "This script will create the user '$DB_USER' if it doesn't exist"

# Try to connect as postgres superuser
# First, try with default postgres password, then prompt if needed
export PGPASSWORD="${POSTGRES_PASSWORD:-postgres}"

# Check if we can connect
if ! psql -h "$DB_HOST" -p "$DB_PORT" -U postgres -c '\q' 2>/dev/null; then
    echo "Cannot connect as postgres user. Please provide the postgres password:"
    read -s POSTGRES_PASSWORD
    export PGPASSWORD="$POSTGRES_PASSWORD"
fi

psql -h "$DB_HOST" -p "$DB_PORT" -U postgres <<-EOSQL
    -- Create user if it doesn't exist
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$DB_USER') THEN
            CREATE ROLE $DB_USER WITH LOGIN PASSWORD '$DB_PASSWORD';
            RAISE NOTICE 'User $DB_USER created successfully';
        ELSE
            RAISE NOTICE 'User $DB_USER already exists';
            -- Update password to ensure it matches
            ALTER ROLE $DB_USER WITH PASSWORD '$DB_PASSWORD';
        END IF;
    END
    \$\$;

    -- Create database if it doesn't exist
    SELECT 'CREATE DATABASE $DB_NAME'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB_NAME')\gexec

    -- Grant privileges on database
    GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;
EOSQL

# Now connect to the database and grant schema privileges
export PGPASSWORD="$DB_PASSWORD"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<-EOSQL
    GRANT ALL ON SCHEMA public TO $DB_USER;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO $DB_USER;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO $DB_USER;
    
    -- Grant privileges on existing objects
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO $DB_USER;
    GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO $DB_USER;
EOSQL

echo ""
echo "✓ Database configuration complete!"
echo "  User: $DB_USER"
echo "  Database: $DB_NAME"
echo "  You can now run: mvn quarkus:dev"

