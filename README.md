# BidNow

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14-black.svg)](https://nextjs.org/)
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
- **Database:** PostgreSQL (Independent database per service)
- **Real-time:** WebSockets & Redis
- **Messaging:** RabbitMQ / Kafka (Event-driven architecture)
- **Storage:** Cloudinary (Media management)

### Frontend
- **Framework:** Next.js (TypeScript)
- **Styling:** Tailwind CSS
- **Real-time:** Socket.io / WebSocket clients

---

## 📁 Project Structure

```bash
bidnow/
├── backend/            # Backend Microservices Source Code
├── frontend/           # Next.js Frontend Source Code
├── docs/               # Technical Documentation & Diagrams
├── common-resources/   # Shared templates and assets
└── README.md           # Project Overview
```

---

## 📖 Documentation
Detailed documentation can be found in the [docs/](./docs/) directory:
- [Business Clarifications](./docs/business-clarifications.md)
- [Functional Requirements](./docs/functional.md)
- [Non-Functional Requirements](./docs/non-functional.md)
- [Architecture Overview](./docs/architecture.md)

---

## 🛠️ Getting Started
*(Instructions for environment setup and running the services will be added as development progresses.)*

---

© 2026 BidNow Team. All rights reserved.
