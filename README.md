# Bonsaai API

REST API for a habit and goal tracking application with social challenges and subscription-based monetization. Built with Java and Spring Boot, designed for production deployment.

## Overview

Bonsaai is a backend system that allows users to build habits, set goals, and compete with others through challenges. It features a freemium model with Google OAuth2 authentication, PIX payment integration, distributed rate limiting, and comprehensive analytics.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Database | PostgreSQL |
| Migrations | Flyway |
| Authentication | OAuth2 (Google) |
| Cache / Rate Limiting | Redis + Bucket4j |
| Payments | Woovi (OpenPix / PIX) |
| Documentation | Springdoc OpenAPI (Swagger) |
| Build | Maven |
| Deployment | Docker / Render |

## Features

### Habit Management
- Create habits with daily or weekly frequency and custom target days
- Log completions (check-in) per habit
- Archive habits without losing historical data (soft delete)

### Goal Tracking
- Create goals with deadlines and link multiple habits to them
- Track progress through goal checkpoints
- Mark goals as complete
- FREE tier: 1 active goal; PRO tier: unlimited goals

### Social Challenges
- Create and join challenges using invite codes
- Check in habits within the challenge context
- Leaderboard with rankings per challenge
- Challenge creation is restricted to PRO users

### Subscription and Payments
- Three subscription plans: Monthly (R$ 9.90), Quarterly (R$ 25.90), Yearly (R$ 99.90)
- PIX payment processing via Woovi/OpenPix gateway
- Idempotent webhook processing to prevent duplicate confirmations
- Automatic plan expiration tracking

### Rate Limiting
- Token bucket algorithm via Bucket4j with Redis backend
- Tiered limits: unauthenticated (50 req/h), FREE (100 req/h), PRO (1000 req/h)

### Analytics
- Habit performance insights and completion statistics
- Restricted to PRO users

## Project Structure

```
src/main/java/dev/mashni/habitsapi/
├── auth/             # Security config, OAuth2 user service, login handler
├── habit/            # Habit CRUD, check-in, archiving, logs
├── goal/             # Goals, linked habits, checkpoints
├── challenge/        # Challenge creation, join, check-in, leaderboard
├── payment/          # Checkout, Woovi gateway client, webhook processor
├── user/             # User profile, plan management
├── analytics/        # PRO-only habit analytics
├── ratelimit/        # Bucket4j + Redis rate limiting interceptor
└── shared/           # CORS config, global exception handler
```

Database migrations are versioned under `src/main/resources/db/migration/` using Flyway.

## API Endpoints

| Module | Method | Path | Description |
|---|---|---|---|
| Habits | POST | /api/habits | Create habit |
| Habits | GET | /api/habits | List active habits |
| Habits | GET | /api/habits/archived | List archived habits |
| Habits | GET | /api/habits/{id} | Get habit details |
| Habits | PUT | /api/habits/{id} | Update habit |
| Habits | DELETE | /api/habits/{id} | Delete habit |
| Habits | PATCH | /api/habits/{id}/archive | Archive habit |
| Habits | POST | /api/habits/{id}/check | Log completion |
| Goals | POST | /api/goals | Create goal |
| Goals | GET | /api/goals | List goals |
| Goals | GET | /api/goals/{id} | Get goal details |
| Goals | PUT | /api/goals/{id}/habits | Update linked habits |
| Goals | PATCH | /api/goals/{id}/complete | Mark goal complete |
| Goals | DELETE | /api/goals/{id} | Delete goal |
| Challenges | POST | /api/challenges | Create challenge (PRO) |
| Challenges | POST | /api/challenges/join | Join via invite code |
| Challenges | GET | /api/challenges | List user challenges |
| Challenges | GET | /api/challenges/{id} | Challenge details + leaderboard |
| Challenges | POST | /api/challenges/{id}/check-in | Toggle habit check-in |
| Payments | POST | /api/payments/checkout | Create PIX payment |
| Payments | GET | /api/payments/{id}/status | Check payment status |
| User | GET | /api/me | Current user info |
| Analytics | GET | /api/analytics | Habit analytics (PRO) |
| Webhooks | POST | /api/webhooks/** | Woovi payment webhook |

Full interactive documentation is available at `/swagger-ui.html` when the application is running.

## Authentication

Authentication is handled via Google OAuth2. All `/api/**` endpoints require an authenticated session, with the exception of the payment webhook endpoint. Session data is managed with cookies (`SameSite=None`, `Secure=true`) and CSRF protection is enforced using the `X-XSRF-TOKEN` header.

## Running Locally

### Prerequisites
- Java 21
- Docker (for PostgreSQL and Redis)

### Setup

1. Clone the repository and copy the environment file:
   ```bash
   cp .env.example .env
   ```

2. Fill in the required variables in `.env`:
   ```
   DB_URL=jdbc:postgresql://localhost:5432/bonsaai
   DB_USERNAME=postgres
   DB_PASSWORD=your_password
   GOOGLE_CLIENT_ID=your_google_client_id
   GOOGLE_CLIENT_SECRET=your_google_client_secret
   FRONTEND_URL=http://localhost:3000
   REDIS_URL=redis://localhost:6379
   WOOVI_APP_ID=your_woovi_app_id
   WOOVI_API_URL=https://api.openpix.com.br
   WOOVI_WEBHOOK_SECRET=your_webhook_secret
   ```

3. Start the infrastructure services:
   ```bash
   ./start-dev.sh
   ```

4. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

The API will be available at `http://localhost:8080`.

## Running with Docker

A multi-stage `Dockerfile` is included for production builds:

```bash
docker build -t bonsaai-api .
docker run -p 8080:8080 --env-file .env bonsaai-api
```

## Running Tests

```bash
./mvnw test
```

Tests use an in-memory H2 database and do not require external services.

## Architecture Notes

- **Soft deletes**: Habits are archived using Hibernate annotations rather than hard-deleted, preserving historical log data.
- **Idempotent webhooks**: Payment webhook events are deduplicated using a `processed_webhook_events` table, preventing double-activation on retries.
- **Plan enforcement**: Business rules for FREE vs PRO tiers are enforced at the service layer, not just at the controller level.
- **Rate limiting**: Applied via a Spring `HandlerInterceptor` before requests reach controllers, with per-user buckets stored in Redis.
