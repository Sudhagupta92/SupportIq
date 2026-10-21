import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Spinner from './Spinner';
import ForbiddenPage from '../pages/ForbiddenPage';

/**
 * ProtectedRoute Component
 * 
 * WHY:
 * Acts as a client-side security barrier before rendering private views.
 * 1. Checks if the session is still loading from localStorage/backend. If so, displays a loading spinner.
 * 2. Checks if an authenticated user session exists. If unauthenticated, redirects to '/login' while saving the attempted path.
 * 3. Enforces Role-Based Access Control (RBAC): If the route requires specific roles (e.g. ['ADMIN']) and the logged-in
 *    user lacks that role, it displays the ForbiddenPage (403) instead of rendering the private view.
 * 
 * @param {Array<string>} [allowedRoles] - Optional list of authorized roles (e.g. ['ADMIN', 'CUSTOMER'])
 * @param {React.ReactNode} children - Route component to render when authorized
 */
const ProtectedRoute = ({ allowedRoles = null, children }) => {
  const { user, token, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <Spinner fullScreen text="Verifying session..." />;
  }

  // If user is not logged in, redirect to login page
  if (!token || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // If role restriction exists, verify user's role
  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <ForbiddenPage />;
  }

  return children;
};

export default ProtectedRoute;
