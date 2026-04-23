# BidNow Frontend

This is the frontend application for the BidNow platform, a modern, high-performance E-commerce platform specialized in real-time auctions.

## 🛠️ Tech Stack

- **Framework:** Next.js 16.2 (App Router)
- **Language:** TypeScript
- **Styling:** Tailwind CSS v4
- **UI Components:** Base UI (`@base-ui/react`) & shadcn/ui
- **Icons:** Lucide React
- **State Management:** Zustand 5
- **Real-time:** Socket.io-client

## 🚀 Getting Started

First, install the dependencies if you haven't already:

```bash
npm install
```

Then, run the development server:

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

## 📁 Project Structure

- `app/`: Next.js App Router layout, pages, and `globals.css`.
- `components/ui/`: Base UI and shadcn-generated primitives.
- `components/auction/`, `components/wallet/`, `components/shared/`: Domain-specific custom components.
- `hooks/`: Custom React hooks (e.g., `useCountdown`, `useAuctionSocket`).
- `lib/`: Utilities (`utils.ts`, `format.ts`, etc.).
- `mock-data/`: Centralized mock data for development.
- `types/`: TypeScript interfaces and types.

## 🤖 AI Agents

If you are an AI coding agent working in this repository, please read the `AGENTS.md` file for specific rules, design system details, and architectural guidelines.
