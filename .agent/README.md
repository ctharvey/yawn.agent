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
- Read-only JPA/JDBC access to the shared yawn Postgres database
- Gradle composite build with sibling `yawn.db`; override its independent location with `-PyawnDbPath=<path>`
- Caffeine cache for alias lookups and resolver responses (500 entries, 5m TTL)
- Authoritative `card_aliases_typed` contract from yawn.db V138. CARD, SET, and RARITY aliases are one-to-many, matched longest-first with deterministic ties, and merged into normal candidate ranking. An alias phrase found in a candidate's card name (for example `ex`) stays lexical name evidence instead of becoming a destructive set filter.
- Public queries are limited to 200 characters, eight distinct terms, and at most 50 ordered candidates per term.
- Unknown seed and price freshness is returned as `lastSeedSync: null` and `lastPriceUpdate: null`; request time is never presented as data freshness.
- Public navigation uses `YAWN_RIP_BASE_URL` (default `https://yawn.rip`) and emits absolute, URL-encoded card/search suggestions.

## Ambiguity spec

| Value | Condition |
|-------|-----------|
| `none` | 1 match, confidence >= 0.90 |
| `low` | 1 match, 0.70 <= confidence < 0.90 |
| `medium` | 2–3 matches, or 1 match with 0.50 <= confidence < 0.70 |
| `high` | 4+ matches, top confidence < 0.50, or no match |

Queries containing `booster box/pack/bundle`, `etb`, `elite trainer`, `collection box`,
`blister`, or `bundle` are detected as sealed products before any card search. Because no
sealed resolver endpoint exists, these responses retain the `suggestedNext` field with a null value.

## Key classes

| Class | Role |
|-------|------|
| `CardResolverController` | REST entry point |
| `CardResolverService` | Token scoring, alias lookup, ambiguity bucketing, sealed detection |
| `AliasService` | Longest-first typed alias evidence extraction |
| `DiscoveryController` | `/agent`, `/agent/tools`, `/llms.txt` |
| `PokemonCardSummary` | Read-only JPA projection of `pokemon_cards` |
| `CardAlias` | Typed V138 alias row from `card_aliases_typed` |

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
- A future sealed product resolver; no route is advertised until one exists
- Populate `setName` in resolver matches (null placeholder; requires join to `pokemon_sets`)
- Wire authoritative catalog/price timestamps through `ResolverFreshnessProvider`
- Caddy routing + VPS deploy wiring

## Dependencies

- PostgreSQL (shared `yawn` database)
- `card_aliases_typed` authoritative schema from V138 in `yawn.db`

## Deployment

- Docker build uses the Yawn family root as context so both `yawn.agent` and `yawn.db` are available to the composite build. `Dockerfile.dockerignore` sends only those two source/build inputs, and the runtime image contains only the Agent artifact.
- Service entry in `yawn.deploy/docker-compose.yml`
- Caddy routes `agent.yawn.rip` to this service

## Caching

- `aliases` cache: alias lookups, 500 entries, 5 minute TTL
- `resolver` cache: full resolve responses, 500 entries, 5 minute TTL

## DNS

`agent.yawn.rip` resolves to the same VPS IP as `yawn.rip`. Caddy routes
`agent.yawn.rip` to this service on port 8081.
