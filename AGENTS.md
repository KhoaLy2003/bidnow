# BidNow AI Agent Instructions

Welcome to the BidNow project! This `AGENTS.md` file is designed to provide you (an autonomous AI coding agent) with the necessary context, constraints, and instructions to interact with, build, test, and modify this repository effectively.

## 🏢 Project Overview
**BidNow** is a modern, high-performance E-commerce platform specialized in real-time auctions. 
The system leverages an event-driven microservices architecture to manage real-time English auction models, auto-bidding, anti-sniping features, and instant websocket notifications.

The project is split into two main directories:
- `/backend`: Java 17 / Spring Boot 3.x microservices.
- `/frontend`: Next.js 14 / TypeScript frontend.
- `/docs`: Technical documentation and architecture diagrams.

Before making any significant architectural changes, always read the relevant documentation in `/docs` to align with the core requirements.

## 🛠️ Tech Stack & Constraints

### Backend Architecture
- **Language:** Java 17
- **Framework:** Spring Boot 3.x with Spring Cloud (Gateway, Service Discovery, Config Server).
- **Build Tool:** Maven. Use Maven commands (e.g., `./mvnw clean install`) for building and testing.
- **Database:** PostgreSQL 15+. 
  - **Constraint:** Each microservice MUST have its own independent database. Do not introduce cross-database queries or shared databases between microservices.
- **Messaging:** RabbitMQ or Apache Kafka. Use event-driven patterns for inter-service communication.
- **Caching & Real-time:** Redis is used for global caching, sessions, and high-performance bid leaderboards.

### Frontend Architecture
- **Framework:** Next.js (TypeScript)
- **Styling:** Tailwind CSS
- **State Management:** Zustand or React Context
- **Real-time:** Socket.io-client or Native WebSockets for live price updates.
- **Constraint:** Always write robust TypeScript interfaces/types for API payloads.

## 🌿 Version Control & Workflow

When committing code or creating branches on behalf of the user, strictly follow these conventions from our `CONTRIBUTING.md`:

### Git Branches
- Use the format `feature/<feature-name>`, `bugfix/<bug-name>`, or `hotfix/<hotfix-name>`.

### Conventional Commits
All commits must follow the conventional commits standard:
`<type>(optional-scope): <short description>`
Valid types: `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`.
Example: `feat(bidding): implement anti-sniping time extension`

## 🧠 Core Agent Directives

1. **Be Concise and Precise:** Do not output unnecessary conversational text. When asked to write or edit code, focus on the implementation.
2. **Review Existing Context First:** Before creating a new component or service, check if a shared template or similar implementation already exists in `/common-resources` or other services.
3. **Respect Microservice Boundaries:** Do not bleed domain logic from the Bidding Service into the User Service, for example. Refer to `docs/architecture.md` for boundary definitions.
4. **Update Documentation:** If you alter the system's architecture, add a new microservice, or change the API surface, you MUST update the respective markdown files in `/docs`.
5. **No Destructive Operations Without Confirmation:** Do not delete databases or run destructive migrations without explicit user approval.
6. **Testing:** Write unit tests for your code. The backend uses JUnit/Mockito, and the frontend should be tested where appropriate.

## 🏃 Commands to Remember
- Backend Build: `cd backend && ./mvnw clean install` (if wrapper exists, or standard `mvn`)
- Frontend Dev: `cd frontend && npm run dev` (or `pnpm`/`yarn` depending on lockfile presence)

*Note: As the project evolves, this AGENTS.md file should be updated to reflect new operational context, tools, and scripts.*
