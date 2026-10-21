# SupportIQ Frontend — Module 2 Base

SupportIQ is an AI-powered customer support application for modern e-commerce stores. **Module 2** provides the complete frontend architecture: authentication pages, session lifecycle management, Axios interceptors, responsive layout, and role-based routing.

> [!NOTE]
> This module sets up the frontend foundation and authentication. Chat, orders, tickets, and AI agent capabilities will be introduced in subsequent modules (Module 3 & Module 4).

---

## 🚀 Quick Start Guide

### 1. Prerequisites
- **Node.js**: v18+ (tested on Node v22)
- **SupportIQ Backend**: Running at `http://localhost:8080` (Module 1 Spring Boot backend)

### 2. Installation & Configuration

Open your terminal in the `supportiq-frontend` folder:

```bash
# 1. Install dependencies
npm install

# 2. Copy the example environment file to .env
cp .env.example .env
# On Windows PowerShell:
# Copy-Item .env.example .env

# 3. Start the development server
npm run dev
```

The frontend development server will start at:
👉 **`http://localhost:5173`**

*(Backend CORS is pre-configured to allow requests from `http://localhost:5173`).*

---

## 🛠️ Tech Stack & Libraries

- **Framework**: React 19 with Vite (JavaScript)
- **Styling**: Tailwind CSS v4
- **Routing**: `react-router-dom` (v7)
- **HTTP Client**: `axios`
- **Icons**: `lucide-react`
- **Linter**: `oxlint`

---

## 📂 Project Architecture

```
supportiq-frontend/
├── .env.example                <- Sample environment configuration
├── .env                        <- Active environment variables (VITE_API_URL)
├── index.html                  <- HTML template with responsive viewport & meta tags
├── package.json                <- Project dependencies and build scripts
├── vite.config.js              <- Vite configuration (port 5173, Tailwind CSS plugin)
├── docs/
│   └── MODULE2_EXPLANATION.md  <- Beginner-friendly deep dive into AuthContext & Interceptors
└── src/
    ├── api/
    │   ├── axios.js            <- Configured Axios instance with request/response interceptors
    │   └── authApi.js          <- Authentication and Admin API caller functions
    ├── context/
    │   ├── AuthContext.jsx     <- Global AuthProvider (user, token, login, register, logout)
    │   └── useAuth.js          <- Custom hook for accessing AuthContext
    ├── components/
    │   ├── Alert.jsx           <- Reusable alert banner (error, success, warning, info)
    │   ├── FormInput.jsx       <- Accessible form input with field errors & password toggle
    │   ├── Navbar.jsx          <- Responsive header with role badges & mobile drawer
    │   ├── ProtectedRoute.jsx  <- Route guard with authentication & RBAC checks
    │   ├── PublicRoute.jsx     <- Public-only route wrapper (redirects logged-in users)
    │   └── Spinner.jsx         <- Loading indicator (inline and full-screen modes)
    ├── pages/
    │   ├── LoginPage.jsx       <- Login view with email/password and role redirection
    │   ├── RegisterPage.jsx    <- Registration view with client validation & 409 handling
    │   ├── ChatPage.jsx        <- Customer/Admin landing placeholder ("Chat coming in Module 4")
    │   ├── AdminDashboardPage.jsx <- Admin-only placeholder with live /api/admin/ping test
    │   ├── ForbiddenPage.jsx   <- 403 Access Denied view
    │   └── NotFoundPage.jsx    <- 404 Catch-all view
    ├── App.jsx                 <- Application route definitions
    ├── main.jsx                <- React DOM entry point
    └── index.css               <- Tailwind CSS root imports and base styles
```

---

## 🌐 Environment Variables

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `VITE_API_URL` | `http://localhost:8080` | Base URL of the SupportIQ Spring Boot backend |

---

## 🔒 Security & Quality Principles

1. **XSS & Token Storage Trade-off**:
   - In this learning project, the JWT token is stored in browser `localStorage` for accessibility and straightforward inspection.
   - *Production Trade-off*: Storing tokens in `localStorage` makes them susceptible to Cross-Site Scripting (XSS) if malicious third-party scripts execute. In high-security production deployments, **`httpOnly`, `Secure`, `SameSite` cookies** should be used so that JavaScript cannot access the token.
