# Database Migration Rules

## Standards

### Version Control for Schema using Liquibase
- **Liquibase Formatted SQL** (`.sql` files) is the project standard for all migrations. It combines the power of raw, optimized PostgreSQL (JSONB, partial indexes, native constraints) with Liquibase's robust tracking metadata.
- **Root Configuration**: All microservices must reference a central master changelog:
  ```yaml
  spring:
    liquibase:
      enabled: true
      change-log: classpath:db/changelog/db.changelog-master.xml
  ```

### Directory Layout
Keep changelogs organized within the main resources folder:
```
src/main/resources/db/changelog/
├── db.changelog-master.xml
└── migrations/
    ├── 001-initial-schema.sql
    └── 002-add-user-avatars.sql
```

### Changeset Identification & Generation
- **Changeset Format**: Every migration file begins with `-- liquibase formatted sql` followed by changeset declarations:
  ```sql
  -- changeset <author>:<unique_id> [runInTransaction:false]
  ```
- **Author**: Developer username/alias (e.g., `khoa`).
- **Unique ID**: Always use **Epoch Milliseconds** to prevent merge conflicts in multi-developer environments.
- **Generation Helper**: Generate the unique ID on Windows/PowerShell using the following command:
  ```powershell
  [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  ```

### Rollback & Locking Standards
- **Rollbacks**: Every changeset *must* provide a `-- rollback` instruction to ensure reversible deployments.
- **Non-Blocking Indexing**: Creating indexes on large tables blocks writes. Always use `CREATE INDEX CONCURRENTLY` and set `runInTransaction:false` on the changeset header.
- **Lock Recovery**: If a pod crashes during a migration and locks `DATABASECHANGELOGLOCK`, release it with:
  ```sql
  UPDATE databasechangeloglock SET locked = false, lockgranted = null, lockedby = null WHERE id = 1;
  ```

---

## Best Practice Templates

### 1. Master Changelog (`db.changelog-master.xml`)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd">

    <include file="db/changelog/migrations/001-initial-schema.sql" relativeToChangelogFile="false"/>

</databaseChangeLog>
```

### 2. Concise Liquibase SQL Migration (`001-initial-schema.sql`)
```sql
-- liquibase formatted sql

-- changeset khoa:1778855571877
-- comment: Initialize core database schema and indexes
CREATE TABLE categories
(
    id            UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    name          VARCHAR(100)             NOT NULL UNIQUE,
    slug          VARCHAR(100)             NOT NULL UNIQUE,
    display_order INTEGER                  NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_slug ON categories (slug);

CREATE TABLE products
(
    id             UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    owner_id       UUID                     NOT NULL,
    title          VARCHAR(255)             NOT NULL,
    category_id    UUID                     NOT NULL,
    price          DECIMAL(15, 2)           NOT NULL CHECK (price >= 0),
    status         VARCHAR(20)              NOT NULL DEFAULT 'DRAFT',
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- Optimize active product queries with partial indexes
CREATE INDEX idx_products_status_active 
ON products (status) 
WHERE deleted_at IS NULL AND status = 'ACTIVE';

-- Seed basic categories
INSERT INTO categories (name, slug, display_order)
VALUES ('Electronics', 'electronics', 1),
       ('Fashion', 'fashion', 2),
       ('Home & Garden', 'home-garden', 3);

-- rollback DROP TABLE products CASCADE;
-- rollback DROP TABLE categories CASCADE;
```

### 3. Non-Blocking Index Migration Template
```sql
-- liquibase formatted sql

-- changeset khoa:1778855571899 runInTransaction:false
-- comment: Create high-load index concurrently without locking writes
CREATE INDEX CONCURRENTLY idx_products_owner_active 
ON products (owner_id) 
WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- rollback DROP INDEX CONCURRENTLY idx_products_owner_active;
```
