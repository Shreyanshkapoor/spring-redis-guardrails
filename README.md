# Grid07 — Backend Engineering Assignment
### Core API & Guardrails — Spring Boot 3.x + PostgreSQL + Redis

---

## Table of Contents
1. [Tech Stack](#tech-stack)
2. [Project Structure](#project-structure)
3. [Quick Start](#quick-start)
4. [API Reference](#api-reference)
5. [Phase 2 — Thread Safety Deep Dive](#phase-2--thread-safety-deep-dive)
6. [Phase 3 — Notification Engine](#phase-3--notification-engine)
7. [Phase 4 — Race Condition / Stress Test](#phase-4--race-condition--stress-test)
8. [Design Decisions](#design-decisions)

---

## Tech Stack

| Layer        | Technology                              |
|--------------|-----------------------------------------|
| Language     | Java 17                                 |
| Framework    | Spring Boot 3.2.5                       |
| Database     | PostgreSQL 16 (JPA / Hibernate)         |
| Cache/State  | Redis 7 (Spring Data Redis)             |
| Build        | Maven                                   |
| Containers   | Docker + Docker Compose                 |

---

## Project Structure

```
src/main/java/com/grid07/
├── Grid07Application.java
├── config/RedisConfig.java
├── entity/
│   ├── User.java            # id, username, is_premium
│   ├── Bot.java             # id, name, persona_description
│   ├── Post.java            # id, author_id, author_type, content, created_at
│   ├── Comment.java         # id, post_id, author_id, depth_level, created_at
│   ├── PostLike.java        # id, post_id, user_id (unique constraint)
│   └── AuthorType.java      # Enum: USER | BOT
├── repository/              # JpaRepository per entity
├── dto/                     # Request DTOs with validation
├── exception/               # GuardrailException (429), ResourceNotFoundException (404)
└── service/
    ├── PostService.java         # Orchestrates DB + Redis
    ├── GuardrailService.java    # Phase 2 Atomic Locks
    ├── ViralityService.java     # Phase 2 Virality Score
    ├── NotificationService.java # Phase 3 Throttler
    └── NotificationScheduler.java # Phase 3 CRON Sweeper
```

---

## Quick Start

### 1. Start infrastructure

```bash
docker-compose up -d
```

Starts `grid07-postgres` on **5432** and `grid07-redis` on **6379**.

### 2. Run the application

```bash
./mvnw spring-boot:run
```

App starts on **http://localhost:8081**.
Hibernate auto-creates all tables on first boot (`ddl-auto=update`).

### 3. Seed test data

```bash
# Create a human user
curl -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"shreyansh","isPremium":true}'

# Create a bot
curl -X POST http://localhost:8081/api/bots \
  -H "Content-Type: application/json" \
  -d '{"name":"AlphaBot","personaDescription":"Helpful bot"}'
```

---

## API Reference

### Core Endpoints (Phase 1)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/posts` | `authorId`, `authorType`, `content` | Create a post |
| POST | `/api/posts/{id}/comments` | `authorId`, `authorType`, `content`, `depthLevel`, `targetUserId`* | Add a comment |
| POST | `/api/posts/{id}/like` | `userId` | Like a post |
| GET | `/api/posts/{id}/virality` | — | Read virality score (Redis) |
| GET | `/api/posts/{id}/bot-count` | — | Read bot reply count (Redis) |

> `*` `targetUserId` is required when `authorType = BOT`.

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 201 | Created |
| 200 | OK |
| 400 | Validation error / duplicate like |
| 404 | Resource not found |
| 429 | Guardrail triggered |

---

## Phase 2 — Thread Safety Deep Dive

This is the core of the assignment. Three Redis guardrails run **before** any database write.

---

### 1. Horizontal Cap (max 100 bot replies per post)

**Redis key:** `post:{id}:bot_count`

**Strategy — lock-free atomic counter:**

```
INCR post:{id}:bot_count   →  newCount  (atomic, server-side)
if newCount > 100:
    DECR post:{id}:bot_count   ← rollback immediately
    throw 429 Too Many Requests
else:
    proceed to PostgreSQL write
```

**Why this is race-condition-safe:**

Redis processes commands single-threaded. `INCR` is an atomic O(1) operation — no two clients can read the same pre-increment value. With 200 simultaneous requests, Redis serializes every `INCR` internally. Only one request ever receives `newCount = 101`; it is rejected immediately and the counter is decremented back. The logical cap stays at exactly 100 — it is mathematically impossible to reach 101 committed rows.

**Data integrity:** If the PostgreSQL write fails after the Redis increment succeeds, the service catches the exception and calls `rollbackBotCount(postId)` — so Redis and the database never drift apart.

---

### 2. Vertical Cap (max depth 20)

Pure application-level check on the `depthLevel` field before any I/O:

```java
if (depthLevel > 20) throw new GuardrailException("Vertical cap reached");
```

---

### 3. Cooldown Cap (bot ↔ human, 10-minute TTL)

**Redis key:** `cooldown:bot_{botId}:human_{userId}`

**Strategy — `SET NX EX` (single atomic command):**

```
SET cooldown:bot_1:human_5  "1"  NX  EX 600
```

`SET NX` is atomic. If two threads race on the same key, exactly one gets `true`; the other sees `false` and is rejected with 429. The `EX 600` TTL auto-expires the key after 10 minutes — no manual cleanup job needed.

---

### Statelessness Guarantee

**Zero in-memory state.** No `HashMap`, no `static` variables, no counters in JVM heap. Every counter, cooldown, and notification queue lives exclusively in Redis. The app can be horizontally scaled to N instances with no coordination.

---

## Phase 3 — Notification Engine

### The Throttler (`NotificationService`)

When a bot interacts with a human's post:

1. Check `notif_cooldown:user_{id}` in Redis (TTL 900 s = 15 min).
2. **Key exists** → `RPUSH user:{id}:pending_notifs "<message>"` (queued).
3. **Key absent** → log `"Push Notification Sent to User"` + set cooldown key.

### The CRON Sweeper (`NotificationScheduler`)

Runs every **5 minutes** via `@Scheduled(fixedRate = 300_000)`.

For every `user:*:pending_notifs` key in Redis:
1. `LRANGE key 0 -1` — fetch all messages.
2. `DEL key` — clear the list.
3. Log: `"Summarized Push Notification: Bot X and [N] others interacted with your posts."`

---

## Phase 4 — Race Condition / Stress Test

### Setup: create 200 bots first

```bash
for i in $(seq 1 200); do
  curl -s -X POST http://localhost:8081/api/bots \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Bot$i\",\"personaDescription\":\"Stress bot $i\"}" > /dev/null
done
```

### Run the stress test

```bash
node stress-test.js 1 1
# args: <postId> <targetUserId>
```

**Expected output:**

```
── Results ─────────────────────────────────────────
  HTTP 201  ✅ Accepted               → 100 requests
  HTTP 429  🚫 Rejected (cap)         → 100 requests

── Verdict ─────────────────────────────────────────
  ✅ PASS  — Horizontal cap held perfectly at 100.
────────────────────────────────────────────────────
```

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| `AuthorType` enum on entities | Avoids a polymorphic join table; keeps schema simple and explicit. |
| INCR + conditional DECR (not Lua) | INCR is already atomic. Simpler, equally correct, easier to audit. Lua adds complexity with no benefit here. |
| `SET NX EX` for cooldown | Single-command atomicity; no `EXISTS` + `SET` race possible. TTL = automatic cleanup. |
| Redis as gatekeeper, Postgres as truth | Guardrails run before DB writes. On DB failure, Redis counter is rolled back. Both stores stay consistent. |
| `@Scheduled(fixedRate)` | Simpler than cron expression for a fixed 5-minute interval; avoids timezone issues in Docker. |
| `StringRedisTemplate` only | All values are numeric strings or plain text. No Jackson/JDK serialization overhead — leaner and faster. |
