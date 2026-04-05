# SmartPay Backend

A production-grade financial backend system built with Java and Spring Boot, supporting group expense splitting, peer-to-peer UPI transfers, role-based access control, and real-time financial analytics.

> Submitted as part of the internship assignment: **Finance Data Processing and Access Control Backend**

---

## Assignment Mapping

| Requirement                                      | Implementation                                                                   |
|--------------------------------------------------|----------------------------------------------------------------------------------|
| User & role management                           | `POST /auth/register`, `PUT /users/{id}/role`, VIEWER / ANALYST / ADMIN roles    |
| User active/inactive status                      | `PUT /users/{id}/status`, login blocked for inactive users                       |
| Financial records (create, view, update, delete) | `POST /expenses`, `GET /expenses`, `PUT /expenses/{id}`, `DELETE /expenses/{id}` |
| Filtering by date, category, type                | `GET /expenses/group/{id}/filter`, `GET /expenses/personal/filter`               |
| Pagination                                       | `GET /expenses/group/{id}/paged`, `GET /expenses/personal/paged`                 |
| Dashboard summary APIs                           | `GET /dashboard/summary-detail`, `GET /dashboard/chart`                          |
| Category wise totals                             | `GET /analysis/me` — full category breakdown with percentages                    |
| Weekly and monthly trends                        | `GET /dashboard/chart`, `GET /dashboard/summary-detail`                          |
| Recent activity                                  | Included in `/dashboard/summary-detail`                                          |
| Role based access control                        | `RoleGuard` — VIEWER read-only, ANALYST write, ADMIN full access                 |
| Input validation and error handling              | `GlobalExceptionHandler`, `ApiException` with proper HTTP status codes           |
| Soft delete                                      | `isCancelled` flag on expenses, reverse ledger entries                           |
| Token authentication                             | JWT — all endpoints except `/auth/**` and `/health` are protected                |
| API documentation                                | Swagger UI at `/swagger-ui.html`                                                 |

---

## Architecture Overview

### Double Entry Ledger
The financial core uses a double-entry ledger — the same pattern used by banks. No balances are stored anywhere. Every transaction creates ledger entries, and balances are always computed from those entries. Cancellations create reverse entries. This makes it mathematically impossible to have inconsistent balances.

```
Expense created → Ledger entries: debtor → payer
Expense cancelled → Reverse ledger entries: payer → debtor
Balance = SUM(incoming) - SUM(outgoing) at query time
```

### Role Based Access Control
Three roles with clearly enforced permissions:

| Action                             | VIEWER | ANALYST | ADMIN |
|------------------------------------|--------|---------|-------|
| View expenses and dashboard        | ✅      | ✅       | ✅     |
| Create / edit / cancel expenses    | ❌      | ✅       | ✅     |
| Initiate transfers and settlements | ❌      | ✅       | ✅     |
| Manage users (roles, status)       | ❌      | ❌       | ✅     |

### Performance
- **Database indexes** on all hot columns (ledger, expenses, group members)
- **Redis caching** on heavy endpoints with per-cache TTLs
- **Parallel query execution** via CompletableFuture — dashboard runs 9 queries simultaneously
- **Neon keep-alive scheduler** — pings DB every 4 minutes to prevent serverless cold starts

---

## Tech Stack

| Layer         | Technology                   |
|---------------|------------------------------|
| Language      | Java 21                      |
| Framework     | Spring Boot 4                |
| Database      | PostgreSQL (Neon Serverless) |
| Cache         | Redis                        |
| Auth          | JWT + Spring Security        |
| ORM           | Spring Data JPA / Hibernate  |
| Migrations    | Flyway                       |
| Documentation | Swagger / OpenAPI 3          |
| Build         | Maven                        |

---

## Prerequisites

- Java 21+
- PostgreSQL 17+
- Redis
- Maven 3.6+

---

## Setup

### 1. Clone the Repository

```bash
git clone https://github.com/your-repo/SmartPay-Backend.git
cd SmartPay-Backend
```

### 2. Configure Environment Variables

Set the following environment variables or add them to `application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/smartpay
spring.datasource.username=your_db_user
spring.datasource.password=your_db_password

# JWT
JWT_SECRET=your_jwt_secret

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Fraud Risk API
RISK_API_URL=https://Akenzz-SmartPay.hf.space/evaluate-risk

# Server
server.port=8000
```

### 3. Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

### 4. Verify

```bash
curl https://zorvyn-wuxb.onrender.com/health
```

### 5. API Documentation

```
https://zorvyn-wuxb.onrender.com/swagger-ui.html
```

