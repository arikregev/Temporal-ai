# Setup Instructions

## Prerequisites

1. **Java 21** - Make sure Java 21 is installed and set as JAVA_HOME
2. **Maven 3.8+** - For building the project
3. **Docker** - For running PostgreSQL and Temporal (or use external services)
4. **Node.js 18+** - For the Web UI (optional)

## Quick Start

### 1. Start Infrastructure Services

**Option A: Using Docker Compose (Recommended)**

```bash
docker-compose up -d
```

This will start:
- PostgreSQL on port 5432
- Temporal server on port 7233  
- Temporal UI on port 8080

**Option B: Use External Services**

If you have PostgreSQL and Temporal running elsewhere, update `application.properties` with the correct connection details.

### 2. Start LLM Service

Make sure Ollama or Llama is running:

```bash
ollama serve
```

The default configuration expects it at `http://localhost:11434`.

### 3. Run the Application

```bash
mvn quarkus:dev
```

The application will be available at `http://localhost:8080`

**Note**: If you see database connection errors, make sure PostgreSQL is running first (see step 1).

### 4. Start Web UI (Optional)

```bash
cd ui
npm install
npm run dev
```

The UI will be available at `http://localhost:5173`

## Troubleshooting

### Database Connection Issues

If you see "role temporal_user does not exist":
1. Make sure Docker is running: `docker ps`
2. Start PostgreSQL: `docker-compose up -d postgres`
3. Wait a few seconds for PostgreSQL to initialize
4. Try running `mvn quarkus:dev` again

### Port Already in Use

If port 8080 is already in use:
- Kill the process: `lsof -ti:8080 | xargs kill -9`
- Or change the port in `application.properties`: `quarkus.http.port=8081`

### Java Version Issues

If you see ByteBuddy errors about Java 25:
- The `.mvn/jvm.config` file includes `-Dnet.bytebuddy.experimental=true` to handle this
- Or use Java 21 instead of Java 25

### Temporal Connection Issues

- Verify Temporal is running: `curl http://localhost:7233`
- Check Temporal UI: `http://localhost:8080` (Temporal UI, not the app)
- Update `temporal.server.address` in `application.properties` if needed

## Configuration

Edit `src/main/resources/application.properties` to configure:
- Database connection details
- Temporal server address
- LLM service URL and model
- CORS origins for Web UI

