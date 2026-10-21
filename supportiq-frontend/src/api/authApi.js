import api from './axios';

/**
 * Registers a new customer account.
 * 
 * @param {Object} data - { name, email, password }
 * @returns {Promise<Object>} Safe user response { id, name, email, role, createdAt }
 */
export const registerApi = async ({ name, email, password }) => {
  const response = await api.post('/api/auth/register', { name, email, password });
  return response.data;
};

/**
 * Authenticates user credentials.
 * 
 * @param {Object} data - { email, password }
 * @returns {Promise<Object>} { token, tokenType, user: { id, name, email, role } }
 */
export const loginApi = async ({ email, password }) => {
  const response = await api.post('/api/auth/login', { email, password });
  return response.data;
};

/**
 * Retrieves the currently authenticated user's profile using the stored Bearer token.
 * 
 * @returns {Promise<Object>} { id, name, email, role, createdAt }
 */
export const getMeApi = async () => {
  const response = await api.get('/api/auth/me');
  return response.data;
};

/**
 * Verifies admin role access against the backend ping endpoint.
 * Restricted to users possessing the ADMIN role.
 * 
 * @returns {Promise<Object>} { message, status, role }
 */
export const pingAdminApi = async () => {
  const response = await api.get('/api/admin/ping');
  return response.data;
};
