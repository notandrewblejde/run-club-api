# Run Club API

Java Spring Boot backend for Run Club - a social running app for connecting runners, tracking activities, and building community through clubs and goals.

## Tech Stack

- **Java 21** with Spring Boot 3
- **PostgreSQL** with Flyway migrations
- **Auth0** for JWT authentication
- **Strava OAuth** for activity sync
- **AWS S3** for photo storage
- **Docker** for containerization

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 13+
- Environment variables configured (see .env.example)

### Local Development

```bash
# Set environment variables
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/runclub
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=<password>
export AUTH0_ISSUER_URI=https://runclub.us.auth0.com/
export STRAVA_CLIENT_ID=<your_client_id>
export STRAVA_CLIENT_SECRET=<your_secret>
export AWS_REGION=us-east-1
export S3_BUCKET=run-club-photos

# Build and run
mvn clean spring-boot:run
```

Server runs on `http://localhost:8080`

### Health Check

```bash
curl http://localhost:8080/health
```

Response:
```json
{
  "status": "ok",
  "version": "1.0.0"
}
```

## Database

Migrations run automatically on startup via Flyway. Located in `src/main/resources/db/migration/`.

## API Security

- Public endpoints: `GET /health`, `POST /v1/strava/webhook`
- All other endpoints require Auth0 JWT Bearer token
- Token validation happens automatically via Spring Security OAuth2 Resource Server

## Building Docker Image

```bash
docker build -t run-club-api:latest .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/runclub \
  -e AUTH0_ISSUER_URI=https://runclub.us.auth0.com/ \
  -e STRAVA_CLIENT_ID=<client_id> \
  -e STRAVA_CLIENT_SECRET=<secret> \
  run-club-api:latest
```

## Project Structure

```
src/
├── main/
│   ├── java/com/runclub/api/
│   │   ├── controller/      # REST endpoints
│   │   ├── service/         # Business logic
│   │   ├── entity/          # JPA entities
│   │   ├── repository/      # Data access
│   │   ├── dto/             # Data transfer objects
│   │   └── config/          # Spring configuration
│   └── resources/
│       ├── db/migration/    # Flyway SQL migrations
│       └── application.properties
└── test/
    └── java/com/runclub/api/
```

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| SPRING_DATASOURCE_URL | PostgreSQL connection | jdbc:postgresql://localhost:5432/runclub |
| SPRING_DATASOURCE_USERNAME | Database user | postgres |
| SPRING_DATASOURCE_PASSWORD | Database password | - |
| AUTH0_ISSUER_URI | Auth0 tenant URL | https://runclub.us.auth0.com/ |
| STRAVA_CLIENT_ID | Strava app client ID | 230392 |
| STRAVA_CLIENT_SECRET | Strava app secret | - |
| AWS_REGION | AWS region | us-east-1 |
| S3_BUCKET | S3 bucket for photos | run-club-photos |

## Deployment

Deployed to AWS ECS on the gridclaw cluster with ECR image registry.

- **ECR Registry**: 800936798816
- **Service**: run-club-api
- **Cluster**: gridclaw
