import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import FormInput from '../components/FormInput';
import Alert from '../components/Alert';
import Spinner from '../components/Spinner';
import { User, Mail, Lock, Bot, UserPlus } from 'lucide-react';

/**
 * RegisterPage Component
 * 
 * Implements client-side validation for customer registration:
 * - Name required
 * - Valid email format
 * - Password minimum 8 characters
 * - Passwords match
 * Displays field-level errors, handles 409 duplicate email error,
 * and navigates to /login upon 201 Created.
 */
const RegisterPage = () => {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
  });

  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Email format validation regex
  const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  /**
   * Validate a single field or all fields
   */
  const validateField = (name, value, currentFormData = formData) => {
    let error = '';

    switch (name) {
      case 'name':
        if (!value.trim()) {
          error = 'Name is required.';
        }
        break;
      case 'email':
        if (!value.trim()) {
          error = 'Email is required.';
        } else if (!EMAIL_REGEX.test(value.trim())) {
          error = 'Please enter a valid email address.';
        }
        break;
      case 'password':
        if (!value) {
          error = 'Password is required.';
        } else if (value.length < 8) {
          error = 'Password must be at least 8 characters long.';
        }
        break;
      case 'confirmPassword':
        if (!value) {
          error = 'Please confirm your password.';
        } else if (value !== currentFormData.password) {
          error = 'Passwords do not match.';
        }
        break;
      default:
        break;
    }

    return error;
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    const updatedForm = { ...formData, [name]: value };
    setFormData(updatedForm);

    // Clear general error when user modifies input
    if (generalError) setGeneralError('');

    // Clear or update field error on change
    if (fieldErrors[name]) {
      const err = validateField(name, value, updatedForm);
      setFieldErrors((prev) => ({ ...prev, [name]: err }));
    }

    // Re-check confirmPassword if password changes
    if (name === 'password' && formData.confirmPassword) {
      const confirmErr = formData.confirmPassword === value ? '' : 'Passwords do not match.';
      setFieldErrors((prev) => ({ ...prev, confirmPassword: confirmErr }));
    }
  };

  const handleBlur = (e) => {
    const { name, value } = e.target;
    const err = validateField(name, value, formData);
    setFieldErrors((prev) => ({ ...prev, [name]: err }));
  };

  const validateAll = () => {
    const errors = {};
    Object.keys(formData).forEach((key) => {
      const err = validateField(key, formData[key], formData);
      if (err) errors[key] = err;
    });
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateAll()) {
      return;
    }

    setIsSubmitting(true);
    setGeneralError('');
    setSuccessMessage('');

    try {
      await register(formData.name.trim(), formData.email.trim(), formData.password);

      // On 201 Created: show success message and redirect to /login
      setSuccessMessage('Registration successful! Redirecting to login...');
      setTimeout(() => {
        navigate('/login', {
          replace: true,
          state: {
            successMessage: 'Account created successfully! You can now sign in.',
          },
        });
      }, 1200);
    } catch (err) {
      // Backend status handling
      const status = err.response?.status;
      const data = err.response?.data;

      if (status === 409) {
        // Required 409 specific message
        setGeneralError('This email is already registered.');
        setFieldErrors((prev) => ({
          ...prev,
          email: 'This email is already registered.',
        }));
      } else if (data?.validationErrors) {
        // Map backend validation errors directly to fields
        setFieldErrors((prev) => ({ ...prev, ...data.validationErrors }));
        setGeneralError(data.message || 'Please correct the highlighted errors.');
      } else {
        setGeneralError(
          data?.message || 'Registration failed. Please try again later.'
        );
      }
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
          Create Your Account
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          Join SupportIQ to experience AI-powered customer assistance
        </p>
      </div>

      {/* Main Register Card */}
      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4 sm:px-0">
        <div className="bg-white py-8 px-6 sm:px-10 shadow-xl shadow-slate-200/60 rounded-2xl border border-slate-200/80">
          {successMessage && (
            <Alert
              type="success"
              message={successMessage}
              className="mb-5"
            />
          )}

          {generalError && (
            <Alert
              type="error"
              message={generalError}
              onClose={() => setGeneralError('')}
              className="mb-5"
            />
          )}

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <FormInput
              id="register-name"
              name="name"
              type="text"
              label="Full Name"
              placeholder="Jane Doe"
              value={formData.name}
              onChange={handleChange}
              onBlur={handleBlur}
              error={fieldErrors.name}
              required
              autoComplete="name"
              icon={User}
              disabled={isSubmitting}
            />

            <FormInput
              id="register-email"
              name="email"
              type="email"
              label="Email Address"
              placeholder="jane@example.com"
              value={formData.email}
              onChange={handleChange}
              onBlur={handleBlur}
              error={fieldErrors.email}
              required
              autoComplete="email"
              icon={Mail}
              disabled={isSubmitting}
            />

            <FormInput
              id="register-password"
              name="password"
              type="password"
              label="Password"
              placeholder="Minimum 8 characters"
              value={formData.password}
              onChange={handleChange}
              onBlur={handleBlur}
              error={fieldErrors.password}
              required
              autoComplete="new-password"
              icon={Lock}
              disabled={isSubmitting}
            />

            <FormInput
              id="register-confirmPassword"
              name="confirmPassword"
              type="password"
              label="Confirm Password"
              placeholder="Re-type your password"
              value={formData.confirmPassword}
              onChange={handleChange}
              onBlur={handleBlur}
              error={fieldErrors.confirmPassword}
              required
              autoComplete="new-password"
              icon={Lock}
              disabled={isSubmitting}
            />

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full mt-2 flex items-center justify-center gap-2 py-3 px-4 border border-transparent rounded-xl shadow-md shadow-indigo-200 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-60 disabled:cursor-not-allowed transition-all duration-150 cursor-pointer"
            >
              {isSubmitting ? (
                <>
                  <Spinner size="sm" className="text-white" />
                  <span>Creating account...</span>
                </>
              ) : (
                <>
                  <UserPlus className="w-4 h-4" />
                  <span>Create Account</span>
                </>
              )}
            </button>
          </form>
        </div>

        {/* Footer Navigation Link */}
        <p className="mt-6 text-center text-sm text-slate-600">
          Already have an account?{' '}
          <Link
            to="/login"
            className="font-semibold text-indigo-600 hover:text-indigo-500 underline underline-offset-4 focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded"
          >
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterPage;
