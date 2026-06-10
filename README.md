# DocSign — Document Signature Platform

Enterprise-grade digital signing platform built with Java 21 + Spring Boot 3.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.3, Spring Security 6 |
| Auth | JWT (jjwt 0.12), BCrypt, RBAC |
| Database | PostgreSQL 16, Spring Data JPA, Flyway |
| Cache | Redis (Lettuce) |
| Storage | MinIO (S3-compatible) |
| PDF | iText 8 |
| Frontend | React 18, Vite, Tailwind CSS, PDF.js, Fabric.js |
| Email | Spring Mail + Thymeleaf |
| DevOps | Docker Compose |

## Project Structure

```
com.labmentix.docsign
├── auth/               JWT auth, User entity, RBAC
├── document/           Upload, status FSM, S3 integration
├── signing/            Token generation, field placement, PDF embed
├── audit/              AOP-based immutable audit logging
├── notification/       Email service, Thymeleaf templates
└── common/             Security config, exceptions, crypto utils
```

## Quick Start

### 1. Start infrastructure
```bash
docker compose up postgres redis minio -d
```

### 2. Create MinIO bucket
Open http://localhost:9001 → login (minioadmin/minioadmin) → create bucket `docsign-documents`

### 3. Run the app
```bash
./mvnw spring-boot:run
```

## RBAC Roles

| Role | Permissions |
|------|------------|
| `OWNER` | Upload docs, send for signing, view audit logs |
| `SIGNER` | Sign documents via token link |
| `VIEWER` | Read-only access to documents |
| `ADMIN` | Full platform access |

## API Endpoints (Phase 1)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/register` | Public | Register new account |
| POST | `/api/auth/login` | Public | Login + get tokens |
| POST | `/api/auth/refresh` | Public | Rotate refresh token |
| POST | `/api/auth/logout` | JWT | Revoke all refresh tokens |
| GET | `/api/auth/me` | JWT | Current user profile |

## Development Roadmap

- [x] **Phase 1** — Spring Boot foundation, PostgreSQL schema, JWT auth + RBAC (Days 1–3)
- [ ] **Phase 2** — Document upload engine, S3, SHA-256, status FSM (Days 4–7)
- [ ] **Phase 3** — Signing workflow, token links, PDF embedding (Days 8–12)
- [ ] **Phase 4** — Audit logging with AOP + HMAC (Days 13–15)
- [ ] **Phase 5** — Email notifications, webhooks (Days 16–18)
- [ ] **Phase 6** — React frontend, PDF viewer, admin panel (Days 19–21)
