### Issue #11: Notification History & User Management APIs

**Title:** `Implement notification CRUD operations and user-facing APIs`

**Description:**

Build user-facing APIs for managing notifications including listing, reading, marking as read/unread, and deleting notifications.

**Tasks:**

**API Endpoints:**

- [ ] `GET /api/v1/notifications` - List user's notifications (paginated, default 20 per page)
- [ ] `GET /api/v1/notifications/unread-count` - Get unread notification count
- [ ] `GET /api/v1/notifications/{id}` - Get single notification details
- [ ] `PUT /api/v1/notifications/{id}/read` - Mark notification as read
- [ ] `PUT /api/v1/notifications/{id}/unread` - Mark notification as unread
- [ ] `PUT /api/v1/notifications/mark-all-read` - Mark all notifications as read
- [ ] `DELETE /api/v1/notifications/{id}` - Soft delete single notification
- [ ] `DELETE /api/v1/notifications/delete-all` - Soft delete all notifications
- [ ] `DELETE /api/v1/notifications/delete-read` - Delete all read notifications

**Query Features:**

- [ ] Pagination support (page, size, sort)
- [ ] Filter by: read/unread, notification type, date range
- [ ] Sort by: created_at (newest first default)
- [ ] Search by title or message content

**Business Logic:**

- [ ] Soft delete implementation (set deleted = true, not physical delete)
- [ ] Auto-mark as read when user views notification details
- [ ] Return metadata in proper JSON format
- [ ] Handle invalid notification IDs gracefully

**Response Format:**

```json
{
  "data": [
    {
      "id": "uuid",
      "type": "OUTBID",
      "channel": "REALTIME",
      "title": "You've been outbid!",
      "message": "Your bid of $100 on 'iPhone 15' has been outbid.",
      "metadata": {
        "auctionId": "uuid",
        "auctionTitle": "iPhone 15",
        "previousBid": 100,
        "currentBid": 110
      },
      "actionUrl": "/auctions/uuid",
      "read": false,
      "createdAt": "2026-04-25T10:30:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3
  }
}
```

**Validation & Security:**

- [ ] User can only access their own notifications
- [ ] Validate pagination parameters (max size: 100)
- [ ] Rate limiting on notification endpoints

**Testing:**

- [ ] Unit tests for all service methods
- [ ] Integration tests for all API endpoints
- [ ] Test edge cases (empty list, invalid IDs, unauthorized access)

**Deliverables:**

- All notification CRUD APIs functional
- Pagination and filtering working correctly
- Proper error handling and validation
- API documentation updated

**Dependencies:**

- Issue #9 (Core infrastructure) completed
- User authentication (Issue #4) completed
