# Temporal Security Analyst Interface

A natural language security analyst interface built with Java 21, Quarkus, Temporal SDK, PostgreSQL, and self-hosted LLM. This system provides intelligent querying over security scan data and Temporal workflow history, finding explanations, policy-as-text compilation, and a knowledge base for organizational Q&A pairs.

## Features

- **Natural Language Queries**: Ask questions about scans, findings, and security metrics in plain English
- **Finding Explanations**: Transform raw scan tool output into developer-friendly explanations
- **Policy Compiler**: Convert natural language policies into executable rules
- **Knowledge Base**: Teach the system common organizational Q&A pairs for improved accuracy
- **REST API**: Full-featured API for all operations
- **CLI Tool**: Command-line interface for queries and management
- **Web UI**: Modern React-based interface for all features

## Architecture

The system consists of six main components:

1. **Natural Language Query Service** - Processes user queries and routes to appropriate data sources
2. **Query Layer** - Unified interface over PostgreSQL scan DB and Temporal workflow history
3. **Finding Explanation Service** - Transforms scan tool output into developer-friendly explanations
4. **Policy Compiler** - Converts natural language policies into executable rules
5. **Knowledge Base Service** - Manages organizational Q&A pairs to teach the LLM common answers
6. **Web UI** - Interactive web interface for querying, managing knowledge base, and viewing results

## Prerequisites

- Java 21+
- Maven 3.8+
- Docker and Docker Compose
- Node.js 18+ and npm (for UI)
- Self-hosted LLM (Ollama/Llama) running on `http://localhost:11434`

## Quick Start

### 1. Start Infrastructure Services

```bash
docker-compose up -d
```

This starts:
- PostgreSQL database on port 5432
- Temporal server on port 7233
- Temporal UI on port 8080

### 2. Start LLM Service

Make sure Ollama or Llama is running and accessible at `http://localhost:11434`. You can start Ollama with:

```bash
ollama serve
```

### 3. Build and Run Backend

```bash
mvn clean install
mvn quarkus:dev
```

The API will be available at `http://localhost:8080`

### 4. Start Web UI

```bash
cd ui
npm install
npm run dev
```

The UI will be available at `http://localhost:5173`

## Configuration

Edit `src/main/resources/application.properties` to configure:

- Database connection (defaults to localhost:5432)
- Temporal server address (defaults to localhost:7233)
- LLM base URL (defaults to http://localhost:11434)
- LLM model name (defaults to llama2)

## API Documentation

### Query Endpoint

**POST** `/api/query`

Process a natural language query.

Request:
```json
{
  "query": "Why did scan X take 47 minutes?",
  "team": "Payments"
}
```

Response:
```json
{
  "source": "QUERY_LAYER",
  "answer": "The scan took 47 minutes because...",
  "data": {...},
  "confidence": 0.95
}
```

### Explanation Endpoint

**GET** `/api/explanation/finding/{findingId}`

Get a developer-friendly explanation of a finding.

Response:
```json
{
  "explanation": "This is a SQL injection vulnerability...",
  "stepsToReproduce": "1. Navigate to...",
  "codePointers": "src/main/java/.../UserService.java:42",
  "impact": "An attacker could...",
  "recommendedFix": "Use parameterized queries..."
}
```

### Policy Compilation

**POST** `/api/policy/compile`

Compile a natural language policy into executable rules.

Request:
```json
{
  "policy": "block builds if reachable critical vuln in prod dependency"
}
```

Response:
```json
{
  "originalPolicy": "block builds if reachable critical vuln in prod dependency",
  "action": "BLOCK",
  "condition": "severity == 'CRITICAL' AND isReachable == true AND environment == 'production'",
  "scope": "builds",
  "ruleCode": "function evaluatePolicy(finding, scan) { ... }"
}
```

### Knowledge Base Management

**POST** `/api/knowledge`

Create a new Q&A pair.

Request:
```json
{
  "question": "What is our policy on critical vulnerabilities?",
  "answer": "All critical vulnerabilities must be fixed within 24 hours...",
  "createdBy": "admin",
  "team": "Security",
  "contextTags": ["policy", "vulnerability"]
}
```

**POST** `/api/knowledge/search`

Search for matching Q&A pairs.

Request:
```json
{
  "query": "What should we do about critical vulnerabilities?",
  "team": "Security",
  "limit": 5
}
```

## CLI Usage

### Query

```bash
mvn quarkus:dev -Dquarkus.args="query 'Why did scan X take 47 minutes?' --team Payments"
```

### Knowledge Base

```bash
# Create entry
mvn quarkus:dev -Dquarkus.args="kb --create --question 'What is CWE-79?' --answer 'Cross-site Scripting' --user admin"

# Search
mvn quarkus:dev -Dquarkus.args="kb --search 'What is XSS?'"

# List all
mvn quarkus:dev -Dquarkus.args="kb --list"
```

### Policy Compilation

```bash
mvn quarkus:dev -Dquarkus.args="policy --compile 'block builds if reachable critical vuln in prod dependency'"
```

## Agent Learning Portal

The system includes an Agent Learning Portal accessible through the Web UI that allows users to provide feedback on LLM responses to improve the knowledge base.

### Features

1. **Feedback Collection**: After each query response, users can provide feedback:
   - Mark response as helpful/not helpful
   - Suggest improvements
   - Add to knowledge base

2. **Knowledge Base Enhancement**: 
   - Convert good LLM responses into knowledge base entries
   - Refine existing entries based on feedback
   - Track usage statistics to identify frequently asked questions

3. **Continuous Improvement**:
   - System learns from user interactions
   - Semantic search improves over time
   - Common questions get faster, more accurate answers

### Using the Learning Portal

1. Navigate to the Query page in the Web UI
2. Submit a query and review the response
3. Click "Feedback" button to:
   - Rate the response
   - Add to knowledge base (if response is good)
   - Suggest improvements
4. View knowledge base entries in the Knowledge Base page
5. Edit or approve entries as needed

## Database Schema

The system uses PostgreSQL with the following main tables:

- `scans` - Security scan executions
- `findings` - Individual security findings
- `cwes` - CWE definitions
- `knowledge_base` - Q&A pairs for common questions
- `knowledge_base_embeddings` - Vector embeddings for semantic search

Schema migrations are managed by Liquibase and run automatically on startup.

## Development

### Running Tests

```bash
mvn test
```

### Building for Production

```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

### Native Compilation

```bash
mvn clean package -Pnative
```

## Deployment

### Local Development

Use Docker Compose as described in Quick Start.

### Production (Openshift ECS)

1. Build Docker image:
```bash
docker build -t temporal-security-analyst .
```

2. Deploy to Openshift with appropriate configuration for:
   - Temporal server endpoint
   - PostgreSQL connection
   - LLM service endpoint
   - CORS origins for Web UI

## Troubleshooting

### LLM Connection Issues

- Verify Ollama/Llama is running: `curl http://localhost:11434/api/tags`
- Check LLM model is available: `ollama list`
- Verify model name in `application.properties` matches installed model

### Database Connection Issues

- Verify PostgreSQL is running: `docker ps`
- Check connection string in `application.properties`
- Ensure database exists: `docker exec -it temporal-ai-postgres psql -U temporal_user -d temporal_ai`

### Temporal Connection Issues

- Verify Temporal is running: `curl http://localhost:7233`
- Check Temporal UI: `http://localhost:8080`
- Verify namespace matches configuration

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

[Add your license here]

## Support

For issues and questions, please open an issue on the repository.

