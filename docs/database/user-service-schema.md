## 2. User Service Tables

### user_profiles

```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL, -- References identity_users.id
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    phone_number VARCHAR(20),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    bio TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
```

#### Table Details
| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | Primary key |
| user_id | UUID | UNIQUE, NOT NULL | User ID from identity service |
| display_name | VARCHAR(100) | | Display name |
| avatar_url | VARCHAR(500) | | Avatar image URL |
| phone_number | VARCHAR(20) | | Phone number |
| address | TEXT | | Address |
| city | VARCHAR(100) | | City |
| country | VARCHAR(100) | | Country |
| postal_code | VARCHAR(20) | | Postal code |
| bio | TEXT | | Biography |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |


### user_roles

```sql
CREATE TYPE user_role_type AS ENUM ('GUEST', 'USER', 'ADMIN');

CREATE TABLE user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL, -- References identity_users.id
    role user_role_type NOT NULL DEFAULT 'USER',
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by UUID, -- References identity_users.id
    UNIQUE(user_id, role)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
```

#### Table Details
| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | Primary key |
| user_id | UUID | NOT NULL | User ID |
| role | user_role_type | NOT NULL, DEFAULT 'USER' | User role (GUEST, USER, ADMIN) |
| granted_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Timestamp when role was granted |
| granted_by | UUID | | User ID who granted the role |


### user_preferences

```sql
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL, -- References identity_users.id
    language VARCHAR(10) DEFAULT 'en',
    timezone VARCHAR(50) DEFAULT 'UTC',
    currency VARCHAR(3) DEFAULT 'USD',
    email_notifications BOOLEAN DEFAULT TRUE,
    push_notifications BOOLEAN DEFAULT TRUE,
    sms_notifications BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
```

#### Table Details
| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | Primary key |
| user_id | UUID | UNIQUE, NOT NULL | User ID |
| language | VARCHAR(10) | DEFAULT 'en' | Preferred language |
| timezone | VARCHAR(50) | DEFAULT 'UTC' | Timezone |
| currency | VARCHAR(3) | DEFAULT 'USD' | Preferred currency |
| email_notifications | BOOLEAN | DEFAULT TRUE | Receive email notifications |
| push_notifications | BOOLEAN | DEFAULT TRUE | Receive push notifications |
| sms_notifications | BOOLEAN | DEFAULT FALSE | Receive SMS notifications |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |

