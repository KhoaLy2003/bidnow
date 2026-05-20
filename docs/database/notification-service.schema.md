## 6. Media Service Tables

### notif_notifications

```sql
CREATE TYPE notification_type AS ENUM (
    'BID_PLACED',
    'BID_OUTBID',
    'AUCTION_ENDING_SOON',
    'AUCTION_WON',
    'AUCTION_LOST',
    'AUCTION_CANCELLED',
    'PAYMENT_REMINDER',
    'PAYMENT_RECEIVED',
    'DEPOSIT_REFUNDED',
    'DEPOSIT_FORFEITED',
    'WATCHLIST_ITEM_STARTING',
    'SYSTEM_ANNOUNCEMENT'
);

CREATE TYPE notification_channel AS ENUM ('IN_APP', 'EMAIL', 'PUSH', 'SMS');
CREATE TYPE notification_status AS ENUM ('PENDING', 'SENT', 'FAILED', 'READ');

CREATE TABLE notif_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL, -- References identity_users.id

    type notification_type NOT NULL,
    channel notification_channel NOT NULL,
    status notification_status NOT NULL DEFAULT 'PENDING',

    -- Content
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    action_url VARCHAR(500),

    -- References
    auction_id UUID, -- References auction_listings.id
    bid_id UUID,     -- References bid_bids.id

    -- Metadata
    metadata JSONB,

    -- Delivery
    sent_at TIMESTAMP,
    read_at TIMESTAMP,
    failed_reason TEXT,
    retry_count INTEGER DEFAULT 0,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notif_notifications_user_id ON notif_notifications(user_id);
CREATE INDEX idx_notif_notifications_status ON notif_notifications(status);
CREATE INDEX idx_notif_notifications_type ON notif_notifications(type);
CREATE INDEX idx_notif_notifications_created_at ON notif_notifications(created_at DESC);
CREATE INDEX idx_notif_notifications_unread ON notif_notifications(user_id, status) WHERE status != 'READ';
```

#### Table Details
| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | Primary key |
| user_id | UUID | NOT NULL | Recipient user ID |
| type | notification_type | NOT NULL | Notification type |
| channel | notification_channel | NOT NULL | Delivery channel (IN_APP, EMAIL, PUSH, SMS) |
| status | notification_status | NOT NULL, DEFAULT 'PENDING' | Delivery status (PENDING, SENT, FAILED, READ) |
| title | VARCHAR(255) | NOT NULL | Notification title |
| message | TEXT | NOT NULL | Notification message content |
| action_url | VARCHAR(500) | | Action URL when the notification is clicked |
| auction_id | UUID | | Related auction ID |
| bid_id | UUID | | Related bid ID |
| metadata | JSONB | | Additional metadata |
| sent_at | TIMESTAMP | | Sent timestamp |
| read_at | TIMESTAMP | | Read timestamp |
| failed_reason | TEXT | | Failure reason |
| retry_count | INTEGER | DEFAULT 0 | Number of retry attempts |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |


### notif_user_preferences

```sql
CREATE TABLE notif_user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL, -- References identity_users.id

    -- Channel preferences
    email_enabled BOOLEAN DEFAULT TRUE,
    push_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled BOOLEAN DEFAULT FALSE,

    -- Type preferences (JSONB for flexibility)
    type_preferences JSONB DEFAULT '{
        "BID_OUTBID": {"email": true, "push": true, "sms": false},
        "AUCTION_WON": {"email": true, "push": true, "sms": true},
        "PAYMENT_REMINDER": {"email": true, "push": true, "sms": false}
    }',

    -- Quiet hours
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    quiet_hours_timezone VARCHAR(50),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notif_user_preferences_user_id ON notif_user_preferences(user_id);
```

#### Table Details
| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | Primary key |
| user_id | UUID | UNIQUE, NOT NULL | User ID |
| email_enabled | BOOLEAN | DEFAULT TRUE | Enable/disable email |
| push_enabled | BOOLEAN | DEFAULT TRUE | Enable/disable push notifications |
| sms_enabled | BOOLEAN | DEFAULT FALSE | Enable/disable SMS |
| type_preferences | JSONB | | Channel preferences for each notification type |
| quiet_hours_start | TIME | | Start of quiet hours |
| quiet_hours_end | TIME | | End of quiet hours |
| quiet_hours_timezone | VARCHAR(50) | | Timezone for quiet hours |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |


### notif_email_logs

```sql
CREATE TABLE notif_email_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID, -- References notif_notifications.id
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL, -- PENDING, SENT, FAILED, RETRY
    failure_reason TEXT,
    retry_count INT DEFAULT 0,
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Table Details
| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | Primary key |
| notification_id | UUID | | Associated notification ID |
| recipient_email | VARCHAR(255) | NOT NULL | Recipient email address |
| subject | VARCHAR(255) | NOT NULL | Email subject |
| template_name | VARCHAR(255) | NOT NULL | Name of the template used |
| status | VARCHAR(255) | NOT NULL | Email status (PENDING, SENT, FAILED, RETRY) |
| failure_reason | TEXT | | Reason for failure |
| retry_count | INT | DEFAULT 0 | Number of retries |
| sent_at | TIMESTAMP | | Timestamp when email was sent |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |


### notif_notification_templates

```sql
CREATE TABLE notif_notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(255) NOT NULL, -- EMAIL, IN_APP, PUSH, SMS
    language VARCHAR(255) NOT NULL, -- EN, VI
    subject VARCHAR(255), -- For email templates
    body_html TEXT,
    body_text TEXT NOT NULL,
    variables JSONB, -- List of placeholders
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Table Details
| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | Primary key |
| name | VARCHAR(255) | NOT NULL, UNIQUE | Template identifier (e.g., AUCTION_WON_EMAIL) |
| type | VARCHAR(255) | NOT NULL | Template type |
| language | VARCHAR(255) | NOT NULL | Template language (EN, VI) |
| subject | VARCHAR(255) | | Default subject for emails |
| body_html | TEXT | | HTML content for emails |
| body_text | TEXT | NOT NULL | Plain text content |
| variables | JSONB | | List of supported variable names |
| active | BOOLEAN | DEFAULT TRUE | Whether the template is active |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |

