import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import PublicRoute from './components/PublicRoute';
import Spinner from './components/Spinner';

// Pages
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ChatPage from './pages/ChatPage';
import AdminDashboardPage from './pages/AdminDashboardPage';
import CustomerTicketsPage from './pages/CustomerTicketsPage';
import CustomerTicketDetailPage from './pages/CustomerTicketDetailPage';
import AdminTicketsPage from './pages/AdminTicketsPage';
import AdminTicketDetailPage from './pages/AdminTicketDetailPage';
import ForbiddenPage from './pages/ForbiddenPage';
import NotFoundPage from './pages/NotFoundPage';

/**
 * RootRedirect Component
 * 
 * Directs traffic from root path ('/'):
 * - Unauthenticated users -> /login
 * - Authenticated ADMIN users -> /admin
 * - Authenticated CUSTOMER users -> /chat
 */
const RootRedirect = () => {
  const { user, token, loading } = useAuth();

  if (loading) {
    return <Spinner fullScreen text="Loading SupportIQ..." />;
  }

  if (!token || !user) {
    return <Navigate to="/login" replace />;
  }

  return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/chat'} replace />;
};

/**
 * SupportIQ App Routes
 */
function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Root Redirect */}
          <Route path="/" element={<RootRedirect />} />

          {/* Public Authentication Routes */}
          <Route
            path="/login"
            element={
              <PublicRoute>
                <LoginPage />
              </PublicRoute>
            }
          />
          <Route
            path="/register"
            element={
              <PublicRoute>
                <RegisterPage />
              </PublicRoute>
            }
          />

          {/* Authenticated Protected Routes */}
          <Route
            path="/chat"
            element={
              <ProtectedRoute>
                <ChatPage />
              </ProtectedRoute>
            }
          />

          {/* Customer Ticket Routes */}
          <Route
            path="/tickets"
            element={
              <ProtectedRoute>
                <CustomerTicketsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/tickets/:ticketNumber"
            element={
              <ProtectedRoute>
                <CustomerTicketDetailPage />
              </ProtectedRoute>
            }
          />

          {/* Admin Protected Routes (Restricted to ADMIN role only) */}
          <Route
            path="/admin"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/tickets"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminTicketsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/tickets/:ticketNumber"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminTicketDetailPage />
              </ProtectedRoute>
            }
          />

          {/* Explicit Forbidden Page */}
          <Route path="/forbidden" element={<ForbiddenPage />} />

          {/* Catch-all 404 Route */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
