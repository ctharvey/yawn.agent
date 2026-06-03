# yawn.agent

Spring Boot service hosting agent-facing endpoints for the Yawn ecosystem.
Serves `agent.yawn.rip` in production.

## Scope

This project provides lightweight, free, read-only HTTP endpoints designed
for AI agents to resolve Pokemon card identities from messy natural-language
queries. Future endpoints will provide card profiles, variant guides, and
market snapshots.

## Current endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/agent/card/resolve?q=<query>` | Resolve a card query to canonical card ID(s) |

## Architecture

- Spring Boot 3.4 on port 8081
- Java 21
- Read-only JPA access to the shared yawn Postgres database
- Uses `yawn.db` as a composite Gradle include for JPA entities
- Caffeine cache for alias lookups and resolver responses
- No Flyway migrations — DB schema changes belong in `yawn.db`

## Dependencies

- PostgreSQL (shared `yawn` database)
- `yawn.db` (shared JPA entities — also used by `yawn.rip` and `yawn.market`)

## Deployment

- Docker image in `Dockerfile` (lighter than yawn.rip — 512m memory limit)
- Service entry in `yawn.deploy/docker-compose.yml`
- Caddy routes `agent.yawn.rip` to this service

## Caching

- `aliases` cache: alias lookups, 500 entries, 5 minute TTL
- `resolver` cache: full resolve responses, 500 entries, 5 minute TTL

## DNS

`agent.yawn.rip` resolves to the same VPS IP as `yawn.rip`. Caddy routes
`agent.yawn.rip` to this service on port 8081.
