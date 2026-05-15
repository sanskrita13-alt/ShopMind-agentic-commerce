# ShopMind — AI-Powered Shoe Recommendation Engine

A lightweight, conversational AI platform that helps shoppers find the perfect shoes through natural dialogue. Built with **Spring Boot 3.4.5** (Java 21) backend and **Vite + React 19** frontend.

**Status:** MVP with Shopify live product integration + multi-provider AI (mock / Gemini / Claude)
**Hosted:** Local dev (can run on 1GB RAM VPS)

---

## 🎯 What It Does

1. **Conversational Intent Extraction** — Asks targeted questions to understand shopper needs:
   - Primary use case (running, walking, casual, gym, college, etc.)
   - Hours on feet per day
   - Budget range
   - Comfort/style/durability priorities
   - Terrain type (urban, trail, mixed)
   - Fit preference (wide, narrow, standard)
   - Versatility needs

2. **Smart Product Ranking** — Scores shoes against the shopper's intent using:
   - Attribute matching (cushioning, weight, support, terrain)
   - Budget fit analysis
   - Durability + style scoring
   - Regret flag detection (e.g., "too heavy for 6+ hour walks")

3. **Merchant Integration** — Shows live Shopify prices, availability, and variants

4. **Reasoning Explanations** — Explains *why* each shoe matches their needs (powered by Claude AI)

---

## 🏗️ Architecture

### Backend (Spring Boot)
```
src/main/java/com/shopmind/
├── controller/           # REST endpoints
│   └── ConversationController.java
├── service/
│   ├── ai/               # Intent extraction + ranking (provider-switchable)
│   │   ├── AiService.java (interface)
│   │   ├── MockAiService.java (deterministic keyword scoring)
│   │   ├── GeminiAiService.java (Google Gemini REST)
│   │   └── ClaudeAiService.java (Anthropic SDK)
│   ├── product/          # Product catalog
│   │   ├── MockProductService.java
│   │   └── ShopifyProductService.java (Storefront API)
│   └── conversation/     # Orchestration
│       └── ConversationService.java
├── entity/               # JPA entities (H2 + PostgreSQL)
│   ├── ConversationSession
│   ├── ConversationMessage
│   ├── UserIntent
│   ├── Recommendation
│   ├── RegretFlag
│   ├── MerchantOffer
│   └── ProductSnapshot
├── repository/           # Data access
│   ├── ConversationSessionRepository
│   ├── ConversationMessageRepository
│   ├── UserIntentRepository
│   ├── RecommendationRepository
│   └── ProductSnapshotRepository
├── dto/                  # DTOs for API responses
│   └── AiDtos.java
├── config/               # Spring configuration
│   ├── CorsConfig.java
│   ├── WebClientConfig.java (Shopify API client)
│   ├── AiServiceConfig.java (AI bean selection)
│   └── RedisConfig.java (placeholder for caching)
└── exception/            # Error handling
    └── GlobalExceptionHandler.java
```

### Frontend (Vite + React)
```
frontend/
├── index.html            # Vite entry HTML
├── vite.config.ts        # Vite + Tailwind plugin config
├── src/
│   ├── main.tsx          # React root + BrowserRouter
│   ├── App.tsx           # Route table (React Router)
│   ├── index.css         # Tailwind + design tokens
│   ├── pages/
│   │   ├── Landing.tsx   # /
│   │   └── Chat.tsx      # /chat
│   ├── store/
│   │   └── chat.ts       # Zustand state
│   └── lib/
│       ├── api.ts        # Axios API client
│       └── types.ts      # TypeScript types
└── public/               # Static assets served at /
```

### Database Schema (JPA + H2/PostgreSQL)
- **conversation_sessions** — Tracks active/completed conversations
- **conversation_messages** — Full chat history with metadata
- **user_intents** — Versioned intent state (evolves per turn)
- **recommendations** — Ranked shoe matches with scores
- **regret_flags** — Warnings (e.g., "too heavy", "wrong terrain")
- **merchant_offers** — Pricing from Shopify variants
- **product_snapshots** — Cached Shopify products (30-min TTL)

---

## 🚀 Quick Start

### Prerequisites
- **Java 21** (or use `./mvnw` wrapper)
- **Node.js 20+** + npm
- **PostgreSQL 14+** (optional, H2 used by default for dev)
- **Shopify Storefront API token** (optional, falls back to mock catalog)
- **Google Gemini API key** _or_ **Anthropic Claude API key** (optional, falls back to mock AI)

