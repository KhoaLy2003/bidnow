### Issue #9: Media Service - Core Infrastructure Setup

**Title:** `Setup notification service infrastructure and database schema`

**Description:**

Set up the foundational infrastructure for the Media Service including database schema, message broker integration, and WebSocket support for real-time notifications.

**Tasks:**

**Database Schema:**

- [ ] Create `notifications` table with columns: id, user_id, type, channel, title, message, metadata (JSONB), action_url, read, deleted, created_at
- [ ] Create `email_logs` table: id, notification_id, recipient_email, subject, template_name, status, failure_reason, retry_count, sent_at, created_at
- [ ] Create `notification_templates` table: id, name, type, language, subject, body_html, body_text, variables (JSONB), active, created_at, updated_at
- [ ] Set up database indexes for performance (user_id, created_at, read, deleted)
- [ ] Create ENUM types for notification_type, channel, language, email_status

**Message Broker Integration:**

- [ ] Configure RabbitMQ/Kafka consumer
- [ ] Set up event listeners for: USER_REGISTERED, AUCTION_CREATED, BID_PLACED, AUCTION_ENDED, PAYMENT events
- [ ] Create event routing logic based on event type
- [ ] Configure dead letter queue for failed event processing

**WebSocket Infrastructure:**

- [ ] Set up WebSocket server for real-time push notifications
- [ ] Implement user connection management (map userId to WebSocket connections)
- [ ] Create broadcast mechanism for sending notifications to specific users
- [ ] Handle connection/disconnection events
- [ ] Implement heartbeat/ping-pong for connection health

**Service Structure:**

- [ ] Create repository layer for database access
- [ ] Create service layer for business logic
- [ ] Set up internal API endpoints for inter-service communication
- [ ] Configure logging and error handling

**Deliverables:**

- Working Media Service that can consume events
- Database schema created and migrations ready
- WebSocket server running and accepting connections
- Health check endpoint functional

**Dependencies:**

- Issue #7 (Backend setup) completed
- Message broker (RabbitMQ/Kafka) running
- PostgreSQL database available
