# ShopMind Frontend

React 19 + Vite SPA for the ShopMind AI shoe-recommendation chatbot.

## Stack

- **Vite** 7 (build + dev server)
- **React** 19
- **TypeScript** 5
- **React Router** 7 (client-side routing)
- **Tailwind CSS** 4 (via `@tailwindcss/vite` plugin)
- **Zustand** 5 (state)
- **Framer Motion** 12 (animations)
- **Axios** 1 (HTTP client)
- **Lucide React** (icons)

## Getting started

```bash
npm install
npm run dev
```

The dev server runs on **http://localhost:3000**. It expects the backend at `http://localhost:8080/api/v1` (configurable via `VITE_API_URL`).

## Environment

Copy `.env.example` to `.env.local` and adjust if needed:

```env
VITE_API_URL=http://localhost:8080/api/v1
```

⚠️ All Vite env vars must be prefixed with `VITE_` — anything else is invisible to the browser bundle. **Never** put API keys here; they ship to every visitor.

## Scripts

| Script | Purpose |
|---|---|
| `npm run dev` | Vite dev server with HMR on port 3000 |
| `npm run build` | Type-check (`tsc -b`) + production build to `dist/` |
| `npm run preview` | Serve the production `dist/` build locally |
| `npm run lint` | Run ESLint over the project |

## Project layout

```
frontend/
├── index.html              ← Vite entry HTML
├── vite.config.ts          ← Vite + Tailwind + alias config
├── src/
│   ├── main.tsx            ← React root + BrowserRouter
│   ├── App.tsx             ← Route table
│   ├── index.css           ← Tailwind import + design tokens
│   ├── vite-env.d.ts       ← import.meta.env typing
│   ├── pages/
│   │   ├── Landing.tsx     ← /
│   │   └── Chat.tsx        ← /chat (+ ProductDeepDive modal)
│   ├── store/
│   │   └── chat.ts         ← Zustand store: session, messages, intent, recs
│   └── lib/
│       ├── api.ts          ← Axios wrappers for the backend
│       └── types.ts        ← TS types mirroring backend DTOs
└── public/                 ← Static assets served at /
```

## Routing

- `/`     → `pages/Landing.tsx`
- `/chat` → `pages/Chat.tsx`

Add a route in `src/App.tsx` to extend.

## Working with the backend

The frontend doesn't authenticate, doesn't hold API keys, and doesn't talk to Shopify or Gemini directly. Every call goes through the Spring Boot backend at `:8080`:

| Frontend function | Backend endpoint |
|---|---|
| `createSession()` in `lib/api.ts` | `POST /api/v1/conversations` |
| `sendMessage(sessionId, content)` | `POST /api/v1/conversations/{id}/messages` |
| `getSession(sessionId)` | `GET /api/v1/conversations/{id}` |

See [../backend/README.md](../backend/README.md) for backend setup, AI providers (mock/Gemini/Claude), and Shopify live-product configuration.

## Production build

```bash
npm run build
npm run preview
```

The build emits to `dist/`. Serve it with any static host (Nginx, Cloudflare Pages, S3, Netlify, Vercel, etc.) — no Node runtime required, since it's a pure SPA.