### Backend Setup

```bash
cd backend

# Build
mvn clean package

# Run locally (H2 in-memory database, mock AI, mock products)
mvn spring-boot:run

# Or with environment variables for real Shopify + real AI
export SHOPMIND_AI_PROVIDER=gemini      # or "claude"
export GOOGLE_API_KEY=AIza...           # if provider=gemini
export ANTHROPIC_API_KEY=sk-ant-...     # if provider=claude
export SHOPIFY_STORE_DOMAIN=your-store.myshopify.com
export SHOPIFY_STOREFRONT_TOKEN=shpat_...

mvn spring-boot:run
```

**Backend runs on:** `http://localhost:8080`

**API Base:** `http://localhost:8080/api/v1`

### Frontend Setup

```bash
cd frontend

# Install
npm install

# Development server (hot reload)
npm run dev

# Or build for production (outputs to dist/)
npm run build
npm run preview
```

**Frontend runs on:** `http://localhost:3000`

---

## 📡 API Endpoints

### Create a Conversation Session
```http
POST /api/v1/conversations
Content-Type: application/json

Response:
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Send a Message
```http
POST /api/v1/conversations/{sessionId}/messages
Content-Type: application/json

Request:
{
  "content": "I need shoes for walking around campus, about 6 hours a day"
}

Response:
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "assistantMessage": {
    "role": "ASSISTANT",
    "content": "Got it! 6 hours on campus... What's your budget range for shoes?",
    "reasoningStatus": "CONFIDENCE_GROWING"
  },
  "currentIntent": {
    "primaryUseCase": "college daily wear",
    "walkingDuration": "6 hours",
    "budget": null,
    "comfortPriority": 0.7,
    "stylePriority": 0.5,
    "durabilityPriority": 0.6,
    "terrainType": "urban",
    "preferredFit": null,
    "needsVersatility": true,
    "confidenceScore": 0.5,
    "missingAttributes": ["budget", "preferredFit"],
    "contradictions": []
  },
  "recommendations": null,
  "debugInfo": {
    "processingTimeMs": 45,
    "rawIntentJson": "{...}"
  }
}
```

### Get Full Session
```http
GET /api/v1/conversations/{sessionId}

Response:
{
  "sessionId": "...",
  "status": "ACTIVE",
  "confidenceScore": 0.8,
  "questionCount": 3,
  "recommendationsGenerated": true,
  "messages": [...],
  "currentIntent": {...},
  "recommendations": [
    {
      "productId": "gid://shopify/Product/123",
      "productName": "Nike Air Zoom Pegasus 41",
      "productBrand": "Nike",
      "productImageUrl": "https://...",
      "price": 130.0,
      "currency": "USD",
      "matchScore": 0.87,
      "comfortScore": 85,
      "durabilityScore": 82,
      "styleScore": 78,
      "reasoning": "Excellent cushioning for 6-hour days with campus style. Trusted brand.",
      "tradeoffs": "Slightly heavier than minimalist shoes | Not ideal for trails",
      "notSuitableFor": "Ultralight runners, trail hikers",
      "regretFlags": [
        {
          "type": "WEIGHT",
          "title": "Moderate weight",
          "description": "Slightly heavy for 6+ hour wear, but manageable with cushioning",
          "severity": "LOW"
        }
      ],
      "merchants": [
        {
          "name": "Shopify Direct",
          "price": 130.0,
          "currency": "USD",
          "deliveryEstimate": "2-3 business days",
          "returnPolicy": "30-day returns",
          "shippingCost": 0.0,
          "inStock": true,
          "checkoutUrl": "https://...",
          "bestValue": true,
          "whyRecommended": "Official store, best price + free shipping"
        }
      ],
      "rank": 1
    },
    {...},
    {...}
  ],
  "createdAt": "2026-05-15T10:30:00Z"
}
```

---

## ⚙️ Configuration

### `application.properties` (Backend)

```properties
# Server
server.port=8080

# Database (H2 dev, PostgreSQL prod)
spring.datasource.url=jdbc:h2:mem:shopmind
spring.datasource.driverClassName=org.h2.Driver
spring.h2.console.enabled=true
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update

# CORS
shopmind.cors.allowed-origins=http://localhost:3000

# Product Caching
shopmind.cache.product-ttl=1800
shopmind.cache.session-ttl=86400
shopmind.cache.recommendation-ttl=3600

