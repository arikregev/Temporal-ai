#!/bin/bash
# Quick setup script to ensure database is ready
# This will recreate the PostgreSQL container with correct settings

set -e

echo "Setting up PostgreSQL database for Temporal Security Analyst..."

# Stop and remove existing container if it exists
#docker-compose down postgres 2>/dev/null || true

# Remove the volume to start fresh (optional - comment out if you want to keep data)
# docker volume rm temporal-ai_postgres_data 2>/dev/null || true

# Start PostgreSQL with correct configuration
#echo "Starting PostgreSQL container..."
#docker-compose up -d postgres

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
timeout=30
counter=0
until docker exec temporal-ai-postgres pg_isready -U temporal -d temporal_ai 2>/dev/null; do
    sleep 1
    counter=$((counter + 1))
    if [ $counter -ge $timeout ]; then
        echo "Error: PostgreSQL did not become ready in time"
        exit 1
    fi
done

echo "PostgreSQL is ready!"
echo ""
echo "Database: temporal_ai"
echo "User: temporal"
echo "Password: temporal"
echo ""
echo "You can now run: mvn quarkus:dev"

