import React, { useState } from 'react';
import {
  FileText,
  Trash2,
  RefreshCw,
  CheckCircle2,
  Layers,
  BookOpen,
  AlertCircle
} from 'lucide-react';
import Spinner from '../Spinner';
import Alert from '../Alert';
import { deleteDocumentApi } from '../../api/documentApi';

/**
 * Formats file size in bytes into human-readable string.
 */
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
};

/**
 * Formats ISO date string into friendly readable format.
 */
const formatDate = (isoString) => {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    return date.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoString;
  }
};

/**
 * StatusBadge Component
 */
const StatusBadge = ({ status, errorMessage }) => {
  const [showErrorTooltip, setShowErrorTooltip] = useState(false);

  switch (status) {
    case 'READY':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200">
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
          READY
        </span>
      );
    case 'PROCESSING':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-50 text-blue-700 ring-1 ring-blue-200">
          <Spinner size="sm" className="text-blue-600" />
          PROCESSING
        </span>
      );
    case 'FAILED':
      return (
        <div className="relative inline-flex items-center">
          <button
            type="button"
            onClick={() => setShowErrorTooltip(!showErrorTooltip)}
            onMouseEnter={() => setShowErrorTooltip(true)}
            onMouseLeave={() => setShowErrorTooltip(false)}
            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-red-50 text-red-700 ring-1 ring-red-200 hover:bg-red-100 transition-colors cursor-pointer"
            title={errorMessage || 'Ingestion failed'}
          >
            <AlertCircle className="w-3.5 h-3.5 text-red-600" />
            FAILED
          </button>
          {showErrorTooltip && errorMessage && (
            <div className="absolute z-20 bottom-full mb-1 left-0 w-64 p-2.5 bg-slate-900 text-white text-[11px] rounded-lg shadow-xl leading-relaxed">
              <p className="font-bold text-red-300 mb-0.5">Failure Reason:</p>
              <p className="break-words">{errorMessage}</p>
            </div>
          )}
        </div>
      );
    default:
      return (
        <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-700">
          {status}
        </span>
      );
  }
};

/**
 * DocumentListTable Component
 */
