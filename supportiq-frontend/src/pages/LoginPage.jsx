import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import FormInput from '../components/FormInput';
import Alert from '../components/Alert';
import Spinner from '../components/Spinner';
import { Mail, Lock, Bot, ArrowRight } from 'lucide-react';

/**
 * LoginPage Component
 * 
 * Handles user authentication via email & password.
 * Redirects dynamically based on user role upon successful login:
 * - ADMIN -> /admin
 * - CUSTOMER -> /chat
 */
const LoginPage = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [formData, setFormData] = useState({
    email: '',
    password: '',
  });

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successNotice, setSuccessNotice] = useState(
    location.state?.successMessage || ''
  );

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    if (errorMessage) setErrorMessage('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.email || !formData.password) {
      setErrorMessage('Please fill in both email and password.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage('');
    setSuccessNotice('');

    try {
      const data = await login(formData.email.trim(), formData.password);
      const role = data.user?.role;

      // Redirect by user role
      if (role === 'ADMIN') {
        navigate('/admin', { replace: true });
      } else {
        navigate('/chat', { replace: true });
      }
    } catch (err) {
      // Backend returns: { timestamp, status, error, message, path }
      const backendMessage =
        err.response?.data?.message ||
        'Unable to log in. Please check your credentials and try again.';
      setErrorMessage(backendMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-50 via-indigo-50/20 to-slate-100 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      {/* Brand Header */}
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="inline-flex w-14 h-14 rounded-2xl bg-gradient-to-tr from-indigo-600 to-indigo-500 items-center justify-center text-white shadow-xl shadow-indigo-200 mb-4 transform hover:scale-105 transition-transform">
          <Bot className="w-8 h-8" />
        </div>
        <h1 className="text-3xl font-extrabold text-slate-900 tracking-tight">
          Welcome to Support<span className="text-indigo-600">IQ</span>
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          Sign in to access your intelligent customer support dashboard
        </p>
      </div>

      {/* Main Login Card */}
      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4 sm:px-0">
        <div className="bg-white py-8 px-6 sm:px-10 shadow-xl shadow-slate-200/60 rounded-2xl border border-slate-200/80">
          {successNotice && (
            <Alert
              type="success"
              message={successNotice}
              onClose={() => setSuccessNotice('')}
              className="mb-5"
            />
          )}

          {errorMessage && (
            <Alert
              type="error"
              message={errorMessage}
              onClose={() => setErrorMessage('')}
              className="mb-5"
            />
          )}

          <form onSubmit={handleSubmit} className="space-y-5" noValidate>
            <FormInput
              id="login-email"
              name="email"
              type="email"
              label="Email Address"
              placeholder="you@example.com"
              value={formData.email}
              onChange={handleChange}
              required
              autoComplete="email"
              icon={Mail}
              disabled={isSubmitting}
            />

            <FormInput
              id="login-password"
              name="password"
              type="password"
              label="Password"
              placeholder="••••••••"
              value={formData.password}
              onChange={handleChange}
              required
              autoComplete="current-password"
              icon={Lock}
              disabled={isSubmitting}
            />

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full flex items-center justify-center gap-2 py-3 px-4 border border-transparent rounded-xl shadow-md shadow-indigo-200 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-60 disabled:cursor-not-allowed transition-all duration-150 cursor-pointer"
            >
              {isSubmitting ? (
                <>
                  <Spinner size="sm" className="text-white" />
                  <span>Signing in...</span>
                </>
              ) : (
                <>
                  <span>Sign In</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          {/* Quick Demo Credentials helper */}
          <div className="mt-6 pt-5 border-t border-slate-100 bg-slate-50/70 -mx-6 -mb-8 px-6 py-4 rounded-b-2xl text-xs text-slate-500">
            <span className="font-semibold text-slate-700 block mb-1">Demo Accounts:</span>
            <div className="flex flex-col gap-1 text-[11px]">
              <div>
                <span className="font-medium text-slate-600">Admin:</span> admin@supportiq.com / Admin@12345
              </div>
              <div>
                <span className="font-medium text-slate-600">Customer:</span> Register a new account below
              </div>
            </div>
          </div>
        </div>

        {/* Footer Navigation Link */}
        <p className="mt-6 text-center text-sm text-slate-600">
          Don&apos;t have an account?{' '}
          <Link
            to="/register"
            className="font-semibold text-indigo-600 hover:text-indigo-500 underline underline-offset-4 focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded"
          >
            Create an account
          </Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
