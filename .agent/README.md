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
| GET | `/agent` | Machine-readable service discovery JSON |
| GET | `/agent/tools` | Structured tool list for agent callers |
| GET | `/llms.txt` | Plain-text guidance for LLM callers |

## Architecture

- Spring Boot 3.4 on port 8081
- Java 21
- Read-only JPA access to the shared yawn Postgres database
- Standalone Gradle project — no composite build dependency on sibling projects
- Caffeine cache for alias lookups and resolver responses (500 entries, 5m TTL)
- `card_aliases` table (V47 migration in `yawn.db`) — add nicknames via SQL INSERT, no deploy needed

## Ambiguity spec

| Value | Condition |
|-------|-----------|
| `none` | 1 match, confidence >= 0.90 |
| `low` | 1 match, 0.70 <= confidence < 0.90 |
| `medium` | 2–3 matches, or 1 match with 0.50 <= confidence < 0.70 |
| `high` | 4+ matches, top confidence < 0.50, or no match |

Queries containing `booster box/pack/bundle`, `etb`, `elite trainer`, `collection box`,
`blister`, or `bundle` are detected as sealed products and redirect to `/api/agent/sealed/resolve`
before any card search.

## Key classes

| Class | Role |
|-------|------|
| `CardResolverController` | REST entry point |
| `CardResolverService` | Token scoring, alias lookup, ambiguity bucketing, sealed detection |
| `AliasService` | DB-backed alias resolution with Caffeine cache |
| `DiscoveryController` | `/agent`, `/agent/tools`, `/llms.txt` |
| `PokemonCardSummary` | Read-only JPA projection of `pokemon_cards` |
| `CardAlias` | JPA entity for `card_aliases` |

## Commits

| Hash | Description |
|------|-------------|
| `ad7681c` | Initial scaffold — project, entities, services, controller, V47 migration |
| `4278a54` | Phase 3-4 — discovery endpoints, unit tests, ambiguity bug fixes |
| `a1889d7` | Sealed detection + spec-aligned ambiguity bucketing |

## Follow-on work

- `GET /agent/card/{cardId}/profile` — full card metadata
- `GET /agent/card/{cardId}/variant-guide` — printing variants
- `GET /agent/card/{cardId}/market-snapshot` — pricing (paid/gated)
- `GET /api/agent/sealed/resolve` — sealed product resolver
- Populate `setName` in resolver matches (null placeholder; requires join to `pokemon_sets`)
- Real freshness metadata from catalog metadata table (currently `Instant.now()` placeholder)
- Caddy routing + VPS deploy wiring

## Dependencies

- PostgreSQL (shared `yawn` database)
- `card_aliases` table seeded via `V47__create_card_aliases.sql` in `yawn.db`

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
