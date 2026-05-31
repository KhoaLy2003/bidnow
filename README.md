# BidNow

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-16-black.svg)](https://nextjs.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**BidNow** is a modern, high-performance E-commerce platform specialized in real-time auctions. Sellers can post items, and buyers compete through a dynamic bidding process featuring auto-bidding and anti-sniping mechanisms.

## 👥 The Team

We are a core team of 2 passionate engineers:
- **Leader:** Project Architect & Backend Lead
- **Developer:** Full-stack Developer

---

## ✨ Key Features

- **English Auction Model:** Ascending price bidding where the highest bidder wins.
- **Buy It Now:** Option for immediate purchase at a fixed price.
- **Anti-Sniping (Bid Extension):** Automatically extends auction time for last-minute bids to ensure fairness.
- **Auto-Bidding Engine:** Users can set maximum bids, and the system will bid on their behalf.
- **Secure Wallet & Deposit:** Mandatory deposit for bidding with automated refunds for losing bidders.
- **Real-time Notifications:** Instant feedback via WebSockets for outbid alerts and auction updates.

---

## 🚀 Tech Stack

### Backend (Microservices)
- **Core:** Java 17, Spring Boot 3.x
- **Infrastructure:** Spring Cloud (Gateway, Config, Discovery)
- **Services:** Identity, User, Auction, Bidding, Wallet, Media, Notification
- **Database:** PostgreSQL (independent database per service)
- **Real-time:** WebSockets & Redis
- **Messaging:** Kafka (event-driven architecture)
- **Storage:** Cloudinary (media management)

### Frontend
- **Framework:** Next.js 16 (TypeScript)
- **Styling:** Tailwind CSS
- **Real-time:** WebSocket client

---

## 📁 Project Structure

```
bidnow/
├── backend/            # Java microservices (Spring Boot)
│   ├── api-gateway/
│   ├── discovery-service/
│   ├── identity-service/
│   ├── user-service/
│   ├── auction-service/
│   ├── bidding-service/
│   ├── wallet-service/
│   ├── media-service/
│   ├── common/
│   └── docker-compose.yml
├── frontend/           # Next.js frontend
├── docker/             # Docker infrastructure configs
├── docs/               # Technical documentation & diagrams
├── common-resources/   # Shared templates and assets (git submodule)
├── .github/            # CI workflows & PR templates
└── README.md
```

---

## 📖 Documentation

Detailed documentation can be found in the [docs/](./docs/) directory:

### Core Specs
- [Business Clarifications](./docs/business-clarifications.md)
- [Functional Requirements](./docs/functional.md)
- [Non-Functional Requirements](./docs/non-functional.md)
- [Architecture Overview](./docs/architecture.md)
- [Database Overview](./docs/database.md)

### Workflows & Diagrams
- [Git Workflow](./docs/git-workflow.md)
- [User Auth Flow](./docs/diagrams/01-user-auth-flow.svg)
- [Auction Creation Flow](./docs/diagrams/02-auction-creation-flow.svg)
- [Bidding & Anti-Sniping Flow](./docs/diagrams/03-bidding-antisniping-flow.svg)
- [Auction Closure Flow](./docs/diagrams/04-auction-closure-flow.svg)
- [Winner Payment Flow](./docs/diagrams/05-winner-payment-flow.svg)
- [Deposit & Withdrawal Flow](./docs/diagrams/06-deposit-withdrawal-flow.svg)
- [Notification Delivery Flow](./docs/diagrams/07-notification-delivery-flow.svg)

### Advanced Topics
- [Distributed Tracing](./docs/distributed-tracing.md)
- [Audit Logs](./docs/audit-logs.md)

---

## 🛠️ Getting Started

### Prerequisites

- Java 17+
- Node.js 20+
- Docker & Docker Compose

### Run with Docker

```bash
# Clone the repository
git clone <repo-url>
cd bidnow

# Start all backend services
cd backend
docker-compose up -d

# Start the frontend
cd ../frontend
npm install
npm run dev
```

The frontend will be available at `http://localhost:3000`.

> Full environment setup details (env variables, service ports, secrets) are documented in [docs/secret/configuration_management_guide.md](./docs/secret/configuration_management_guide.md).

---

## 🤝 Contributing

Please read [CONTRIBUTING.md](./CONTRIBUTING.md) for our branching strategy, commit conventions, and PR process.

---

© 2026 BidNow Team. All rights reserved.
