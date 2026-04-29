### Issue #12: Real-time Notification System & Frontend UI

**Title:** `Implement real-time push notifications and notification center UI`

**Description:**

Build the real-time notification delivery system via WebSocket and create the frontend notification center UI with bell icon, dropdown, and notification list page.

**Tasks:**

**WebSocket Integration (Backend):**

- [ ] Broadcast notification to user's active WebSocket connections
- [ ] Handle multiple connections per user (multiple devices/tabs)
- [ ] Ensure notification delivered to all user's active connections
- [ ] Log delivery status in database

**WebSocket Integration (Frontend):**

- [ ] Set up WebSocket client connection
- [ ] Authenticate WebSocket connection with JWT
- [ ] Listen for incoming notifications
- [ ] Handle reconnection on disconnect
- [ ] Display toast/popup for new notifications

**Notification Center UI:**

- [ ] Bell icon in header with unread count badge
- [ ] Dropdown panel showing recent notifications (last 10)
- [ ] Notification item component with:
  - Icon based on notification type
  - Title and message
  - Time ago (e.g., "2 minutes ago")
  - Read/unread indicator (dot or highlight)
  - Click to mark as read and navigate to action URL
- [ ] "Mark all as read" button
- [ ] "View all notifications" link to full page

**Notification List Page:**

- [ ] Full list of notifications with pagination
- [ ] Filter tabs: All / Unread
- [ ] Filter by type dropdown
- [ ] Search functionality
- [ ] Individual notification actions: mark read/unread, delete
- [ ] Bulk actions: select multiple, mark as read, delete
- [ ] Empty state when no notifications
- [ ] Loading state while fetching

**Real-time Features:**

- [ ] Toast notification popup for important events (auction won, outbid)
- [ ] Sound notification (optional, user can disable)
- [ ] Desktop browser notification permission request
- [ ] Auto-update unread count in real-time
- [ ] Auto-add new notification to list without refresh

**UI/UX:**

- [ ] Responsive design (mobile, tablet, desktop)
- [ ] Smooth animations for dropdown and toast
- [ ] Color coding by notification type (success, warning, info, error)
- [ ] Notification icons library (bid, auction, payment, etc.)
- [ ] Time formatting (just now, 5 min ago, 2 hours ago, yesterday, date)

**API Integration:**

- [ ] Call notification APIs on mount and pagination
- [ ] Mark as read API call on click
- [ ] Delete API call with confirmation
- [ ] Real-time sync between tabs (using localStorage events)

**Deliverables:**

- WebSocket real-time push working
- Notification center UI functional
- Full notification list page complete
- Toast notifications displaying properly
- All features responsive and tested

**Dependencies:**

- Issue #9 (Core infrastructure) completed
- Issue #11 (CRUD APIs) completed
- Frontend setup (Issue #8) completed
