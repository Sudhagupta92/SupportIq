import api from './axios';

/**
 * Uploads a policy PDF document to the backend for ingestion and vectorization.
 * 
 * @param {File} file - The PDF file selected by the administrator
 * @param {Function} [onUploadProgress] - Optional Axios progress callback for tracking upload progress
 * @returns {Promise<Object>} PolicyDocumentResponse { id, filename, contentType, sizeBytes, pageCount, chunkCount, status, createdAt }
 */
export const uploadDocumentApi = async (file, onUploadProgress) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await api.post('/api/admin/documents', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    onUploadProgress,
  });
  return response.data;
};

/**
 * Fetches all policy documents, ordered newest first, with ingestion status and chunk count.
 * 
 * @returns {Promise<Array<Object>>} List of PolicyDocumentResponse objects
 */
export const getDocumentsApi = async () => {
  const response = await api.get('/api/admin/documents');
  return response.data;
};

/**
 * Deletes a policy document metadata row and all associated vector chunks from the vector store.
 * 
 * @param {number|string} id - The ID of the policy document to delete
 * @returns {Promise<void>}
 */
export const deleteDocumentApi = async (id) => {
  await api.delete(`/api/admin/documents/${id}`);
};

/**
 * Tests similarity search against indexed vector chunks.
 * 
 * @param {string} query - The search query
 * @param {number} [topK=4] - Top K results to retrieve (between 1 and 10)
 * @returns {Promise<Array<Object>>} List of RagSearchResultResponse objects
 */
export const searchDocumentsApi = async (query, topK = 4) => {
  const response = await api.post('/api/admin/documents/search', {
    query,
    topK,
  });
  return response.data;
};
