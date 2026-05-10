### Issue #10: Email Notification System with Template Management

**Title:** `Implement email notification system with multilingual templates and admin management`

**Description:**

Build the email notification system with HTML template support, multilingual capability (EN/VI), retry mechanism, and admin panel for template management and testing.

**Tasks:**

**Email Service Integration:**

- [ ] Integrate MailTrap
- [ ] Configure SMTP settings and API credentials
- [ ] Create email sender service with retry logic
- [ ] Implement email queue processing

**Template Engine:**

- [ ] Implement template rendering engine with variable substitution
- [ ] Support for: {userName}, {auctionTitle}, {bidAmount}, {currentPrice}, {actionUrl}, {paymentDeadline}, etc.
- [ ] HTML email templates for each notification type (EN/VI)
- [ ] Plain text fallback for each template

**Retry Mechanism:**

- [ ] Retry logic: Attempt 1 (immediate), Attempt 2 (+5 min), Attempt 3 (+15 min)
- [ ] Update email_logs status after each attempt
- [ ] Log failure reasons (invalid email, SMTP error, bounce, etc.)
- [ ] Mark as FAILED after 3 failed attempts
- [ ] Create admin alert for failed emails

**Initial Templates (EN/VI):**

- [ ] Welcome email (registration)
- [ ] Auction created successfully
- [ ] Auction won
- [ ] Auction lost
- [ ] Payment reminder #1
- [ ] Payment reminder #2
- [ ] Payment successful
- [ ] Deposit refunded

**Admin Template Management:**

- [ ] List all templates (filterable by type, language)
- [ ] View template details
- [ ] Edit template (subject, HTML body, text body)
- [ ] Preview template with sample data
- [ ] Test email functionality: admin inputs test email address, system sends test email with template
- [ ] Template activation/deactivation toggle
- [ ] Template variable documentation in UI

**Admin Failed Email Dashboard:**

- [ ] Overview stats: total sent, success rate, failed count, retry count
- [ ] Chart: email delivery stats over time
- [ ] List failed emails with filters (date range, template type, failure reason)
- [ ] Manual retry button for individual failed emails
- [ ] Bulk retry for multiple failed emails

**API Endpoints:**

- [ ] `POST /api/v1/admin/templates` - Create template
- [ ] `GET /api/v1/admin/templates` - List templates
- [ ] `GET /api/v1/admin/templates/{id}` - Get template details
- [ ] `PUT /api/v1/admin/templates/{id}` - Update template
- [ ] `POST /api/v1/admin/templates/{id}/test` - Send test email
- [ ] `GET /api/v1/admin/email-logs` - List email logs with stats
- [ ] `POST /api/v1/admin/email-logs/{id}/retry` - Retry failed email
- [ ] `POST /api/v1/admin/email-logs/bulk-retry` - Retry multiple emails

**Deliverables:**

- Email sending functional with retry mechanism
- All initial templates created in EN and VI
- Admin panel for template management with test email feature
- Admin dashboard for monitoring email delivery
- Failed email retry mechanism working

**Dependencies:**

- Issue #9 (Core infrastructure) completed
- SendGrid/AWS SES account configured
