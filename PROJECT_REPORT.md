# Project Report: AI Cognitive Memory System — JavaAPI

**Date:** 2026-06-07
**Version:** MVP 1 (complete)
**Language / Runtime:** Java 21, Spring Boot 4.0.6
**Database:** PostgreSQL
**Cache / Session store:** Redis (configured, reserved for future use)
**External dependency:** Python FastAPI AI service

---

## Table of Contents

1. [What This System Does](#1-what-this-system-does)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Project Structure](#3-project-structure)
4. [Database Schema](#4-database-schema)
5. [Feature List](#5-feature-list)
6. [Complete API Reference](#6-complete-api-reference)
7. [Data Flow — Request Lifecycle](#7-data-flow--request-lifecycle)
   - 7.1 Authentication Flow
   - 7.2 Token Refresh Flow
   - 7.3 Chat Pipeline Flow (POST /ai/chat)
   - 7.4 Memory Store Flow
   - 7.5 Memory Retrieve Flow
   - 7.6 Session Management Flow
8. [Component Deep-Dive](#8-component-deep-dive)
9. [DTO Schema Reference](#9-dto-schema-reference)
10. [Security Model](#10-security-model)
11. [Configuration Reference](#11-configuration-reference)
12. [How to Run Locally](#12-how-to-run-locally)
13. [Known Limitations & Next Steps](#13-known-limitations--next-steps)

---

## 1. What This System Does

The AI Cognitive Memory System is a REST API backend that gives an AI assistant a persistent,
user-scoped memory. Every message a user sends is saved to a vector memory store (via the Python
AI service). Before the AI answers, the system retrieves the most relevant past memories and
injects them as context, so the AI can give coherent, contextually aware responses across
separate sessions and long time gaps.

Key capabilities:
- Users register, log in, and authenticate via short-lived JWT access tokens + long-lived refresh
  tokens.
- Users create named chat sessions and send messages.
- Every message is persisted to PostgreSQL for history retrieval.
- Every message is also stored in the Python vector memory service.
- When a new message arrives, the top-5 most semantically similar past memories are retrieved and
  injected into the AI prompt as context.
- The AI response is saved, and the full exchange is returned to the client.
- Users can also call the memory endpoints directly to store or retrieve memories outside the chat
  flow.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Client (mobile / web)                      │
└───────────────────────────────┬─────────────────────────────────────┘
                                │  HTTPS  (JSON)
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Spring Boot API  (port 8080)                    │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                      Filter Chain                           │   │
│  │  RateLimitingFilter (5 req / 60s per IP)                    │   │
│  │        → JwtFilter  (validates Bearer token)                │   │
│  │        → UsernamePasswordAuthenticationFilter               │   │
│  └───────────────────────────┬─────────────────────────────────┘   │
│                              │                                      │
│  ┌───────────────┐  ┌───────────────┐  ┌────────────────────────┐  │
│  │ PublicCtrl    │  │  AiController  │  │  SessionController     │  │
│  │ /public/**    │  │  /ai/**        │  │  /sessions/**          │  │
│  └───────┬───────┘  └──────┬────────┘  └──────────┬─────────────┘  │
│          │                 │                       │                │
│  ┌───────────────┐  ┌─────────────────────────────────────────────┐ │
│  │MemoryController│  │          MemoryController                   │ │
│  │  /memory/**    │  │   Legacy AIController /llm/**               │ │
│  └───────┬───────┘  └─────────────────────────────────────────────┘ │
│          │                 │                                        │
│          └──────┬──────────┘                                        │
│                 │                                                   │
│  ┌──────────────▼─────────────────────────────────────────────┐    │
│  │                   PythonAiGateway                           │    │
│  │  (Spring RestClient — base URL: ai.base-url)               │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
│                             │                                       │
│  ┌──────────────────────────▼──────────────────────────────────┐    │
│  │  Spring Data JPA / Hibernate → PostgreSQL                   │    │
│  │  Tables: users, user_roles, refresh_tokens,                 │    │
│  │          chat_sessions, chat_messages                       │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
                             │
                             │ HTTP (JSON)
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│            Python FastAPI AI Service  (port 8000)                   │
│                                                                     │
│  POST /memory/store    — embed + persist text to vector store       │
│  POST /memory/retrieve — semantic search, return top-k strings      │
│  POST /ai/chat         — generate AI answer with context injected   │
│  POST /chat            — legacy chat endpoint (no context param)    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Project Structure

```
JavaAPI/
├── pom.xml                          Maven build descriptor
├── application.properties.example  Config template (safe to commit)
├── PROJECT_REPORT.md                This document
│
└── src/main/java/com/CognitiveMemory/demo/
    │
    ├── SpringAPI.java               @SpringBootApplication entry point
    │
    ├── config/
    │   ├── SecurityConfig.java      Filter chain, CSRF off, stateless, /public/** open
    │   └── AiClientConfig.java      Produces RestClient bean pointed at ai.base-url
    │
    ├── controller/
    │   ├── PublicController.java    /public/** — signup, login, refresh (no auth required)
    │   ├── MemoryController.java    /memory/** — store and retrieve memories
    │   ├── AiController.java        /ai/**     — POST /ai/chat full pipeline (NEW)
    │   ├── SessionController.java   /sessions/** — session CRUD + message flow
    │   └── AIController.java        /llm/**    — legacy stubs (kept for compatibility)
    │
    ├── dto/
    │   ├── LoginRequest.java            {userNameOrEmail, password}
    │   ├── JwtResponse.java             {accessToken, refreshToken, username}
    │   ├── RefreshTokenRequest.java     {refreshToken}
    │   ├── AiChatRequest.java           {userId, sessionId, message}       ← used by legacy /llm
    │   ├── AiChatResponse.java          {answer}
    │   ├── AiChatBody.java              {message, sessionId}               ← used by /ai/chat (NEW)
    │   ├── AiSendRequest.java           {userId, message, context}         ← sent to Python /ai/chat (NEW)
    │   ├── MemoryStoreRequest.java      {userId, sessionId, text, role}    ← used by /sessions pipeline
    │   ├── MemoryStoreBody.java         {content, role}                    ← used by /memory/store (NEW)
    │   ├── MemoryRetrieveRequest.java   {userId, query, topK}
    │   ├── MemoryRetrieveResponse.java  {memories: List<String>}
    │   ├── CreateSessionRequest.java    {title}
    │   ├── PostMessageRequest.java      {message}
    │   ├── SessionCreateResponse.java   {sessionId, createdAt}             (NEW)
    │   └── SessionSummaryResponse.java  {sessionId, title, createdAt, messageCount} (NEW)
    │
    ├── entity/
    │   ├── User.java                @Entity users table
    │   └── RefreshToken.java        @Entity refresh_tokens table
    │
    ├── filter/
    │   ├── JwtFilter.java           Reads Authorization: Bearer, sets SecurityContext
    │   └── RateLimitingFilter.java  In-memory per-IP bucket (5 req / 60s)
    │
    ├── gateway/
    │   └── PythonAiGateway.java     All outbound HTTP calls to Python AI service
    │
    ├── Repository/
    │   ├── UserRepository.java
    │   └── RefreshTokenRepository.java
    │
    ├── service/
    │   ├── UserService.java
    │   ├── UserDetailsServiceImp.java
    │   └── RefreshTokenService.java
    │
    ├── sessions/
    │   ├── entity/
    │   │   ├── ChatSession.java
    │   │   └── ChatMessage.java
    │   ├── Repository/
    │   │   ├── ChatSessionRepository.java
    │   │   └── ChatMessageRepository.java   (extended with history + count queries)
    │   └── service/
    │       └── CurrentUserService.java      Reads SecurityContext → User entity
    │
    └── utils/
        └── JwtUtil.java             HMAC-SHA JWT generation and validation
```

---

## 4. Database Schema

### Table: `users`

| Column      | Type         | Constraints              |
|-------------|--------------|--------------------------|
| id          | INTEGER      | PK, auto-increment       |
| user_name   | VARCHAR      | UNIQUE, NOT NULL         |
| first_name  | VARCHAR      | nullable                 |
| last_name   | VARCHAR      | nullable                 |
| mobile_no   | VARCHAR      | nullable                 |
| email       | VARCHAR      | UNIQUE, NOT NULL         |
| password    | VARCHAR      | NOT NULL (BCrypt hash)   |

### Table: `user_roles` (collection table on User)

| Column      | Type    | Constraints         |
|-------------|---------|---------------------|
| user_id     | INTEGER | FK → users.id       |
| roles       | VARCHAR | e.g. "USER","ADMIN" |

### Table: `refresh_tokens`

| Column      | Type        | Constraints           |
|-------------|-------------|-----------------------|
| id          | INTEGER     | PK, auto-increment    |
| user_id     | INTEGER     | FK → users.id, UNIQUE |
| token       | VARCHAR     | UNIQUE, NOT NULL      |
| expiry_date | TIMESTAMP   | NOT NULL              |

### Table: `chat_sessions`

| Column     | Type        | Constraints        |
|------------|-------------|--------------------|
| id         | BIGINT      | PK, auto-increment |
| user_id    | INTEGER     | FK → users.id      |
| title      | VARCHAR     | NOT NULL           |
| created_at | TIMESTAMP   | NOT NULL           |

### Table: `chat_messages`

| Column     | Type          | Constraints               |
|------------|---------------|---------------------------|
| id         | BIGINT        | PK, auto-increment        |
| session_id | BIGINT        | FK → chat_sessions.id     |
| role       | VARCHAR       | NOT NULL ("user"/"assistant") |
| content    | VARCHAR(8000) | NOT NULL                  |
| created_at | TIMESTAMP     | NOT NULL                  |

**Entity relationships:**

```
User ──< ChatSession ──< ChatMessage
User ──── RefreshToken  (one-to-one)
```

---

## 5. Feature List

### Authentication & Identity
- **Sign up** — register a new user with username, email, and password (BCrypt-hashed).
- **Login** — authenticate and receive a short-lived JWT access token (15 min) + long-lived
  refresh token (7 days).
- **Token refresh** — exchange a valid refresh token for a new access + refresh token pair
  (rotation: old token is deleted on use).
- **Stateless sessions** — no server-side HTTP sessions; every request carries a Bearer token.

### Rate Limiting
- 5 requests per 60 seconds per client IP.
- Requests exceeding the limit receive HTTP 429.

### Memory System
- **Store memory** — directly store a piece of text with a role tag into the vector store via
  POST /memory/store.
- **Retrieve memories** — semantic search over the user's past memories with a configurable
  topK via POST /memory/retrieve.
- Every chat message (user and assistant) is automatically stored in the vector memory as part of
  the chat pipeline.

### Chat Pipeline (POST /ai/chat)
A single request triggers a 7-step pipeline:
1. Persist the user message to PostgreSQL.
2. Store the user message in the Python vector memory.
3. Retrieve the top-5 semantically relevant past memories.
4. Build a context string from those memories.
5. Call the Python AI model with the message + context.
6. Persist the AI response to PostgreSQL.
7. Return the AI response to the client.

### Session Management
- **Create session** — create a named (or default "New chat") session. Returns session ID and
  creation timestamp.
- **List sessions** — list all sessions for the authenticated user ordered by newest first,
  including the message count per session.
- **Session history** — retrieve all messages in a session in chronological order.
  Returns 403 if the session belongs to a different user.
- **Post message** — (via /sessions/{id}/message) the original session-based chat flow which also
  stores messages and calls the AI.

---

## 6. Complete API Reference

### Public Endpoints (no authentication required)

#### `GET /public/health-check`
Health probe. Returns 200 OK.

#### `POST /public/signup`
Register a new user.

**Request body:**
```json
{
  "userName": "alice",
  "email": "alice@example.com",
  "password": "s3cur3P@ss",
  "firstName": "Alice",
  "lastName": "Smith",
  "mobileNo": "0000000000"
}
```
**Response:** 200 OK — user created.

#### `POST /public/login`
Authenticate and receive tokens.

**Request body:**
```json
{ "userNameOrEmail": "alice", "password": "s3cur3P@ss" }
```
**Response 200:**
```json
{
  "accessToken": "<JWT>",
  "refreshToken": "<UUID>",
  "username": "alice"
}
```

#### `POST /public/refresh`
Exchange a refresh token for new token pair.

**Request body:**
```json
{ "refreshToken": "<UUID>" }
```
**Response 200:** Same shape as login response.

---

### Authenticated Endpoints (require `Authorization: Bearer <JWT>`)

#### Memory

##### `POST /memory/store`
Store a single memory entry for the current user.

**Request body:**
```json
{ "content": "I prefer concise code with no extra comments.", "role": "user" }
```
**Responses:**
- `200 OK` — stored successfully.
- `400 Bad Request` — `content` or `role` is blank (validation).
- `503 Service Unavailable` — Python memory service is unreachable.

##### `POST /memory/retrieve`
Semantic search over the current user's memories.

**Query param:** `topK` (default 5)
**Request body:** plain text query string
**Response 200:**
```json
{ "memories": ["memory text 1", "memory text 2", ...] }
```

---

#### AI Chat

##### `POST /ai/chat`
Full cognitive chat pipeline. Requires an existing session.

**Request body:**
```json
{ "message": "What was my main concern last week?", "sessionId": 3 }
```
**Responses:**
- `200 OK`
  ```json
  { "answer": "Based on our last conversation, your main concern was..." }
  ```
- `400 Bad Request` — missing/blank fields.
- `403 Forbidden` — session belongs to a different user.
- `404 Not Found` — session does not exist.
- `503 Service Unavailable` — Python AI service is unreachable.

---

#### Sessions

##### `POST /sessions/create`
Create a new chat session.

**Request body:** `{ "title": "Work planning" }` (title is optional; defaults to "New chat")
**Response 201:**
```json
{ "sessionId": 7, "createdAt": "2026-06-07T10:00:00Z" }
```

##### `GET /sessions/list`
List all sessions with message counts.

**Response 200:**
```json
[
  { "sessionId": 7, "title": "Work planning", "createdAt": "2026-06-07T10:00:00Z", "messageCount": 12 },
  { "sessionId": 3, "title": "New chat",       "createdAt": "2026-06-01T08:30:00Z", "messageCount": 4  }
]
```

##### `GET /sessions/{sessionId}/history`
All messages in a session, oldest first.

**Responses:**
- `200 OK` — array of `ChatMessage` objects.
- `403 Forbidden` — session belongs to a different user.
- `404 Not Found` — session not found.

**Response 200 (example item):**
```json
[
  { "id": 1, "role": "user",      "content": "Hello!",           "createdAt": "..." },
  { "id": 2, "role": "assistant", "content": "Hi! How can I help?", "createdAt": "..." }
]
```

---

#### Session-based Chat (original endpoints, kept intact)

##### `POST /sessions` — create session (returns full entity)
##### `GET /sessions` — list sessions (no message count)
##### `GET /sessions/{sessionId}/messages` — top-50 messages, newest first
##### `POST /sessions/{sessionId}/message`
Send a message and get an AI response via the session pipeline.

**Request body:** `{ "message": "What do you know about me?" }`
**Response 200:** The saved assistant `ChatMessage` entity.

---

#### Legacy Stubs (kept for backward compatibility)

##### `POST /llm/request` — echoes the request body back
##### `GET /llm/response` — returns static string

---

## 7. Data Flow — Request Lifecycle

### 7.1 Authentication Flow

```
Client
  │
  ├─► POST /public/login  { userNameOrEmail, password }
  │         │
  │         ├─ AuthenticationManager.authenticate()
  │         │       └─ UserDetailsServiceImp.loadUserByUsername()
  │         │               └─ UserRepository.findByUserNameOrEmail()
  │         │
  │         ├─ JwtUtil.generateToken(username)       → access token (15 min)
  │         ├─ RefreshTokenService.createRefreshToken(userId) → stored UUID (7 days)
  │         │
  │         └─► { accessToken, refreshToken, username }
  │
  └─► Every subsequent request:
          │
          ├─ RateLimitingFilter: check IP bucket — 429 if over limit
          ├─ JwtFilter: extract "Authorization: Bearer <token>"
          │       ├─ JwtUtil.extractUsername(token)
          │       ├─ UserDetailsServiceImp.loadUserByUsername()
          │       ├─ JwtUtil.validateToken(token, userDetails)
          │       └─ SecurityContextHolder.setAuthentication(...)
          │
          └─► Controller receives request with authenticated principal
```

### 7.2 Token Refresh Flow

```
Client
  │
  └─► POST /public/refresh  { refreshToken: "<UUID>" }
            │
            ├─ RefreshTokenService.findByToken(uuid)
            ├─ RefreshTokenService.verifyExpiration(token)  → deletes + throws if expired
            ├─ RefreshTokenService.deleteByUserId(userId)   → invalidate old token
            │
            ├─ JwtUtil.generateToken(username)              → new access token
            ├─ RefreshTokenService.createRefreshToken(userId) → new refresh token
            │
            └─► { accessToken, refreshToken, username }
```

### 7.3 Chat Pipeline Flow (POST /ai/chat)

This is the core of the system — the 7-step cognitive memory pipeline:

```
Client
  │
  └─► POST /ai/chat  { message, sessionId }
            │
            │  [Auth via JwtFilter — userId extracted from SecurityContext]
            │
      AiController
            │
            ├─ STEP 0: Verify session ownership
            │       ChatSessionRepository.findById(sessionId)
            │       → 404 if not found
            │       → 403 if session.user ≠ current user
            │
            ├─ STEP 1: Persist user message
            │       ChatMessageRepository.save({session, role="user", content, createdAt})
            │       → chatMessageId assigned by DB
            │
            ├─ STEP 2: Store message in vector memory
            │       PythonAiGateway.storeMemory(userId, message, "user")
            │       → POST http://python-service/memory/store
            │       → throws RestClientException on failure → caught as 503
            │
            ├─ STEP 3: Retrieve relevant past memories (semantic search)
            │       PythonAiGateway.retrieveMemory(userId, message, topK=5)
            │       → POST http://python-service/memory/retrieve
            │       → returns List<String> of relevant past texts
            │       → throws RestClientException on failure → caught as 503
            │
            ├─ STEP 4: Build context string
            │       context = memories.join("\n")   (empty string if no memories)
            │
            ├─ STEP 5: Call AI model with message + context
            │       PythonAiGateway.sendToAi(userId, message, context)
            │       → POST http://python-service/ai/chat  { userId, message, context }
            │       → returns String answer
            │       → throws RestClientException on failure → caught as 503
            │
            ├─ STEP 6: Persist AI response
            │       ChatMessageRepository.save({session, role="assistant", content=answer, createdAt})
            │
            └─ STEP 7: Return to client
                    ResponseEntity.ok({ answer })
```

**Error paths in the pipeline:**

| Point of failure           | HTTP status | Message                                        |
|---------------------------|-------------|------------------------------------------------|
| Session not found         | 404         | "Session not found"                            |
| Session belongs to other  | 403         | "Access denied"                                |
| Python service unreachable| 503         | "AI service is currently unavailable..."       |
| DB error or other         | 500         | "Failed to process chat request"               |

### 7.4 Memory Store Flow (POST /memory/store)

```
Client
  │
  └─► POST /memory/store  { content, role }
            │
      MemoryController
            ├─ currentUserService.requireUser()  → User entity
            ├─ PythonAiGateway.storeMemory(userId, content, role)
            │       → POST http://python-service/memory/store
            │       → { userId, sessionId: null, text: content, role }
            │
            ├─ Success  → 200 OK (empty body)
            ├─ Python down → RestClientException → 503
            └─ Other error → 500
```

### 7.5 Memory Retrieve Flow (POST /memory/retrieve)

```
Client
  │
  └─► POST /memory/retrieve?topK=5  (body: plain text query)
            │
      MemoryController
            ├─ currentUserService.requireUser()  → User entity
            ├─ PythonAiGateway.retrieveMemories(MemoryRetrieveRequest)
            │       → POST http://python-service/memory/retrieve
            │       → { userId, query, topK }
            │
            └─► { memories: ["text1", "text2", ...] }
```

### 7.6 Session Management Flow

```
POST /sessions/create
  │
  ├─ requireUser()
  ├─ ChatSessionRepository.save({ user, title, createdAt })
  └─► 201  { sessionId, createdAt }

GET /sessions/list
  │
  ├─ requireUser()
  ├─ ChatSessionRepository.findByUserOrderByCreatedAtDesc(user)
  ├─ For each session: ChatMessageRepository.countBySession(session)
  └─► 200  [{ sessionId, title, createdAt, messageCount }, ...]

GET /sessions/{sessionId}/history
  │
  ├─ requireUser()
  ├─ ChatSessionRepository.findById(sessionId)  → 404 if absent
  ├─ session.user ≠ current user  → 403
  ├─ ChatMessageRepository.findBySessionOrderByCreatedAtAsc(session)
  └─► 200  [{ id, role, content, createdAt }, ...]  (all messages, oldest first)
```

---

## 8. Component Deep-Dive

### PythonAiGateway

The single point of contact between the Java API and the Python AI service. Uses Spring's
`RestClient` (non-reactive blocking client) configured with the base URL from `ai.base-url`.

**Method inventory:**

| Method | Signature | Error behavior |
|--------|-----------|----------------|
| `chat` | `(AiChatRequest) → AiChatResponse` | catches exception, returns default message |
| `storeMemory` | `(MemoryStoreRequest) → boolean` | catches exception, returns false |
| `retrieveMemories` | `(MemoryRetrieveRequest) → MemoryRetrieveResponse` | catches exception, returns empty list |
| `storeMemory` *(new)* | `(userId, content, role) → void` | throws RestClientException |
| `retrieveMemory` *(new)* | `(userId, query, topK) → List<String>` | throws RestClientException |
| `sendToAi` *(new)* | `(userId, message, context) → String` | throws RestClientException |

The first three methods (used by the legacy session pipeline) are lenient — they swallow errors to
avoid crashing the original chat flow. The three new methods throw, so `AiController` and
`MemoryController` can return proper 503 responses.

### CurrentUserService

Reads the authenticated principal from `SecurityContextHolder`, then loads the full `User` entity
from the database. Throws a `RuntimeException` if the context is empty or the user is not found.
Every authenticated controller calls `requireUser()` as its first step.

### JwtFilter

Extracts the JWT from the `Authorization: Bearer` header on every request (except `/public/**`).
Validates the token signature and expiry using `JwtUtil`, then populates the Spring Security
context so downstream code can identify the user.

### RateLimitingFilter

Runs before `JwtFilter`. Maintains an in-memory `ConcurrentHashMap` of `{IP → (count,
windowStart)}`. If the count exceeds `request.max-requests` within `request.time-window-ms`
milliseconds, the filter writes HTTP 429 directly and does not pass the request further.

### RefreshTokenService

Manages the refresh token lifecycle. Tokens are UUIDs stored in the `refresh_tokens` table.
On each call to `createRefreshToken`, any existing token for that user is deleted (rotation). The
service also checks expiry and deletes expired tokens immediately when found.

---

## 9. DTO Schema Reference

### Inbound DTOs (client → API)

| DTO | Fields | Used by |
|-----|--------|---------|
| `LoginRequest` | `userNameOrEmail: String`, `password: String` | POST /public/login |
| `RefreshTokenRequest` | `refreshToken: String` | POST /public/refresh |
| `AiChatBody` | `message: String` @NotBlank, `sessionId: Long` @NotNull | POST /ai/chat |
| `MemoryStoreBody` | `content: String` @NotBlank, `role: String` @NotBlank | POST /memory/store |
| `CreateSessionRequest` | `title: String` (optional) | POST /sessions, /sessions/create |
| `PostMessageRequest` | `message: String` | POST /sessions/{id}/message |

### Outbound DTOs (API → client)

| DTO | Fields | Returned by |
|-----|--------|-------------|
| `JwtResponse` | `accessToken`, `refreshToken`, `username` | login, refresh |
| `AiChatResponse` | `answer: String` | POST /ai/chat, /sessions/{id}/message |
| `SessionCreateResponse` | `sessionId: Long`, `createdAt: Instant` | POST /sessions/create |
| `SessionSummaryResponse` | `sessionId`, `title`, `createdAt`, `messageCount: long` | GET /sessions/list |
| `MemoryRetrieveResponse` | `memories: List<String>` | POST /memory/retrieve |

### Gateway DTOs (API → Python service)

| DTO | Fields | Sent to Python endpoint |
|-----|--------|------------------------|
| `AiChatRequest` | `userId`, `sessionId`, `message` | POST /chat (legacy) |
| `AiSendRequest` | `userId`, `message`, `context` | POST /ai/chat |
| `MemoryStoreRequest` | `userId`, `sessionId`, `text`, `role` | POST /memory/store |
| `MemoryRetrieveRequest` | `userId`, `query`, `topK` | POST /memory/retrieve |

---

## 10. Security Model

### Token strategy

```
Access token:   JWT, HMAC-SHA, 15-minute TTL
                Payload: { sub: username, iat, exp }
                Transmitted: Authorization: Bearer <token>

Refresh token:  UUID, stored in DB, 7-day TTL
                Transmitted: in JSON body of /public/refresh
                Rotated on every use (old token deleted immediately)
```

### Endpoint access rules

| Path pattern | Rule |
|--------------|------|
| `/public/**` | `permitAll()` — no token required |
| all others | `authenticated()` — valid JWT required |

### Password storage

Passwords are hashed with `BCryptPasswordEncoder` before persistence. Plain-text passwords are
never stored.

### Known security gaps (production readiness)

- JWT secret is currently in `application.properties`. Should be injected from an environment
  variable or secret manager.
- No HTTPS enforcement at the application layer (deploy behind a TLS-terminating proxy).
- No account lockout on repeated login failures.
- `RateLimitingFilter` uses an in-memory store — resets on restart and does not work across
  multiple instances.

---

## 11. Configuration Reference

Copy `application.properties.example` to `src/main/resources/application.properties` and fill in
your values.

| Property key | Description | Example value |
|---|---|---|
| `spring.datasource.url` | JDBC URL for PostgreSQL | `jdbc:postgresql://localhost:5432/cognitive_memory` |
| `spring.datasource.username` | DB user | `postgres` |
| `spring.datasource.password` | DB password | `secret` |
| `spring.jpa.hibernate.ddl-auto` | Schema strategy | `update` (dev) / `validate` (prod) |
| `spring.data.redis.host` | Redis host | `localhost` |
| `spring.data.redis.port` | Redis port | `6379` |
| `jwt.secret` | HMAC key for JWT signing | 32+ char random string |
| `jwt.expiration.ms` | Access token TTL in ms | `900000` (15 min) |
| `jwt.refresh.expiration.ms` | Refresh token TTL in ms | `604800000` (7 days) |
| `python.ai.service.base-url` | Python AI base URL — stored as `ai.base-url` in code | `http://localhost:8000` |
| `python.ai.service.bearer-token` | Auth header for Python service (reserved) | `<token>` |
| `request.max-requests` | Rate limit — requests per window | `5` |
| `request.time-window-ms` | Rate limit — window duration in ms | `60000` |

---

## 12. How to Run Locally

**Prerequisites:**
- Java 21
- Maven (wrapper `mvnw.cmd` included)
- PostgreSQL running and a database named `cognitive_memory` created
- Python FastAPI AI service running on port 8000

**Step 1 — Configure**
```
copy application.properties.example src\main\resources\application.properties
# Edit the file and fill in your DB credentials and JWT secret
```

**Step 2 — Build**
```powershell
.\mvnw.cmd -q clean package -DskipTests
```

**Step 3 — Run**
```powershell
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8080`.

**Quick-start curl sequence:**
```bash
# 1. Sign up
curl -s -X POST http://localhost:8080/public/signup \
  -H "Content-Type: application/json" \
  -d '{"userName":"alice","email":"alice@test.com","password":"pass123"}'

# 2. Login → copy accessToken
curl -s -X POST http://localhost:8080/public/login \
  -H "Content-Type: application/json" \
  -d '{"userNameOrEmail":"alice","password":"pass123"}'

# 3. Create a session  (use token from step 2)
curl -s -X POST http://localhost:8080/sessions/create \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"My first chat"}'

# 4. Send a chat message (use sessionId from step 3)
curl -s -X POST http://localhost:8080/ai/chat \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello, what do you know about me?","sessionId":1}'

# 5. View full history
curl -s http://localhost:8080/sessions/1/history \
  -H "Authorization: Bearer <TOKEN>"

# 6. List all sessions with message counts
curl -s http://localhost:8080/sessions/list \
  -H "Authorization: Bearer <TOKEN>"
```

---

## 13. Known Limitations & Next Steps

### Current limitations
- `RateLimitingFilter` is in-memory — not suitable for multi-instance deployments (Redis-backed
  bucket would fix this).
- No API documentation (Swagger/OpenAPI) — clients must refer to this report.
- No DB migration tool (Flyway/Liquibase) — schema is managed by Hibernate `ddl-auto=update`.
- No retry or circuit-breaker around `PythonAiGateway` — a slow Python service will block the
  calling thread for the full HTTP timeout.
- Secrets (`jwt.secret`, DB password) live in `application.properties` — must be externalised
  before production.
- Redis is in the dependency list but not yet used.
- No Dockerfile or docker-compose for local stack orchestration.
- `chat_messages.content` is capped at 8000 chars — very long AI responses will be truncated.

### Recommended next steps (priority order)
1. **Externalise secrets** — read `jwt.secret`, DB creds, and Python service token from environment
   variables or a secrets manager.
2. **Resilience** — add `Resilience4j` circuit-breaker + retry on `PythonAiGateway`; configure
   `RestClient` connection and read timeouts.
3. **Redis rate limiter** — replace in-memory map with a Redis sliding-window counter so rate
   limits survive restarts and work across instances.
4. **Flyway migrations** — replace `ddl-auto=update` with versioned SQL migrations.
5. **OpenAPI docs** — add `springdoc-openapi-starter-webmvc-ui` for auto-generated Swagger UI.
6. **Tests** — unit tests for services, controller-layer slice tests (`@WebMvcTest`) with mocked
   gateway, and one integration test per endpoint.
7. **CI pipeline** — GitHub Actions: build → test → Docker build on push to main.
8. **Dockerfile + docker-compose** — containerise the Spring API, PostgreSQL, Redis, and Python
   service for one-command local startup.
9. **Observability** — add Micrometer metrics, structured JSON logging, and optional distributed
   tracing (Micrometer Tracing / OpenTelemetry).
10. **Bearer-token support for Python gateway** — wire `python.ai.service.bearer-token` into
    `AiClientConfig` so the Python service can be secured.
