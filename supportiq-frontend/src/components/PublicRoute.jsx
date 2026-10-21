import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Spinner from './Spinner';

/**
 * PublicRoute Component
 * 
 * WHY:
 * Protects public-only pages like /login and /register from already-authenticated users.
 * If a logged-in user visits /login or /register, they are redirected to their appropriate landing page:
 * - ADMIN -> /admin
 * - CUSTOMER -> /chat
 */
const PublicRoute = ({ children }) => {
  const { user, token, loading } = useAuth();

  if (loading) {
    return <Spinner fullScreen text="Checking session..." />;
  }

  if (token && user) {
    const destination = user.role === 'ADMIN' ? '/admin' : '/chat';
    return <Navigate to={destination} replace />;
  }

  return children;
};

export default PublicRoute;