# AI Mode (use mock for development, real for production)
shopmind.ai.mock-mode=true

# Shopify (optional)
shopify.store-domain=${SHOPIFY_STORE_DOMAIN:demo-shoes.myshopify.com}
shopify.storefront-token=${SHOPIFY_STOREFRONT_TOKEN:mock}
shopify.api-version=2026-04

# Anthropic Claude (optional, requires API key)
shopmind.ai.anthropic-api-key=${ANTHROPIC_API_KEY:}
shopmind.ai.claude-model=${CLAUDE_MODEL:claude-haiku-4-5-20251001}
```

### `.env.local` (Frontend)

```env
VITE_API_URL=http://localhost:8080/api/v1
```

---

## 🤖 AI Modes

### Mock Mode (Default)
- **Keyword-based intent extraction** — parses user messages for tags
- **Deterministic scoring** — consistent results (no randomness)
- **Hardcoded products** — 10 test shoes with fake prices
- **No API calls** — instant responses, zero cost
- **Perfect for:** Local development, testing, CI/CD

**Enable:** `shopmind.ai.mock-mode=true` (default)

### Real AI Mode
- **Anthropic Claude** — natural language understanding
- **Shopify Storefront API** — live product catalog + real prices
- **Hybrid scoring** — Claude enriches reasoning; mock handles numeric consistency
- **Graceful fallback** — any API error falls back to mock
- **Configurable:** Haiku (cheap), Sonnet (smarter), Opus (slowest/best)

**Enable:** 
```bash
export shopmind.ai.mock-mode=false
export ANTHROPIC_API_KEY=sk-ant-...
export SHOPIFY_STORE_DOMAIN=your-store.myshopify.com
export SHOPIFY_STOREFRONT_TOKEN=your-token
mvn spring-boot:run
```

---

## 📊 Current Product Catalog (Mock)

10 hardcoded shoes for development testing:

| Brand | Model | Type | Price | Cushioning |
|---|---|---|---|---|
| Nike | Air Zoom Pegasus 41 | Running | $130 | High |
| Adidas | Ultraboost Light | Running/Lifestyle | $190 | Ultra-high |
| New Balance | Fresh Foam X 1080v14 | Running | $160 | Maximum |
| Brooks | Ghost 16 | Running | $140 | High |
| ASICS | Gel-Nimbus 26 | Running | $160 | Ultra-high |
| Nike | Air Force 1 '07 | Casual/Sneaker | $110 | Moderate |
| Puma | RS-X Reinvention | Casual/Sneaker | $85 | Moderate |
| Skechers | Go Walk 7 | Walking | $75 | High |
| Nike | React Infinity Run 4 | Running | $160 | High |
| Allbirds | Tree Runners | Casual/Lifestyle | $98 | Moderate |

---

## 🔄 Conversation Flow

```
User: "I need shoes for walking around campus..."
  ↓
[1] Extract Intent → primaryUseCase=college, walkingDuration=?, budget=?, ...
[2] Check Confidence → missing attributes → pick next question
[3] Repeat until: confidence ≥ 0.65 OR attributes filled
  ↓
User: (5+ messages with sufficient context)
  ↓
[4] Rank Products → score all shoes against intent
[5] Generate Reasoning → why each shoe matches (Claude or template)
[6] Fetch Merchant Offers → real Shopify prices + availability
[7] Return Top 3 Recommendations
  ↓
Response: "Here are 3 shoes perfect for your 6-hour campus walks..."
```

---

## 🎨 Frontend Features

### Landing Page (`/`)
- Hero section with feature highlights
- Call-to-action button linking to `/chat`
- Marketing copy explaining the service

### Chat Page (`/chat`)
- **Left Panel:** Conversation transcript with message history
- **Right Panel:** 
  - Live intent model display (extracted attributes + confidence score)
  - Recommendation cards (when ready) with match scores, pricing, merchants
  - "Product Deep Dive" modal for detailed shoe info
- **Debug Toggle:** View raw intent JSON + processing time
- **State Management:** Zustand (lightweight, performant)

### UI Libraries
- **Tailwind CSS 4** — utility-first styling
- **Framer Motion** — smooth animations
- **Lucide Icons** — minimal icon set
- **Axios** — HTTP client

---

## 🧪 Testing

### Backend Unit Tests
```bash
cd backend
mvn test

# Specific test
mvn test -Dtest=MockAiServiceTest
```

### Manual Testing

**1. Test Mock Mode (default)**
```bash
# Terminal 1
cd backend && mvn spring-boot:run

