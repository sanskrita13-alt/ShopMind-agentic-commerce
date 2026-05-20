# ShopMind — AI Footwear Decision Intelligence

ShopMind is a full-stack AI shopping assistant that helps users find footwear they won't regret. It replaces generic product grids with a guided conversation, scores every product against the user's stated priorities, and explains every recommendation with transparent reasoning and tradeoff analysis.

#Demo link:- https://drive.google.com/file/d/1S3y-g1Vj3OxC8WNvea6t9ybEF-H3xsk-/view?usp=sharing
---

## Table of Contents

1. [What it does](#what-it-does)
2. [Architecture overview](#architecture-overview)
3. [Project structure](#project-structure)
4. [Tech stack](#tech-stack)
5. [Prerequisites](#prerequisites)
6. [Getting started](#getting-started)
   - [Backend setup](#backend-setup)
   - [Frontend setup](#frontend-setup)
7. [Environment variables](#environment-variables)
   - [Backend (.env)](#backend-env)
   - [Frontend (.env)](#frontend-env)
8. [AI providers](#ai-providers)
   - [Groq (recommended)](#groq-recommended)
   - [Gemini (alternative)](#gemini-alternative)
   - [Mock (offline / testing)](#mock-offline--testing)
9. [How the conversation works](#how-the-conversation-works)
10. [Frontend pages and phases](#frontend-pages-and-phases)
11. [Backend API reference](#backend-api-reference)
12. [Key backend services](#key-backend-services)
13. [Database](#database)
14. [Product catalog](#product-catalog)
15. [Running in production](#running-in-production)
16. [Troubleshooting](#troubleshooting)

---

## What it does

- **Conversational discovery** — the AI asks focused questions (use case, hours on feet, budget, comfort vs style) and adjusts each question based on what the user has already said.
- **Intent extraction** — every user message is parsed into a structured intent object: `primaryUseCase`, `budget`, `comfortPriority`, `stylePriority`, `terrainType`, etc.
- **Product scoring** — every product in the catalog is scored against the intent across comfort, durability, style, and budget fit.
- **Shortlist** — the top 3 products are returned with match scores (0–100%), comfort/durability/style scores, reasoning, and tradeoff analysis.
- **Regret prevention** — products are flagged with warnings (sizing issues, cushioning gaps for long wear, weight concerns) before the user buys.
- **Compare** — side-by-side head-to-head view with per-row winners highlighted.
- **Deep dive** — full product detail with retailer comparison table, pricing, delivery estimates, and return policy.

---

## Architecture overview

```
┌─────────────────────────────────────────────┐
│  Browser (React SPA)                        │
│  Landing page → Conversational Chat UI      │
│  Phases: discovery → shortlist → compare    │
│           → deep dive                       │
└──────────────────┬──────────────────────────┘
                   │  HTTP  (Axios)
                   │  VITE_API_URL=http://localhost:8080/api/v1
                   ▼
┌─────────────────────────────────────────────┐
│  Spring Boot Backend  (:8080)               │
│                                             │
│  ConversationController                     │
│      └─ ConversationService                 │
│           ├─ AiService (interface)          │
│           │    ├─ GroqAiService    ◄── recommended
│           │    ├─ GeminiAiService  ◄── alternative
│           │    └─ MockAiService    ◄── default / fallback
│           ├─ ShopifyProductService          │
│           └─ MockProductService             │
│                                             │
│  H2 in-memory DB (dev)                      │
│  PostgreSQL (prod)                          │
└─────────────────────────────────────────────┘
                   │
                   ▼
         Groq / Gemini REST API
         (llama-3.3-70b-versatile)
```

Each user message triggers one AI call that simultaneously:
1. Extracts structured intent from the full conversation history
2. Decides what to ask next (or triggers recommendations if confidence ≥ 0.65)

---

## Project structure

```
shopmind/
├── README.md
├── frontend/                          # React + Vite SPA
│   ├── src/
│   │   ├── main.tsx                   # Entry point
│   │   ├── App.tsx                    # Router (/ and /chat)
│   │   ├── pages/
│   │   │   ├── Landing.tsx            # Hero page with search bar
│   │   │   └── Chat.tsx               # Full chat + shortlist + compare + deep dive
│   │   ├── store/
│   │   │   └── chat.ts                # Zustand state (session, messages, intent, recs)
│   │   ├── lib/
│   │   │   ├── api.ts                 # Axios API client
│   │   │   └── types.ts               # Shared TypeScript types
│   │   └── index.css                  # Global design tokens + component styles
│   ├── .env                           # VITE_API_URL (committed template)
│   └── package.json
│
└── backend/                           # Spring Boot 3 / Java 21
    ├── .env                           # Real secrets — loaded automatically at startup
    ├── pom.xml
    └── src/main/java/com/shopmind/
        ├── ShopMindApplication.java
        ├── config/
        │   ├── AiServiceConfig.java   # Selects Groq / Gemini / Mock bean
        │   ├── CorsConfig.java
        │   ├── RedisConfig.java
        │   └── WebClientConfig.java
        ├── controller/
        │   └── ConversationController.java
        ├── service/
        │   ├── ai/
        │   │   ├── AiService.java         # Interface
        │   │   ├── GroqAiService.java     # Groq (OpenAI-compatible)
        │   │   ├── GeminiAiService.java   # Google Gemini
        │   │   └── MockAiService.java     # Deterministic fallback
        │   ├── conversation/
        │   │   └── ConversationService.java
        │   └── product/
        │       ├── ShopifyProductService.java
        │       └── MockProductService.java
        ├── entity/                    # JPA entities
        │   ├── ConversationSession.java
        │   ├── ConversationMessage.java
        │   ├── UserIntent.java
        │   ├── Recommendation.java
        │   ├── RegretFlag.java
        │   ├── MerchantOffer.java
        │   └── ProductSnapshot.java
        ├── dto/                       # Request / response objects
        │   ├── AiDtos.java            # ExtractedIntent, QuestionDecision, ProductMatch, etc.
        │   ├── ConversationResponse.java
        │   ├── MessageRequest.java
        │   └── SessionResponse.java
        ├── repository/                # Spring Data JPA repositories
        └── resources/
            ├── application.properties
            └── nike_products.csv      # 51-product mock catalog
```

---

## Tech stack

### Frontend

| Layer | Technology |
|---|---|
| Framework | React 19 |
| Build tool | Vite 7 |
| Language | TypeScript 5 |
| Routing | React Router DOM v7 |
| State management | Zustand 5 |
| HTTP client | Axios |
| Animation | Framer Motion |
| Icons | Lucide React |
| Styling | Tailwind CSS v4 (via `@tailwindcss/vite`) |

### Backend

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.4.5 |
| Language | Java 21 |
| HTTP client | Spring WebFlux WebClient |
| ORM | Spring Data JPA + Hibernate |
| Database (dev) | H2 in-memory |
| Database (prod) | PostgreSQL |
| JSON | Jackson |
| CSV parsing | Apache Commons CSV |
| Env loading | spring-dotenv |
| Boilerplate | Lombok, MapStruct |

---

## Prerequisites

- **Java 21** — `java -version` should show 21.x
- **Maven 3.9+** — `mvn -version`
- **Node.js 20+** — `node -v`
- **npm 10+** — `npm -v`
- A **Groq API key** (free, no credit card) — get one at `console.groq.com/keys`

---

## Getting started

### Backend setup

```bash
cd shopmind/backend
```

**1. Add your Groq API key to `.env`:**

Open `backend/.env` and fill in:

```
GROQ_API_KEY=gsk_your_key_here
```

Everything else is pre-configured. The `spring-dotenv` library loads this file automatically on startup — no manual `export` required.

**2. Start the backend:**

```bash
mvn spring-boot:run
```

The server starts on `http://localhost:8080`. First run downloads dependencies (~2 min). Subsequent runs start in ~7 seconds.

**Verify it's working:**

```bash
curl -X POST http://localhost:8080/api/v1/conversations
# → {"sessionId":"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"}
```

---

### Frontend setup

```bash
cd shopmind/frontend
npm install
npm run dev
```

The app opens at `http://localhost:3000`.

**Available scripts:**

| Command | What it does |
|---|---|
| `npm run dev` | Start Vite dev server on port 3000 with HMR |
| `npm run build` | TypeScript check + production build to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | ESLint check |

---

## Environment variables

### Backend (`.env`)

Located at `backend/.env`. Loaded automatically by `spring-dotenv`.

| Variable | Default | Description |
|---|---|---|
| `SHOPMIND_AI_PROVIDER` | `mock` | AI engine: `groq`, `gemini`, or `mock` |
| `GROQ_API_KEY` | _(empty)_ | Groq API key. If blank, falls back to mock. |
| `GROQ_MODEL` | `llama-3.3-70b-versatile` | Groq model to use |
| `GOOGLE_API_KEY` | _(empty)_ | Gemini API key. Used when provider=gemini. |
| `GEMINI_MODEL` | `gemini-2.0-flash` | Gemini model to use |
| `LLM_MAX_TOKENS` | `2048` | Max output tokens per AI call |
| `SHOPIFY_STORE_DOMAIN` | _(demo)_ | Shopify store domain for live products |
| `SHOPIFY_STOREFRONT_TOKEN` | `mock` | Storefront API token. Set to `mock` for CSV catalog. |
| `CORS_ORIGINS` | `http://localhost:3000` | Comma-separated allowed frontend origins |
| `LOG_LEVEL` | `INFO` | Spring logging level |
| `H2_CONSOLE_ENABLED` | `false` | Enable H2 web console at `/h2-console` |

### Frontend (`.env`)

Located at `frontend/.env`.

| Variable | Default | Description |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080/api/v1` | Backend base URL |

For a different backend URL (e.g. deployed), create `frontend/.env.local`:

```
VITE_API_URL=https://your-api.example.com/api/v1
```

---

## AI providers

### Groq (recommended)

**Why Groq:** Uses custom LPU hardware — responses arrive in ~200–700ms vs ~1–3s for other providers. Free tier gives 30 requests per minute (RPM), enough for real usage without hitting limits.

**Setup:**
1. Create a free account at `console.groq.com`
2. Generate an API key (starts with `gsk_`)
3. Add to `backend/.env`: `GROQ_API_KEY=gsk_...`
4. Set provider: `SHOPMIND_AI_PROVIDER=groq`

**Model options:**

| Model | Speed | Quality | Notes |
|---|---|---|---|
| `llama-3.3-70b-versatile` | Fast | Excellent | Default — best conversational quality |
| `llama3-8b-8192` | Very fast | Good | Use if you need lower latency |
| `mixtral-8x7b-32768` | Fast | Good | Longer context window |

### Gemini (alternative)

**Setup:**
1. Get a key at `aistudio.google.com/app/apikey`
2. Add to `backend/.env`: `GOOGLE_API_KEY=AIza...`
3. Set provider: `SHOPMIND_AI_PROVIDER=gemini`

**Free tier:** 15 RPM. Can hit limits quickly during active testing.

**Model options:** `gemini-2.0-flash` (default), `gemini-2.0-flash-lite` (higher quota), `gemini-1.5-pro` (smarter but slower).

### Mock (offline / testing)

The default mode when no API key is configured. Uses deterministic keyword matching — no network calls, instant responses (~5ms per message).

The mock engine:
- Extracts intent by scanning for keywords (`college`, `gym`, `run`, `comfort`, etc.)
- Asks follow-up questions based on which intent fields are still missing
- Scores products using comfort priority, use-case tag matching, and budget fit
- Falls back automatically from Groq/Gemini on any error or rate limit

**To force mock mode:** Set `SHOPMIND_AI_PROVIDER=mock` in `.env`.

---

## How the conversation works

### Per-message flow

```
User sends message
        │
        ▼
ConversationService.processMessage()
        │
        ├── Save user message to DB
        │
        ├── Build full conversation history
        │
        ├── AiService.extractIntent(history)
        │     └── Single AI call returns:
        │           - Structured ExtractedIntent
        │           - readyToRecommend boolean
        │           - nextQuestion string
        │           - confidenceScore (0–1)
        │           - missingAttributes list
        │
        ├── Save intent version to DB
        │
        ├── if readyToRecommend:
        │       ├── ShopifyProductService.searchProducts()
        │       ├── AiService.rankProducts()  (mock scoring + AI reasoning)
        │       ├── Save recommendations + regret flags + merchant offers
        │       └── Return shortlist to frontend
        │
        └── else:
                └── Return nextQuestion to frontend
```

### Confidence scoring

`confidenceScore = (6 - missingAttributes.length) / 6.0`

The 6 key attributes are: `primaryUseCase`, `walkingDuration`, `budget`, `comfortPriority`, `stylePriority`, `preferredFit`.

Recommendations trigger when confidence ≥ 0.65, all attributes are filled, or the question count hits 8 (hard cap to prevent infinite loops).

### Product scoring (MockAiService)

Each product is scored on:

| Factor | Weight |
|---|---|
| Cushioning vs comfort priority | ±0.2 |
| Use-case tag match | +0.15 |
| Budget fit | ±0.1 |
| Style priority | +0.12 × stylePriority |
| Durability priority | +0.1 × dp × durability% |
| Versatility tags | ±0.1 |
| Terrain match | ±0.08–0.1 |

Final score is clamped to [0.30, 0.99]. When a live AI provider is configured, it rewrites the `reasoning`, `tradeoffs`, and `notSuitableFor` text with personalized explanations referencing the user's actual stated needs.

---

## Frontend pages and phases

### Landing page (`/`)

- Hero section with search bar and popular category pills
- "How it works" section with the 4-stage flow
- Any form submission or pill click navigates to `/chat`

### Chat page (`/chat`)

The chat page has 4 phases managed by Zustand:

| Phase | Triggered when | What the user sees |
|---|---|---|
| `discovery` | Session starts | Conversational chat panel + Decision Intelligence sidebar (fit score, intent summary, explainable fit bars) |
| `shortlist` | AI returns recommendations | 3 product cards with match score, comfort/durability/style bars, regret flags, price |
| `compare` | User clicks "Compare all" | Side-by-side table with per-row winner dots |
| `deepdive` | User taps a product card | Full product detail, retailer table, fit score, match drivers, follow-up ask input |

### State (Zustand — `store/chat.ts`)

| State field | Type | Description |
|---|---|---|
| `sessionId` | `string \| null` | Backend session UUID. `'mock-session'` if backend is unreachable. |
| `messages` | `MessageDTO[]` | Full conversation history |
| `intent` | `IntentDTO \| null` | Latest extracted intent snapshot |
| `recommendations` | `RecommendationDTO[]` | Scored shortlist |
| `phase` | `Phase` | Current UI phase |
| `compareSet` | `string[]` | IDs of products in the compare view (max 3) |
| `activeProductId` | `string \| null` | Product open in deep dive |
| `isLoading` | `boolean` | True while waiting for backend response |

### Offline / mock fallback

If the backend is unreachable, `initSession()` sets `sessionId = 'mock-session'` and `send()` falls back to a built-in context-aware mock that generates responses referencing the user's previous message (e.g. "Campus life — that means long days on your feet…"). After 4 exchanges, it returns a hardcoded shortlist of 3 shoes (Nike Air Zoom Pegasus 41, New Balance Fresh Foam X 1080v14, Brooks Ghost 16).

---

## Backend API reference

Base URL: `http://localhost:8080/api/v1`

### `POST /conversations`

Create a new conversation session.

**Response:**
```json
{
  "sessionId": "4d240483-c570-4475-9b6f-a28813dbd452"
}
```

---

### `POST /conversations/{sessionId}/messages`

Send a user message and get the AI response.

**Request body:**
```json
{
  "content": "I need shoes for college"
}
```

**Response:**
```json
{
  "sessionId": "4d240483-...",
  "assistantMessage": {
    "id": "c8e52816-...",
    "role": "assistant",
    "content": "You mentioned college — how many hours are you on your feet each day?",
    "reasoningStatus": "Walking duration affects cushioning needs.",
    "createdAt": null
  },
  "currentIntent": {
    "primaryUseCase": "college",
    "walkingDuration": null,
    "budget": null,
    "comfortPriority": null,
    "stylePriority": null,
    "durabilityPriority": null,
    "terrainType": "urban",
    "preferredFit": null,
    "needsVersatility": null,
    "confidenceScore": 0.17,
    "missingAttributes": ["walkingDuration", "budget", "comfortPriority", "stylePriority", "preferredFit"]
  },
  "recommendations": [],
  "debugInfo": {
    "processingTimeMs": 691
  }
}
```

When confidence is high enough, `recommendations` is populated with the shortlist:

```json
"recommendations": [
  {
    "id": "...",
    "productId": "nike-001",
    "productName": "Air Zoom Pegasus 41",
    "productBrand": "Nike",
    "productImageUrl": "...",
    "matchScore": 0.87,
    "comfortScore": 88,
    "durabilityScore": 85,
    "styleScore": 82,
    "reasoning": "ReactX foam handles 6-8 hour college days...",
    "tradeoffs": "Athletic silhouette — reads less casual than lifestyle sneakers.",
    "notSuitableFor": "Formal or business-casual outfits.",
    "price": 10799.0,
    "currency": "INR",
    "rank": 1,
    "regretFlags": [
      {
        "type": "WEIGHT",
        "title": "Heavier than minimalist trainers",
        "description": "280g — noticeably bulkier than ultra-lights.",
        "severity": "LOW"
      }
    ],
    "merchantOffers": [
      {
        "merchantName": "Nike India",
        "price": 10799.0,
        "currency": "INR",
        "deliveryEstimate": "2–3 days",
        "returnPolicy": "30-day free returns",
        "shippingCost": 0.0,
        "inStock": true,
        "checkoutUrl": "...",
        "bestValue": true,
        "whyRecommended": "Official Nike India store — best price + free shipping."
      }
    ]
  }
]
```

---

### `GET /conversations/{sessionId}`

Retrieve a full session including all messages, latest intent, and recommendations.

**Response:** `SessionResponse` — same shape as the message response but includes the full `messages` array and `createdAt` timestamps.

---

## Key backend services

### `ConversationService`

Orchestrates the per-message pipeline: saves messages, calls the AI, saves intent versions, triggers product ranking when ready, and builds the response DTO.

### `GroqAiService` / `GeminiAiService`

Both implement `AiService` with three methods:

| Method | What it does |
|---|---|
| `extractIntent(history)` | Sends full conversation to AI, returns structured `ExtractedIntent`. Also caches the question decision in a `ThreadLocal` to avoid a second API call. |
| `decideNextQuestion(intent, count)` | Returns the cached question decision from `extractIntent` — zero extra API calls. |
| `rankProducts(products, intent)` | Runs `MockAiService.rankProducts()` for numeric scores, then calls AI to rewrite `reasoning`, `tradeoffs`, and `notSuitableFor` with personalized text. |

Both services retry up to 3 times with exponential backoff (2s, 4s) on `429 Too Many Requests` before falling back to mock.

### `MockAiService`

Deterministic fallback. No network calls. Used when:
- No API key is configured
- Provider is explicitly set to `mock`
- A live AI call fails after retries

### `MockProductService`

Loads 51 Nike products from `nike_products.csv` at startup. Each product has: title, brand, price, product type, tags, and attributes (cushioning, terrain, weight, material). Used when `SHOPIFY_STOREFRONT_TOKEN=mock` (the default).

### `ShopifyProductService`

Queries the Shopify Storefront GraphQL API for live products. Activated when a real `SHOPIFY_STOREFRONT_TOKEN` is provided.

---

## Database

Dev uses **H2 in-memory** — no setup needed. Data resets on every restart.

**Entities:**

| Table | Description |
|---|---|
| `conversation_sessions` | One row per chat session. Tracks question count, confidence score, and whether recommendations have been generated. |
| `conversation_messages` | All messages (role: USER or ASSISTANT) for a session, in order. |
| `user_intents` | Versioned intent snapshots — one row per message turn. |
| `recommendations` | Saved product recommendations for a session. |
| `regret_flags` | Warnings attached to recommendations (type, severity, description). |
| `merchant_offers` | Retailer options attached to a recommendation (price, delivery, returns). |
| `product_snapshots` | Optional product data cache. |

**To switch to PostgreSQL** for production, add to `.env`:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/shopmind
SPRING_DATASOURCE_USERNAME=shopmind
SPRING_DATASOURCE_PASSWORD=yourpassword
```

And update `application.properties`:
```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

---

## Product catalog

The default catalog is `backend/src/main/resources/nike_products.csv` — 51 products loaded at startup by `MockProductService`.

CSV columns: `id`, `title`, `brand`, `productType`, `minPrice`, `tags`, `images`, `attributes`

**Attributes** is a JSON object embedded in the CSV. Recognized keys:

| Key | Example values | Used for |
|---|---|---|
| `cushioning` | `low`, `moderate`, `high`, `ultra-high`, `maximum` | Comfort scoring |
| `terrain` | `urban`, `road`, `trail`, `off-road` | Terrain matching |
| `weight` | `280g` | Long-wear regret flag |
| `material` | `leather`, `mesh`, `knit` | Durability scoring |

To replace the catalog with live Shopify data, set `SHOPIFY_STOREFRONT_TOKEN` to a real token starting with `shpat_`.

---

## Running in production

1. Build the frontend:
   ```bash
   cd frontend && npm run build
   # Output in frontend/dist/
   ```

2. Serve `dist/` from any static host (Vercel, Netlify, S3+CloudFront, Nginx).

3. Set `VITE_API_URL` to your deployed backend URL before building:
   ```
   VITE_API_URL=https://api.yourdomain.com/api/v1
   ```

4. Build and run the backend JAR:
   ```bash
   cd backend
   mvn package -DskipTests
   java -jar target/shopmind-backend-1.0.0.jar \
     --shopmind.ai.provider=groq \
     --shopmind.ai.groq-api-key=gsk_...
   ```

5. Set `CORS_ORIGINS` to your frontend URL:
   ```
   CORS_ORIGINS=https://yourdomain.com
   ```

---

## Troubleshooting

### Backend starts but chat still uses mock responses

**Symptom:** `processingTimeMs` in the API response is under 50ms.

**Cause:** The env vars weren't loaded, so the backend defaulted to `mock` mode.

**Fix:** The `spring-dotenv` dependency in `pom.xml` loads `backend/.env` automatically. If you added the dependency recently, run `mvn spring-boot:run` fresh (not from a cached build). Verify the provider is set:
```bash
curl -s -X POST http://localhost:8080/api/v1/conversations/test-session-id/messages \
  -H "Content-Type: application/json" \
  -d '{"content":"test"}' | grep processingTimeMs
# > 200ms means Groq is active
```

### `429 Too Many Requests` from Groq

The service retries automatically (2s, 4s backoff) and falls back to mock if still rate-limited. On the free tier:
- Groq allows 30 RPM — enough for ~15 messages/min
- If you need more, upgrade to a paid Groq plan or switch to `llama3-8b-8192` which has higher free limits

### Frontend shows "Start chatting" but never gets a response

**Cause:** Backend is unreachable or CORS is blocking the request.

**Fix:**
1. Check the backend is running: `curl http://localhost:8080/api/v1/conversations -X POST`
2. Check `CORS_ORIGINS` in `backend/.env` includes `http://localhost:3000`
3. Check `VITE_API_URL` in `frontend/.env` points to the correct backend address

### H2 console access

Enable in `.env`:
```
H2_CONSOLE_ENABLED=true
```
Then visit `http://localhost:8080/h2-console` — JDBC URL: `jdbc:h2:mem:shopmind`, user: `sa`, no password.

### Port 8080 already in use

```powershell
# Find the process
netstat -ano | findstr :8080
# Kill it (replace PID)
Stop-Process -Id <PID> -Force
```
