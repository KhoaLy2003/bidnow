# Sensitive Configuration Management Sheet

## Purpose
This sheet is used to manage and track sensitive environment configuration information required during development, deployment, and maintenance.

> Recommended:
> - Store the actual secret values in a secure vault/tool.
> - Use this sheet mainly for tracking ownership, usage, rotation, and access.
> - Avoid exposing real PROD secrets in shared documents.

---

# Environment Overview

| Environment | Purpose | Access Level | Owner | Notes |
|---|---|---|---|---|
| DEV | Development & local testing | Developers | Tech Lead / DevOps | Shared development environment |
| PROD | Production system | Restricted | DevOps / Admin | Strict access control |

---

# DEV Environment

## Database Connections

| System | DB Type | Host | Port | Database Name | Username | Password Location | Access Owner | Rotation Policy | Notes |
|---|---|---|---|---|---|---|---|---|---|
| User Service | PostgreSQL |  | 5432 |  |  | Vault / Secret Manager |  | Every 90 days |  |
| Media Service | PostgreSQL |  | 5432 |  |  | Vault / Secret Manager |  | Every 90 days |  |
| Auth Service | MySQL |  | 3306 |  |  | Vault / Secret Manager |  | Every 90 days |  |

---

## API Keys / Tokens

| Service | Key Type | Usage | Storage Location | Owner | Expiration | Rotation Policy | Notes |
|---|---|---|---|---|---|---|---|
| Firebase | API Key | Push Notification | Vault / GitHub Secret |  |  | Every 90 days |  |
| SendGrid | API Key | Email Service | Vault / GitHub Secret |  |  | Every 90 days |  |
| AWS | Access Key | Cloud Resource Access | Vault / Secret Manager |  |  | Every 60 days |  |

---

## Infrastructure / Server Access

| Resource | Type | Endpoint/IP | Username | Authentication Method | Access Owner | Notes |
|---|---|---|---|---|---|---|
| DEV Server | Linux VM |  |  | SSH Key |  |  |
| Kubernetes Cluster | K8s |  |  | kubeconfig |  |  |
| Redis | Cache |  |  | Password |  |  |

---

## CI/CD Secrets

| Platform | Secret Name | Purpose | Managed By | Rotation Policy | Notes |
|---|---|---|---|---|---|
| GitHub Actions | DEV_DB_PASSWORD | Database Connection | DevOps | Every 90 days |  |
| GitHub Actions | AWS_ACCESS_KEY_ID | AWS Deployment | DevOps | Every 60 days |  |
| Jenkins | SONAR_TOKEN | SonarQube Scan | DevOps | Every 90 days |  |

---

# PROD Environment

## Database Connections

| System | DB Type | Host | Port | Database Name | Username | Password Location | Access Owner | Rotation Policy | Notes |
|---|---|---|---|---|---|---|---|---|---|
| User Service | PostgreSQL |  | 5432 |  |  | Vault / Secret Manager |  | Every 60 days | Restricted access |
| Media Service | PostgreSQL |  | 5432 |  |  | Vault / Secret Manager |  | Every 60 days | Restricted access |
| Auth Service | MySQL |  | 3306 |  |  | Vault / Secret Manager |  | Every 60 days | Restricted access |

---

## API Keys / Tokens

| Service | Key Type | Usage | Storage Location | Owner | Expiration | Rotation Policy | Notes |
|---|---|---|---|---|---|---|---|
| Firebase | API Key | Push Notification | Vault / Secret Manager |  |  | Every 60 days | Restricted access |
| SendGrid | API Key | Email Service | Vault / Secret Manager |  |  | Every 60 days | Restricted access |
| AWS | Access Key | Cloud Resource Access | Vault / Secret Manager |  |  | Every 30 days | MFA required |

---

## Infrastructure / Server Access

| Resource | Type | Endpoint/IP | Username | Authentication Method | Access Owner | Notes |
|---|---|---|---|---|---|---|
| PROD Server | Linux VM |  |  | SSH Key + MFA |  | Restricted access |
| Kubernetes Cluster | K8s |  |  | kubeconfig + MFA |  | Restricted access |
| Redis | Cache |  |  | Password |  | Restricted access |

---

## CI/CD Secrets

| Platform | Secret Name | Purpose | Managed By | Rotation Policy | Notes |
|---|---|---|---|---|---|
| GitHub Actions | PROD_DB_PASSWORD | Database Connection | DevOps | Every 60 days | Restricted access |
| GitHub Actions | AWS_ACCESS_KEY_ID | AWS Deployment | DevOps | Every 30 days | MFA required |
| Jenkins | SONAR_TOKEN | SonarQube Scan | DevOps | Every 60 days | Restricted access |

---

# Recommended Security Practices

## Secret Management
- Do not store real secrets directly in Git repositories.
- Use Secret Manager / Vault solutions:
  - HashiCorp Vault
  - AWS Secrets Manager
  - Azure Key Vault
  - Google Secret Manager
  - GitHub Actions Secrets

## Access Control
- Separate DEV and PROD access.
- Apply least privilege principle.
- Enable MFA for production access.
- Track who can access each resource.

## Rotation Policy
- Rotate passwords and keys regularly.
- Immediately rotate secrets when a member leaves the team.
- Avoid using shared personal accounts.

## Audit & Monitoring
- Maintain audit logs for secret access.
- Monitor unusual login activity.
- Review access permissions periodically.

---

# Suggested Folder Structure

```text
/config
  /dev
    application-dev.yml
    secrets-dev.env

  /prod
    application-prod.yml
    secrets-prod.env
```

---

# Suggested Tools

| Purpose | Recommended Tool |
|---|---|
| Secret Management | HashiCorp Vault |
| CI/CD Secret Storage | GitHub Actions Secrets |
| Documentation | Confluence / Notion |
| Password Sharing | 1Password / Bitwarden |
| Infrastructure Secret Injection | Kubernetes Secrets |
| Code Scan | SonarQube |