---

## API Reference

### Authentication

| Method | Endpoint         | Description             | Auth |
|--------|------------------|-------------------------|------|
| POST   | `/auth/register` | Register new user       | None |
| POST   | `/auth/login`    | Login and get JWT token | None |

---

### User Management

| Method | Endpoint             | Description                 | Role Required |
|--------|----------------------|-----------------------------|---------------|
| GET    | `/users/me`          | Get current user profile    | Any           |
| PUT    | `/users/upi`         | Update UPI ID               | Any           |
| GET    | `/users/all`         | Get all users               | ADMIN         |
| PUT    | `/users/{id}/role`   | Update user role            | ADMIN         |
| PUT    | `/users/{id}/status` | Activate or deactivate user | ADMIN         |

---

### Groups

| Method | Endpoint               | Description                   | Role Required |
|--------|------------------------|-------------------------------|---------------|
| POST   | `/groups`              | Create a group                | ANALYST+      |
| GET    | `/groups/my`           | Get my groups                 | Any           |
| GET    | `/groups/{id}/members` | Get group members             | Any           |
| POST   | `/groups/{id}/members` | Add member to group           | ADMIN         |
| POST   | `/groups/join`         | Join group by code            | Any           |
| GET    | `/groups/{id}/detail`  | Full group detail in one call | Any           |

---

### Expenses

| Method | Endpoint                      | Description                      | Role Required |
|--------|-------------------------------|----------------------------------|---------------|
| POST   | `/expenses`                   | Create group expense             | ANALYST+      |
| POST   | `/expenses/direct-split`      | Create ad-hoc expense            | ANALYST+      |
| GET    | `/expenses/group/{id}`        | Get group expenses               | Any           |
| GET    | `/expenses/group/{id}/my`     | Get my expenses in group         | Any           |
| GET    | `/expenses/my`                | Get all my expenses              | Any           |
| PUT    | `/expenses/{id}`              | Edit expense                     | ANALYST+      |
| DELETE | `/expenses/{id}`              | Cancel expense (soft delete)     | ANALYST+      |
| GET    | `/expenses/group/{id}/filter` | Filter expenses by date/category | Any           |
| GET    | `/expenses/personal/filter`   | Filter personal expenses         | Any           |
| GET    | `/expenses/group/{id}/paged`  | Paginated group expenses         | Any           |
| GET    | `/expenses/personal/paged`    | Paginated personal expenses      | Any           |

**Filter parameters:** `category`, `from` (YYYY-MM-DD), `to` (YYYY-MM-DD)

**Pagination parameters:** `page` (default 0), `size` (default 10)

---

### Personal Expenses

| Method | Endpoint                | Description              | Role Required |
|--------|-------------------------|--------------------------|---------------|
| POST   | `/personal-expenses`    | Record personal expense  | ANALYST+      |
| GET    | `/personal-expenses/my` | Get my personal expenses | Any           |

---

### Ledger

| Method | Endpoint                              | Description                  | Role Required |
|--------|---------------------------------------|------------------------------|---------------|
| GET    | `/ledger/group/{id}/balances`         | All member balances in group | Any           |
| GET    | `/ledger/group/{id}/user/{userId}`    | Single user balance in group | Any           |
| GET    | `/ledger/group/{id}/transactions`     | Group transaction history    | Any           |
| GET    | `/ledger/group/{id}/simplified-debts` | Optimized settlement plan    | Any           |
| GET    | `/ledger/my-transactions`             | All my transactions          | Any           |
| GET    | `/ledger/who-owes-me`                 | Summary of who owes me       | Any           |
| GET    | `/ledger/whom-i-owe`                  | Summary of whom I owe        | Any           |

---

### Settlements

| Method | Endpoint                             | Description                             | Role Required |
|--------|--------------------------------------|-----------------------------------------|---------------|
| POST   | `/settlements/initiate`              | Initiate settlement, get UPI link       | ANALYST+      |
| POST   | `/settlements/{id}/claim`            | Payer claims they paid                  | ANALYST+      |
| POST   | `/settlements/{id}/confirm`          | Receiver confirms payment               | ANALYST+      |
| POST   | `/settlements/{id}/dispute`          | Receiver disputes payment               | ANALYST+      |
| GET    | `/settlements/pending-confirmations` | Settlements waiting for my confirmation | Any           |

---

### Transfers (P2P)

