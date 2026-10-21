# SupportIQ Backend - Module 1: Foundation & Authentication

SupportIQ is an AI-powered customer support agent platform for e-commerce. **Module 1** establishes the core backend foundation, PostgreSQL persistence with pgvector, Spring Security 6, and stateless JWT-based authentication.

---

## 🛠️ Tech Stack & Requirements

- **Java**: 17+ (LTS)
- **Framework**: Spring Boot 3.3.x (Spring Web, Spring Security, Spring Data JPA, Bean Validation)
- **Database**: PostgreSQL 16 with `pgvector/pgvector:pg16`
- **Security & Tokens**: Spring Security 6 + JJWT 0.12.x (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- **Build Tool**: Maven

---

## 🚀 Step-by-Step Setup & Running

### Step 1: Start Database with Docker Compose

Ensure Docker Desktop or Docker daemon is running on your machine, then run:

```bash
# Navigate to the project root
cd supportiq-backend

# Start the pgvector PostgreSQL container in detached mode
docker compose up -d
```

> **Database Details:**
> - **Host**: `localhost:5432`
> - **Database Name**: `supportiq`
> - **Username**: `postgres`
> - **Password**: `postgres`
> - **Volume**: `postgres_data` (persisted on disk)

### Step 2: Configure Environment Variables (Optional)

Sensible defaults are pre-configured in `src/main/resources/application.yml`. If you wish to override them:

| Variable | Description | Default Value |
| :--- | :--- | :--- |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/supportiq` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `JWT_SECRET` | Secret key for signing HMAC-SHA tokens (>= 32 chars) | Built-in 512-bit hex key |
| `JWT_EXPIRATION_MS`| JWT validity duration in milliseconds | `86400000` (24 hours) |
| `ADMIN_EMAIL` | Default administrator account email | `admin@supportiq.com` |
| `ADMIN_PASSWORD` | Default administrator account password | `Admin@12345` |

### Step 3: Build & Run the Spring Boot Application

Using Maven:

```bash
# Clean and compile
mvn clean install

# Run the Spring Boot application
mvn spring-boot:run
```

The application will start on **`http://localhost:8080`**.

> **Automatic Admin Seeding**: On the initial startup, `DataInitializer` automatically detects if an administrator account exists. If not, it creates `admin@supportiq.com` with password `Admin@12345` and role `ADMIN`.

---

## 📡 API Endpoints & Testing with cURL / Postman

### 1. Register a New Customer

- **Endpoint**: `POST /api/auth/register`
- **Access**: Public
- **Validation**:
  - `name`: Not blank, 2-100 characters
  - `email`: Valid email format
  - `password`: Minimum 8 characters
- **Security Rule**: Role is **always** assigned to `CUSTOMER`. Clients cannot escalate their role.

#### cURL Request:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Jane Doe",
    "email": "jane@example.com",
    "password": "Password123"
  }'
```

#### Success Response (`201 Created`):
```json
{
  "id": 1,
  "name": "Jane Doe",
  "email": "jane@example.com",
  "role": "CUSTOMER",
  "createdAt": "2026-10-21T00:15:30"
}
```

#### Duplicate Email Error (`409 Conflict`):
```json
{
  "timestamp": "2026-10-21T00:15:45",
  "status": 409,
  "error": "Conflict",
  "message": "Email is already registered: jane@example.com",
  "path": "/api/auth/register"
}
```

---

### 2. Login & Obtain JWT Token

- **Endpoint**: `POST /api/auth/login`
- **Access**: Public

#### cURL Request (Customer):
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane@example.com",
    "password": "Password123"
  }'
```

#### Success Response (`200 OK`):
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQ1VTVE9NRVIiLCJ1c2VySWQiOjEsInN1YiI6ImphbmVAZXhhbXBsZS5jb20iLCJpYXQiOjE3OTI1OTY5MzAsImV4cCI6MTc5MjY4MzMzMH0...",
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "name": "Jane Doe",
    "email": "jane@example.com",
    "role": "CUSTOMER",
    "createdAt": "2026-10-21T00:15:30"
  }
}
```

#### Bad Credentials Error (`401 Unauthorized`):
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane@example.com",
    "password": "WrongPassword!"
  }'
```

```json
{
  "timestamp": "2026-10-21T00:16:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password.",
  "path": "/api/auth/login"
}
```

---

### 3. Get Current User Profile (`/me`)

- **Endpoint**: `GET /api/auth/me`
- **Access**: Any authenticated user (Requires `Authorization: Bearer <token>`)

#### cURL Request:
```bash
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE"
```

#### Success Response (`200 OK`):
```json
{
  "id": 1,
  "name": "Jane Doe",
  "email": "jane@example.com",
  "role": "CUSTOMER",
  "createdAt": "2026-10-21T00:15:30"
}
```

#### Missing or Invalid Token Error (`401 Unauthorized`):
```json
{
  "timestamp": "2026-10-21T00:16:30",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource.",
  "path": "/api/auth/me"
}
```

---

### 4. Admin Health Ping (`/admin/ping`)

- **Endpoint**: `GET /api/admin/ping`
- **Access**: Requires `ADMIN` role

#### Step 4a: Login as Administrator:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@supportiq.com",
    "password": "Admin@12345"
  }'
```

#### Step 4b: Access Admin Ping with Admin Token:
```bash
curl -X GET http://localhost:8080/api/admin/ping \
  -H "Authorization: Bearer ADMIN_JWT_TOKEN_HERE"
```

#### Success Response (`200 OK`):
```json
{
  "message": "Admin access confirmed. Pong!",
  "status": "UP",
  "role": "ADMIN"
}
```

#### Forbidden Error (`403 Forbidden` - if requested with a CUSTOMER token):
```json
{
  "timestamp": "2026-10-21T00:17:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied: You do not have the necessary permissions to access this resource.",
  "path": "/api/admin/ping"
}
```

---

## 🧪 Running Automated Tests

The test suite runs with an isolated in-memory H2 database in PostgreSQL compatibility mode, meaning no live Docker service is required to execute tests.

```bash
mvn test
```

Tests verify:
1. Registration success with 201 status and password exclusion
2. Duplicate email attempts triggering 409 Conflict
3. Successful authentication returning valid Bearer JWT and user details
