#!/bin/bash
# Script to create database and user if they don't exist
# This can be run manually if docker-compose isn't used

set -e

DB_NAME="temporal_ai"
DB_USER="temporal"
DB_PASSWORD="temporal"
DB_HOST="localhost"
DB_PORT="5432"
#DB_NAME="${DB_NAME:temporal_ai}"
#DB_USER="${DB_USER:temporal}"
#DB_PASSWORD="${DB_PASSWORD:temporal}"
#DB_HOST="${DB_HOST:localhost}"
#DB_PORT="${DB_PORT:5432}"

echo "Creating PostgreSQL database and user..."
echo "Database: $DB_NAME"
echo "User: $DB_USER"
echo "Host: $DB_HOST:$DB_PORT"

# Check if psql is available
if ! command -v psql &> /dev/null; then
    echo "Error: psql command not found. Please install PostgreSQL client tools."
    exit 1
fi

# Try to connect as postgres user (superuser)
export PGPASSWORD="${POSTGRES_PASSWORD:temporal}"

psql -h "$DB_HOST" -p "$DB_PORT" -U $DB_USER <<-EOSQL
    -- Create user if it doesn't exist
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$DB_USER') THEN
            CREATE ROLE $DB_USER WITH LOGIN PASSWORD '$DB_PASSWORD';
            RAISE NOTICE 'User $DB_USER created';
        ELSE
            RAISE NOTICE 'User $DB_USER already exists';
        END IF;
    END
    \$\$;

    -- Create database if it doesn't exist
    SELECT 'CREATE DATABASE $DB_NAME'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB_NAME')\gexec

    -- Grant privileges
    GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;
EOSQL

# Connect to the new database and grant schema privileges
export PGPASSWORD="$DB_PASSWORD"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<-EOSQL
    GRANT ALL ON SCHEMA public TO $DB_USER;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO $DB_USER;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO $DB_USER;
EOSQL

echo "Database and user setup complete!"