# Terminal 2
curl -X POST http://localhost:8080/api/v1/conversations \
  -H "Content-Type: application/json" \
  -d '{}' | jq .sessionId

# Terminal 3
curl -X POST http://localhost:8080/api/v1/conversations/{SESSION_ID}/messages \
  -H "Content-Type: application/json" \
  -d '{"content": "I need shoes for running"}'
```

**2. Test Real Shopify + Claude**
```bash
# With env vars set
export ANTHROPIC_API_KEY=sk-ant-...
export SHOPIFY_STOREFRONT_TOKEN=shpat_...

mvn spring-boot:run
# Then repeat curl tests above
```

**3. Frontend Testing**
```bash
cd frontend && npm run dev
# Navigate to http://localhost:3000
# Test full conversation flow, verify recommendations display
```

---

## 📦 Dependencies

### Backend (pom.xml)
| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-data-jpa` | Database ORM |
| `spring-boot-starter-validation` | Bean validation |
| `spring-boot-starter-webflux` | WebClient for async HTTP |
| `h2` | In-memory dev database |
| `postgresql` | Production database |
| `lombok` | Boilerplate reduction (@Data, @Builder) |
| `mapstruct` | Type-safe bean mapping |
| `jackson-databind` | JSON serialization |
| `com.anthropic:sdk` (planned) | Claude API client |

### Frontend (package.json)
| Package | Purpose |
|---|---|
| `vite` | Build tool + dev server |
| `@vitejs/plugin-react` | React Fast Refresh + JSX transform |
| `react-router-dom` | Client-side routing |
| `@tailwindcss/vite` | Tailwind v4 Vite plugin |
| `react` | UI library |
| `tailwindcss` | CSS framework |
| `zustand` | State management |
| `axios` | HTTP client |
| `framer-motion` | Animations |
| `lucide-react` | Icons |

---

## 🚢 Deployment

### Local Development
```bash
# Terminal 1: Backend (port 8080)
cd backend && mvn spring-boot:run

# Terminal 2: Frontend (port 3000)
cd frontend && npm run dev
```

### Docker (Recommended for Staging/Prod)

**Backend Dockerfile** (add to `backend/`):
```dockerfile
FROM openjdk:21-jdk-slim
COPY target/shopmind-backend-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Frontend Dockerfile** (add to `frontend/`):
```dockerfile
FROM node:20-alpine as builder
COPY . .
RUN npm install && npm run build

FROM node:20-alpine
COPY --from=builder dist ./dist
ENTRYPOINT ["npx", "vite", "preview", "--host", "0.0.0.0", "--port", "3000"]
```

**docker-compose.yml** (add to root):
```yaml
version: '3.8'
services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/shopmind
      SHOPIFY_STORE_DOMAIN: ${SHOPIFY_STORE_DOMAIN}
      SHOPIFY_STOREFRONT_TOKEN: ${SHOPIFY_STOREFRONT_TOKEN}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}

  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    environment:
      VITE_API_URL: http://localhost:8080/api/v1

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: shopmind
      POSTGRES_PASSWORD: shopmind-dev
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

```bash
# Build and run
docker-compose up --build
```

