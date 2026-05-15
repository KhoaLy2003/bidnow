## 1. Identity Service Tables

### identity_users

```sql
CREATE TABLE identity_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_email_verified BOOLEAN DEFAULT FALSE,
    verification_otp VARCHAR(6),
    otp_expires_at TIMESTAMP,
    password_reset_token VARCHAR(255),
    password_reset_expires_at TIMESTAMP,
    last_login_at TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_identity_users_email ON identity_users(email);
CREATE INDEX idx_identity_users_email_verified ON identity_users(is_email_verified);
```

#### Table Details
| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | Primary key, auto-generated UUID |
| email | VARCHAR(255) | UNIQUE, NOT NULL | User's email address |
| password_hash | VARCHAR(255) | NOT NULL | Hashed password |
| is_email_verified | BOOLEAN | DEFAULT FALSE | Email verification status |
| verification_otp | VARCHAR(6) | | 6-digit OTP for email verification |
| otp_expires_at | TIMESTAMP | | Expiration time for OTP |
| password_reset_token | VARCHAR(255) | | Token for password reset |
| password_reset_expires_at | TIMESTAMP | | Expiration time for password reset token |
| last_login_at | TIMESTAMP | | Last login timestamp |
| failed_login_attempts | INTEGER | DEFAULT 0 | Number of failed login attempts |
| locked_until | TIMESTAMP | | Account lockout expiration time |
| is_active | BOOLEAN | DEFAULT TRUE | Account active status |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |


### identity_refresh_tokens

JWT Refresh Token Management

```sql
CREATE TABLE identity_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_identity_refresh_tokens_user_id ON identity_refresh_tokens(user_id);
CREATE INDEX idx_identity_refresh_tokens_expires_at ON identity_refresh_tokens(expires_at);
```

#### Table Details
| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | Primary key, auto-generated UUID |
| user_id | UUID | NOT NULL, REFERENCES identity_users(id) | User ID |
| token_hash | VARCHAR(255) | UNIQUE, NOT NULL | Hashed refresh token |
| device_info | VARCHAR(500) | | Device information |
| ip_address | VARCHAR(45) | | IP address |
| expires_at | TIMESTAMP | NOT NULL | Expiration timestamp |
| is_revoked | BOOLEAN | DEFAULT FALSE | Revocation status |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

