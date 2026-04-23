<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

# BidNow Frontend AI Agent Instructions

Welcome to the frontend of the BidNow project! As an AI agent working in this directory, adhere strictly to the following architectural guidelines and design constraints.

## 🛠️ Tech Stack & Tooling
- **Framework:** Next.js 15 (App Router).
- **Styling:** Tailwind CSS v4.
- **Components:** shadcn/ui. **DO NOT** hand-write primitive components if a shadcn equivalent exists (e.g., Button, Input, Dialog, etc.). Use the CLI `npx shadcn@latest add <component>` to add them. They reside in `components/ui/`.
- **Icons:** Lucide React (`lucide-react`). Do not use other icon libraries.
- **State Management:** Zustand or React Context.
- **Real-time:** Socket.io-client or Native WebSockets for live auction updates.

## 🎨 Design System & Styling Rules
- **Color System:** We use a dual-layer token system. Layer 1 is shadcn semantic variables, Layer 2 is BidNow extended tokens (auction states, wallet, brand palette). All are defined in `app/globals.css`. Do not hardcode raw hex colors in components; always use the CSS variables (e.g., `bg-[--color-auction-active-bg]`).
- **Typography:** 
  - *Geist Sans* for UI labels and body.
  - *Geist Mono* for ALL prices, bid amounts, wallet balances, and countdown numbers to prevent width-jump on digit change.
  - *DM Sans* for display headings.
- **Spacing & Layout:** Base unit is 4px. Follow the established Tailwind spacing scale and z-index scale defined in the design system.

## 📁 Project Structure Guidelines
- `app/`: Next.js App Router layout, pages, and `globals.css`.
- `components/ui/`: shadcn-generated primitives.
- `components/auction/`, `components/wallet/`, `components/notification/`, `components/shared/`: Domain-specific custom components.
- `hooks/`: Custom React hooks (e.g., `useCountdown`, `useAuctionSocket`).
- `lib/`: Utilities (`utils.ts`, `format.ts`, etc.).
- `types/`: TypeScript interfaces. **Rule:** Always write robust TypeScript interfaces/types for API payloads and component props.

## 🧠 Directives for Agents
1. **Respect the Design System:** Ensure you have read `../docs/design-system.md` for specific animations, border-radius rules, z-indexes, and component patterns before implementing any UI features.
2. **Real-time UX:** Always consider WebSocket updates. Prices and countdowns must animate smoothly (using `price-roll`, `bid-pulse`, or `countdown-tick` animations) without full page reloads.
3. **shadcn/ui Composition:** Compose complex elements out of shadcn primitives. For example, `AuctionCard` should wrap the shadcn `<Card>`, and `BidInput` should extend shadcn `<Input>`.
4. **TypeScript Strictness:** Strict mode is enforced. Do not use `any`. Provide exact typings for all component props.

*To run the frontend, use `npm run dev` (or equivalent package manager command).*
