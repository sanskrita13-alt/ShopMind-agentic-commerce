# ShopMind Technical Document

## 1. Overview

ShopMind is an AI-powered footwear recommendation platform built to reduce purchase regret through conversational commerce and explainable recommendations.

Instead of overwhelming users with filters and product grids, ShopMind guides users through a conversation to understand their priorities and recommend the most suitable footwear.

---

## 2. System Architecture

High-level architecture:

Frontend (React + TypeScript)
↓
Spring Boot Backend
↓
PostgreSQL Database
↓
AI Layer (Groq / Gemini)

### Frontend
Responsible for:
- Conversational chat interface
- Recommendation display
- User interaction flow
- Session management

### Backend
Responsible for:
- Recommendation logic
- Session management
- Product scoring
- API handling
- AI orchestration

### Database
Stores:
- User sessions
- Conversations
- Recommendations
- Product data

---

## 3. Tech Stack

### Frontend
- React
- TypeScript
- CSS

### Backend
- Spring Boot
- Java
- Maven

### Database
- PostgreSQL

### AI Layer
- Groq (Primary)
- Gemini (Fallback)

---

## 4. Recommendation Flow

1. User starts conversation.
2. System collects preferences.
3. Follow-up questions refine intent.
4. Products are matched against:
   - Comfort
   - Budget
   - Usage
   - Style
   - Durability
5. Fit Score is generated.
6. Explainable recommendations are shown.

---

## 5. Product Catalog Design

Due to Shopify API limitations under the free plan, real-time store integration was not implemented.

Instead, a CSV ingestion system was used containing footwear listings across brands.

This CSV format follows Shopify's import schema, enabling future migration into real Shopify stores.

---

## 6. Key Design Decisions

- Migrated backend from Express.js to Spring Boot for scalability and security.
- Shifted from website-first to app-first experience.
- Started with shoes-only MVP for focused recommendation quality.
- Adopted conversational discovery instead of traditional filters.
- Replaced confidence percentage with Fit Score for clearer decision support.

---

## 7. Current Limitations

- Shoes-only implementation
- Static product catalog
- No real-time inventory sync
- No payment integration

---

## 8. Future Scope

- Multiple categories beyond footwear
- Live Shopify integration
- Personalized recommendation memory
- Voice commerce support
- Cross-brand comparisons