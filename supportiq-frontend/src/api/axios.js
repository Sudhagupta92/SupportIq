import axios from 'axios';

// Resolve base URL from environment variable, falling back to local backend port
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const TOKEN_STORAGE_KEY = 'supportiq_token';
export const USER_STORAGE_KEY = 'supportiq_user';

/**
 * Safe localStorage reader with error handling.
 * Wrapped in try/catch to gracefully handle restricted browser environments (e.g. private browsing or disabled cookies).
 */
export const getStoredToken = () => {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
};

/**
 * Safe localStorage cleaner for tokens and cached user data.
 */
export const clearStoredAuth = () => {
  try {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
  } catch {
    // Gracefully handle storage write restrictions
  }
};

// Create a pre-configured Axios instance for SupportIQ
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

/**
 * Request Interceptor
 * 
 * WHY:
 * Automatically injects the JWT 'Authorization: Bearer <token>' header on every outgoing HTTP request
 * if a token exists in storage. This allows our backend's JwtAuthenticationFilter to verify the user's
 * identity and role (CUSTOMER vs ADMIN) for protected resources without re-prompting the user.
 */
api.interceptors.request.use(
  (config) => {
    const token = getStoredToken();
    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

/**
 * Response Interceptor
 * 
 * WHY:
 * Handles 401 Unauthorized responses centrally across the whole application.
 * 
 * CRITICAL EXCLUSION:
 * We explicitly bypass 401 handling for '/api/auth/login' and '/api/auth/register'.
 * When a user provides wrong credentials on the login page, the backend returns a 401. If we redirected
 * on that 401, the browser would reload/redirect, immediately discarding the validation error message
 * ("Invalid email or password.") before the user can see what went wrong.
 * 
 * For all OTHER endpoints, a 401 indicates that the token has expired, been revoked, or is malformed.
 * In those cases, we safely clear stored credentials and redirect the user back to '/login' to re-authenticate.
 */
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      const requestUrl = error.config?.url || '';
      const isAuthEndpoint =
        requestUrl.includes('/api/auth/login') ||
        requestUrl.includes('/api/auth/register');

      if (!isAuthEndpoint) {
        clearStoredAuth();

        // Redirect to /login if not already on the login or register page
        if (
          typeof window !== 'undefined' &&
          !window.location.pathname.startsWith('/login') &&
          !window.location.pathname.startsWith('/register')
        ) {
          window.location.href = '/login';
        }
      }
    }
    return Promise.reject(error);
  }
);

export default api;
