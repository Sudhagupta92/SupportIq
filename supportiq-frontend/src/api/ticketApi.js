import api from './axios';

/**
 * Creates a support ticket from customer form or escalation modal.
 * @param {Object} payload { subject, description, category, priority, relatedOrderNumber }
 * @returns {Promise<Object>} Created ticket or existing ticket with existing=true flag
 */
export const createTicketApi = async (payload) => {
  const response = await api.post('/api/tickets', payload);
  return response.data;
};

/**
 * Fetches paginated tickets for the logged-in customer.
 * @param {number} page
 * @param {number} size
 * @returns {Promise<Object>} PageResponse<TicketResponse>
 */
export const getMyTicketsApi = async (page = 0, size = 10) => {
  const response = await api.get('/api/tickets', {
    params: { page, size },
  });
  return response.data;
};

/**
 * Fetches ticket detail and message thread for customer.
 * @param {string} ticketNumber
 * @returns {Promise<Object>} TicketDetailResponse
 */
export const getMyTicketApi = async (ticketNumber) => {
  const response = await api.get(`/api/tickets/${encodeURIComponent(ticketNumber)}`);
  return response.data;
};

/**
 * Posts a customer follow-up message to the ticket thread.
 * @param {string} ticketNumber
 * @param {string} message
 * @returns {Promise<Object>} TicketDetailResponse
 */
export const addCustomerMessageApi = async (ticketNumber, message) => {
  const response = await api.post(`/api/tickets/${encodeURIComponent(ticketNumber)}/messages`, {
    message,
  });
  return response.data;
};

/**
 * Admin: Fetches paginated tickets across all users with filters.
 * @param {Object} params { page, size, status, priority, category, search }
 * @returns {Promise<Object>} PageResponse<AdminTicketResponse>
 */
export const getAdminTicketsApi = async ({ page = 0, size = 10, status, priority, category, search } = {}) => {
  const params = { page, size };
  if (status) params.status = status;
  if (priority) params.priority = priority;
  if (category) params.category = category;
  if (search && search.trim()) params.search = search.trim();

  const response = await api.get('/api/admin/tickets', { params });
  return response.data;
};

/**
 * Admin: Fetches complete ticket details including customer name/email.
 * @param {string} ticketNumber
 * @returns {Promise<Object>} AdminTicketDetailResponse
 */
export const getAdminTicketApi = async (ticketNumber) => {
  const response = await api.get(`/api/admin/tickets/${encodeURIComponent(ticketNumber)}`);
  return response.data;
};

/**
 * Admin: Posts an agent reply to a ticket thread.
 * @param {string} ticketNumber
 * @param {string} message
 * @returns {Promise<Object>} AdminTicketDetailResponse
 */
export const addAdminMessageApi = async (ticketNumber, message) => {
  const response = await api.post(`/api/admin/tickets/${encodeURIComponent(ticketNumber)}/messages`, {
    message,
  });
  return response.data;
};

/**
 * Admin: Updates ticket lifecycle status adhering to transition rules.
 * @param {string} ticketNumber
 * @param {string} status (OPEN, IN_PROGRESS, RESOLVED, CLOSED)
 * @returns {Promise<Object>} AdminTicketDetailResponse
 */
export const updateAdminTicketStatusApi = async (ticketNumber, status) => {
  const response = await api.patch(`/api/admin/tickets/${encodeURIComponent(ticketNumber)}/status`, {
    status,
  });
  return response.data;
};
