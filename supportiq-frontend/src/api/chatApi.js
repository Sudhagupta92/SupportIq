import api from './axios';

/**
 * Sends a customer message to the SupportIQ RAG Chat API.
 * 
 * @param {string} message The customer query text (max 1000 characters).
 * @returns {Promise<{ answer: string, answered: boolean, sources: Array<{ filename: string, pageNumber: number, score: number }> }>}
 */
export const sendChatMessageApi = async (message) => {
  const response = await api.post('/api/chat', { message });
  return response.data;
};