const DocumentListTable = ({
  documents = [],
  isLoading = false,
  error = null,
  onRefresh,
  onDocumentDeleted,
}) => {
  const [deletingId, setDeletingId] = useState(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [deleteError, setDeleteError] = useState('');

  const handleDelete = async (id) => {
    setDeletingId(id);
    setDeleteError('');
    try {
      await deleteDocumentApi(id);
      setConfirmDeleteId(null);
      if (onDocumentDeleted) {
        onDocumentDeleted(id);
      }
    } catch (err) {
      setDeleteError(
        err.response?.data?.message || err.message || 'Failed to delete policy document.'
      );
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* Table Header Controls */}
      <div className="p-5 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center font-bold">
            <BookOpen className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-base font-bold text-slate-900">Indexed Policy Documents</h3>
            <p className="text-xs text-slate-500">
              {documents.length} {documents.length === 1 ? 'document' : 'documents'} currently stored in pgvector
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={onRefresh}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-xl text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors disabled:opacity-50 cursor-pointer self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          <span>Refresh</span>
        </button>
      </div>

      {/* Delete Error Notification */}
      {deleteError && (
        <div className="p-4 border-b border-slate-200">
          <Alert
            type="error"
            title="Deletion Error"
            message={deleteError}
            onClose={() => setDeleteError('')}
          />
        </div>
      )}

      {/* General Fetch Error */}
      {error && !isLoading && (
        <div className="p-4 border-b border-slate-200">
          <Alert
            type="error"
            title="Unable to load documents"
            message={error}
            onClose={() => {}}
          />
        </div>
      )}

      {/* Loading State */}
      {isLoading ? (
        <div className="py-16 flex flex-col items-center justify-center text-slate-400">
          <Spinner size="lg" className="text-indigo-600 mb-3" />
          <p className="text-sm font-medium text-slate-500">Loading policy documents...</p>
        </div>
      ) : documents.length === 0 ? (
        /* Empty State */
        <div className="py-16 px-4 text-center">
          <div className="w-14 h-14 rounded-2xl bg-indigo-50 text-indigo-500 flex items-center justify-center mx-auto mb-3">
            <FileText className="w-7 h-7" />
          </div>
          <h4 className="text-sm font-bold text-slate-800">No policy documents uploaded</h4>
          <p className="text-xs text-slate-500 max-w-sm mx-auto mt-1 leading-relaxed">
            Upload your first policy document above (e.g. Return Policy, Shipping Policy) to index knowledge into the vector store.
          </p>
        </div>
      ) : (
        /* Table */
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-slate-600">
            <thead className="bg-slate-50/80 text-[11px] font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200">
              <tr>
                <th scope="col" className="px-5 py-3.5">Filename</th>
                <th scope="col" className="px-5 py-3.5">Size</th>
                <th scope="col" className="px-5 py-3.5">Pages</th>
                <th scope="col" className="px-5 py-3.5">Chunks</th>
                <th scope="col" className="px-5 py-3.5">Status</th>
                <th scope="col" className="px-5 py-3.5">Uploaded</th>
                <th scope="col" className="px-5 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {documents.map((doc) => {
                const isConfirming = confirmDeleteId === doc.id;
                const isBeingDeleted = deletingId === doc.id;

                return (
                  <tr key={doc.id} className="hover:bg-slate-50/60 transition-colors">
                    {/* Filename */}
                    <td className="px-5 py-4 font-semibold text-slate-900">
                      <div className="flex items-center gap-2.5">
                        <div className="w-7 h-7 rounded-lg bg-red-50 text-red-600 flex items-center justify-center shrink-0">
                          <FileText className="w-4 h-4" />
                        </div>
                        <span className="truncate max-w-[200px] sm:max-w-[260px]" title={doc.filename}>
                          {doc.filename}
                        </span>
                      </div>
                    </td>

                    {/* Size */}
                    <td className="px-5 py-4 whitespace-nowrap text-slate-500">
                      {formatFileSize(doc.sizeBytes)}
                    </td>

                    {/* Pages */}
                    <td className="px-5 py-4 whitespace-nowrap">
                      <span className="inline-flex items-center gap-1 font-medium text-slate-700">
                        {doc.pageCount || 0}
                      </span>
                    </td>

                    {/* Chunks */}
                    <td className="px-5 py-4 whitespace-nowrap">
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md font-semibold text-[11px] bg-indigo-50 text-indigo-700">
                        <Layers className="w-3 h-3 text-indigo-500" />
                        {doc.chunkCount || 0}
                      </span>
                    </td>

                    {/* Status Badge */}
                    <td className="px-5 py-4 whitespace-nowrap">
                      <StatusBadge status={doc.status} errorMessage={doc.errorMessage} />
                    </td>

                    {/* Uploaded Date */}
                    <td className="px-5 py-4 whitespace-nowrap text-slate-400 text-[11px]">
                      {formatDate(doc.createdAt)}
                    </td>

                    {/* Delete Action with Confirmation Step */}
                    <td className="px-5 py-4 whitespace-nowrap text-right">
                      {isConfirming ? (
                        <div className="inline-flex items-center gap-2 bg-red-50 p-1.5 rounded-xl border border-red-200">
                          <span className="text-[11px] font-bold text-red-700">Delete?</span>
                          <button
                            type="button"
                            onClick={() => handleDelete(doc.id)}
                            disabled={isBeingDeleted}
                            className="px-2 py-1 text-[11px] font-bold rounded-lg text-white bg-red-600 hover:bg-red-700 disabled:opacity-50 cursor-pointer"
                          >
                            {isBeingDeleted ? <Spinner size="sm" className="text-white" /> : 'Confirm'}
                          </button>
                          <button
                            type="button"
                            onClick={() => setConfirmDeleteId(null)}
                            disabled={isBeingDeleted}
                            className="px-2 py-1 text-[11px] font-semibold rounded-lg text-slate-600 hover:bg-slate-200 cursor-pointer"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setConfirmDeleteId(doc.id)}
                          className="inline-flex items-center gap-1 p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors cursor-pointer"
                          title="Delete document and purge vectors"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default DocumentListTable;