2. **Credential Sanitization**:
   - Passwords and JWT tokens are **never** logged to the console.
3. **Global 401 Interceptor**:
   - An expired or revoked token automatically triggers credential purging and redirects to `/login`.
   - `/api/auth/login` and `/api/auth/register` are explicitly bypassed from the 401 redirect so that invalid credentials can display an inline error message on the form.
4. **Resilient LocalStorage**:
   - All `localStorage` interactions are guarded in `try / catch` blocks to gracefully handle private-browsing storage limits.

---

## ✅ Comprehensive Manual Testing Checklist

Use this checklist to verify that all Module 2 features function as expected:

### 1. Registration Flow (`/register`)
- [ ] **Empty Form Submission**: Click "Create Account" with blank fields. Confirm client-side validation errors appear beneath each required field.
- [ ] **Invalid Email**: Enter `invalid-email` and blur the field. Verify "Please enter a valid email address." displays.
- [ ] **Short Password**: Enter a password shorter than 8 characters (e.g. `pass`). Verify "Password must be at least 8 characters long." displays.
- [ ] **Password Mismatch**: Enter different passwords in "Password" and "Confirm Password". Verify "Passwords do not match." displays.
- [ ] **Duplicate Email (409 Conflict)**: Register with `admin@supportiq.com`. Verify the form displays: *"This email is already registered."*
- [ ] **Successful Registration (201 Created)**: Register with a new email (e.g. `customer1@example.com`). Verify a green success alert appears and the browser redirects to `/login` with a confirmation banner.

### 2. Login Flow (`/login`)
- [ ] **Wrong Password (401 Unauthorized)**: Enter `admin@supportiq.com` and `WrongPassword!`. Verify the page stays on `/login` and displays the backend error message (e.g. *"Invalid email or password."*).
- [ ] **Password Visibility Toggle**: Click the eye icon next to the password input. Verify the password toggles between masked dots and readable plaintext.
- [ ] **Customer Login**: Sign in with customer credentials. Confirm redirection to `/chat`.
- [ ] **Admin Login**: Sign in with `admin@supportiq.com` / `Admin@12345`. Confirm redirection to `/admin`.

### 3. Role-Based Access Control & Routing
- [ ] **Customer accessing `/admin`**: While logged in as a CUSTOMER, manually navigate to `http://localhost:5173/admin`. Verify you are shown the **403 Forbidden Page** (`ForbiddenPage`) with a button to return to `/chat`.
- [ ] **Logged-out user accessing `/admin` or `/chat`**: In an incognito window, navigate to `/chat` or `/admin`. Verify immediate redirection to `/login`.
- [ ] **Logged-in user accessing `/login` or `/register`**: While logged in, try visiting `/login`. Verify immediate redirection back to `/chat` (or `/admin` if Admin).
- [ ] **Admin Live API Verification**: While logged in as Admin on `/admin`, click **"Verify Admin Ping API"**. Confirm a green success banner displays the live response: `"Admin access confirmed. Pong! (Role: ADMIN, Status: UP)"`.
- [ ] **Root Route (`/`)**: Visit `http://localhost:5173/`. If logged in as customer, redirects to `/chat`; if admin, redirects to `/admin`; if logged out, redirects to `/login`.
- [ ] **404 Catch-All**: Navigate to `http://localhost:5173/non-existent-page`. Confirm the **404 Not Found Page** displays with a button to return home.

### 4. Navbar & Session Lifecycle
- [ ] **Navbar Details**: Verify the logged-in user's name and role badge (`CUSTOMER` or `ADMIN`) appear in the navigation bar.
- [ ] **Admin Link Visibility**: Confirm the "Admin" nav link only appears for users with the `ADMIN` role.
- [ ] **Mobile Responsiveness**: Resize the browser window to mobile width (<768px). Confirm the hamburger icon appears and opens the drawer with navigation links, user details, and logout button.
- [ ] **Session Persistence**: Refresh the browser page (`F5`). Verify the loading spinner displays briefly while `GET /api/auth/me` verifies the token, and the session remains intact without logging out.
- [ ] **Logout**: Click the "Logout" button. Confirm the session token is removed from storage and you are redirected to `/login`.