| Method | Endpoint                           | Description                           | Role Required |
|--------|------------------------------------|---------------------------------------|---------------|
| POST   | `/transfers/initiate`              | Initiate P2P transfer, get UPI link   | ANALYST+      |
| POST   | `/transfers/{id}/claim`            | Sender claims they sent               | ANALYST+      |
| POST   | `/transfers/{id}/confirm`          | Receiver confirms receipt             | ANALYST+      |
| POST   | `/transfers/{id}/dispute`          | Receiver disputes claim               | ANALYST+      |
| GET    | `/transfers/pending-confirmations` | Transfers waiting for my confirmation | Any           |
| GET    | `/transfers/my`                    | My transfer history                   | Any           |

---

### Dashboard

| Method | Endpoint                    | Description                                         | Role Required |
|--------|-----------------------------|-----------------------------------------------------|---------------|
| GET    | `/dashboard/chart`          | Weekly income vs expense chart data                 | Any           |
| GET    | `/dashboard/summary-detail` | Full dashboard — balance, pending, activity, trends | Any           |
| GET    | `/dashboard/weekly-summary` | 7 day daily breakdown                               | Any           |

---

### Financial Analysis

| Method | Endpoint       | Description                                             | Role Required |
|--------|----------------|---------------------------------------------------------|---------------|
| GET    | `/analysis/me` | Self-computed financial insights and category breakdown | Any           |

**Response includes:**
- Total income, expense, net balance
- Category wise breakdown with percentages
- Monthly comparison with trend (UP / DOWN / STABLE)
- Weekly and daily averages
- Top spending category and most frequent category

---

### Friends

| Method | Endpoint               | Description           | Role Required |
|--------|------------------------|-----------------------|---------------|
| POST   | `/friends/request`     | Send friend request   | Any           |
| POST   | `/friends/{id}/accept` | Accept friend request | Any           |
| POST   | `/friends/{id}/reject` | Reject friend request | Any           |
| GET    | `/friends/pending`     | Get pending requests  | Any           |
| GET    | `/friends`             | Get friends list      | Any           |

---

### Fraud Risk

| Method | Endpoint         | Description                   | Role Required |
|--------|------------------|-------------------------------|---------------|
| POST   | `/risk/evaluate` | Evaluate fraud risk by UPI ID | Any           |

**Request:** `{ "receiverUpiId": "someone@upi", "amount": 500.00 }`

---

### Reports

| Method | Endpoint   | Description              | Role Required |
|--------|------------|--------------------------|---------------|
| POST   | `/reports` | Report a user as scammer | Any           |

---

### Health

| Method | Endpoint  | Description         | Auth |
|--------|-----------|---------------------|------|
| GET    | `/health` | Server health check | None |

---

## Error Handling

All responses follow a consistent envelope:

```json
{
  "success": true | false,
  "message": "Description",
  "data": { } | "null"
}
```

| Status Code | Meaning                               |
|-------------|---------------------------------------|
| 200         | Success                               |
| 400         | Bad request or validation error       |
| 401         | Unauthorized — missing or invalid JWT |
| 403         | Forbidden — insufficient role         |
| 404         | Resource not found                    |
| 500         | Internal server error                 |

---

## Assumptions and Design Decisions

1. **Soft delete over hard delete** — Expenses are never deleted. Cancellation creates reverse ledger entries maintaining full audit trail.

2. **No payment aggregation** — Real money moves peer to peer via UPI deep links. The backend never holds or processes money, avoiding RBI Payment Aggregator compliance requirements.

3. **Dual confirmation model** — Both sender and receiver must agree before ledger updates. This prevents false settlement claims which is a gap in tools like Splitwise.

4. **Graceful degradation** — If Redis goes down, app continues with direct DB calls. If fraud API is unreachable, transaction is allowed with a safe fallback. No single point of failure.

5. **Role migration** — Existing USER roles were migrated to ANALYST on schema update, preserving all existing user permissions.

6. **Analysis is self-computed** — Financial insights are computed entirely from the ledger and personal expense data. No external AI dependency.

---

## Database Schema

Key tables:
- `users` — user accounts with role and active status
- `groups` — expense sharing groups
- `group_members` — group membership with roles
- `expenses` — group expenses with split type
- `expense_splits` — individual split amounts
- `ledger_entries` — append-only financial ledger (source of truth)
- `settlements` — group debt settlements
- `transfers` — peer to peer transfers
- `personal_expenses` — individual expense tracking
- `scam_reports` — community fraud reporting
- `friendships` — friend relationships

Schema is version controlled via Flyway migrations (V1 through V10).

---

**API Version:** 2.0.0
**Last Updated:** April 2026
**Documentation:** `https://zorvyn-wuxb.onrender.com/swagger-ui.html`