### VPS/Cloud Deployment (AWS EC2, DigitalOcean, etc.)
1. **Minimal requirements:** 1GB RAM, 1 vCPU
2. **Use PostgreSQL** (not H2) for persistence
3. **Set environment variables** for Shopify + Anthropic
4. **Configure CORS** to match your domain
5. **Use SSL/TLS** (Let's Encrypt)
6. **Monitor** Claude API costs (Haiku is cheap)

---

## 🔐 Security

### Current Safeguards
- ✅ CORS configured for specific origins
- ✅ Input validation on all API requests (`@NotBlank`, `@Valid`)
- ✅ Exception handling prevents stack trace leaks
- ✅ No SQL injection (JPA parameterized queries)
- ✅ No hardcoded secrets (uses `${ENV_VAR:fallback}`)

### TODO Before Production
- [ ] Add JWT authentication (or OAuth 2.0)
- [ ] Rate limiting on conversation endpoints
- [ ] SQL injection prevention audit
- [ ] XSS protection in frontend (add a CSP meta tag in `index.html`)
- [ ] Anthropic API key rotation strategy
- [ ] Shopify token vault (AWS Secrets Manager, HashiCorp Vault)
- [ ] HTTPS only
- [ ] Add request logging + audit trail

---

## 📈 Performance

### Resource Usage (Observed)
| Metric | Value |
|---|---|
| Backend Startup | 5-10 seconds |
| Backend Memory | 200-300 MB base + conversation heap |
| Frontend Bundle | ~500 KB gzipped |
| API Response Time | 50-200 ms (mock), 200-500 ms (Claude) |
| Shopify API Latency | 200-400 ms |
| Database Size | ~10 MB per 10k conversations |

### Optimization Tips
- **Enable caching** → Uncomment Redis in `pom.xml`, wire in `config/RedisConfig.java`
- **Batch Shopify requests** → Fetch all products once per 30 min
- **Use connection pooling** → HikariCP (default in Spring Boot)
- **Frontend code splitting** → Vite splits dynamic `import()` boundaries automatically; add route-level lazy imports for further gains
- **CDN for images** → Serve product images via Cloudflare/CloudFront

---

## 🐛 Troubleshooting

### Backend Won't Start
```bash
# Check Java version
java -version  # Should be 21+

# Clear Maven cache
mvn clean

# Run with debug output
mvn spring-boot:run -X
```

### Frontend Can't Reach Backend
```bash
# Check CORS config in application.properties
# Verify VITE_API_URL in .env.local

# Test API directly
curl http://localhost:8080/api/v1/conversations
```

### Shopify API Errors
```bash
# Verify credentials
echo $SHOPIFY_STORE_DOMAIN
echo $SHOPIFY_STOREFRONT_TOKEN

# Check GraphQL syntax at Shopify Admin > Apps > GraphQL
# Verify token has "storefront" scope (not admin)
```

### Claude Not Working
```bash
# Verify API key
echo $ANTHROPIC_API_KEY | head -c 20

# Check logs for "falling back to mock"
# Claude errors are logged but don't crash the app
```

---

## 🗓️ Roadmap

### Phase 1 (Current MVP)
- ✅ Conversational intent extraction (keyword matching)
- ✅ Shoe ranking against intent
- ✅ Regret flag detection
- ✅ Mock product catalog
- ✅ REST API + React UI

### Phase 2 (Planned - See `PLAN.md`)
- 🔄 **Shopify Live Integration** — Real product prices + inventory
- 🔄 **Anthropic Claude AI** — Natural language understanding + reasoning
- 🔄 **ProductSnapshot Caching** — 30-min TTL for Shopify products
- 🔄 **Scoring Fixes** — Use all extracted intent attributes (stylePriority, durabilityPriority, etc.)

### Phase 3 (Future)
- [ ] User accounts + saved preferences
- [ ] Order history + feedback loop
- [ ] Mobile app (React Native)
- [ ] Multi-language support
- [ ] Competitor price comparison (Amazon, Foot Locker, etc.)
- [ ] Size recommendation engine
- [ ] Sustainability metrics
- [ ] Recommendation analytics + A/B testing

---

## 📚 Documentation

- **[PLAN.md](PLAN.md)** — Detailed implementation roadmap (5 work streams, 10 files)
- **[API.md](docs/API.md)** — Full OpenAPI/Swagger specification (optional)
- **Architecture Diagram** — See `.claude/diagrams/` (optional)

---

## 🤝 Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make changes (both backend + frontend)
4. Run tests: `mvn test` (backend), `npm test` (frontend)
5. Commit with clear messages
6. Push and open a PR

---

## 📄 License

MIT License — See LICENSE.md

---

## 📞 Support

- **Issues:** GitHub Issues
- **Email:** [your-email@example.com]
- **Docs:** See README.md (this file) + PLAN.md + code comments

---

## 🎓 Learn More

### Spring Boot
- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Building REST APIs](https://spring.io/guides/gs/rest-service/)

### Vite + React Router
- [Vite Docs](https://vite.dev/)
- [React Router Docs](https://reactrouter.com/)
- [React 19](https://react.dev)
- [Tailwind CSS](https://tailwindcss.com)

### Shopify
- [Storefront GraphQL API](https://shopify.dev/docs/api/storefront-graphql)
- [API Authentication](https://shopify.dev/docs/api/storefront-graphql/2026-04)

### Anthropic Claude
- [Claude API Docs](https://docs.anthropic.com)
- [Java SDK](https://github.com/anthropics/anthropic-sdk-java)

---

**Built with ❤️ by ShopMind Team**  
Last updated: 2026-05-15
