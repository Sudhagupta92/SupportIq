import React, { createContext, useState, useEffect, useCallback } from 'react';
import { loginApi, registerApi, getMeApi } from '../api/authApi';
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY, clearStoredAuth } from '../api/axios';

/**
 * SECURITY NOTE ON TOKEN STORAGE:
 * In this learning project, we store the JWT in browser localStorage for ease of setup and inspection.
 * Trade-off: Storing tokens in localStorage leaves them accessible to JavaScript running on the page,
 * which makes them vulnerable to Cross-Site Scripting (XSS) attacks.
 * In a hardened production architecture, httpOnly, Secure, SameSite cookies are the industry-standard
 * choice because they cannot be read by JavaScript, protecting authentication credentials from XSS exfiltration.
 */

const AuthContext = createContext(null);

/**
 * AuthProvider Component
 * 
 * WHY:
 * Provides a single, centralized source of truth for the user's authentication state,
 * active token, and session lifecycle. Any component in the React tree (Navbar, ProtectedRoute,
 * Login/Register pages) can consume this context via useAuth() without prop-drilling.
 */
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(() => {
    try {
      return localStorage.getItem(TOKEN_STORAGE_KEY);
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(true);

  /**
   * Session Restoration on Mount
   * 
   * WHY:
   * When a user refreshes the page or opens a new tab, the in-memory React state is reset.
   * If a token exists in localStorage, we contact the backend ('/api/auth/me') to confirm the token
   * is still valid and fetch the freshest user record (including role).
   * If the token has expired or is invalid, we clear storage and reset auth state.
   */
  useEffect(() => {
    let isMounted = true;

    const restoreSession = async () => {
      let storedToken = null;
      try {
        storedToken = localStorage.getItem(TOKEN_STORAGE_KEY);
      } catch {
        storedToken = null;
      }

      if (!storedToken) {
        if (isMounted) setLoading(false);
        return;
      }

      try {
        const userData = await getMeApi();
        if (isMounted) {
          setUser(userData);
          setToken(storedToken);
          try {
            localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(userData));
          } catch {
            // Storage quota or restriction ignored
          }
        }
      } catch {
        // Token is invalid, expired, or backend rejected it
        clearStoredAuth();
        if (isMounted) {
          setUser(null);
          setToken(null);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    restoreSession();

    return () => {
      isMounted = false;
    };
  }, []);

  /**
   * Log in user with credentials and persist session.
   * NOTE: Never log the password or token to console.
   */
  const login = useCallback(async (email, password) => {
    const data = await loginApi({ email, password });
    const { token: receivedToken, user: receivedUser } = data;

    try {
      localStorage.setItem(TOKEN_STORAGE_KEY, receivedToken);
      if (receivedUser) {
        localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(receivedUser));
      }
    } catch {
      // Graceful fallback if storage is restricted
    }

    setToken(receivedToken);
    setUser(receivedUser);
    return data;
  }, []);

  /**
   * Register a new customer account.
   */
  const register = useCallback(async (name, email, password) => {
    return await registerApi({ name, email, password });
  }, []);

  /**
   * Log out user, purge storage, and reset in-memory state.
   */
  const logout = useCallback(() => {
    clearStoredAuth();
    setToken(null);
    setUser(null);
  }, []);

  const value = {
    user,
    token,
    loading,
    login,
    register,
    logout,
    isAuthenticated: Boolean(token && user),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export { AuthContext };
export { useAuth } from './useAuth';
export default AuthContext;
