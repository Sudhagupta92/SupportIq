import React, { useState, useRef } from 'react';
import { UploadCloud, FileText, X, ArrowUpCircle } from 'lucide-react';
import Spinner from '../Spinner';
import Alert from '../Alert';
import { uploadDocumentApi } from '../../api/documentApi';

const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

/**
 * Formats file size in bytes to human-readable string (KB or MB).
 */
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
};

/**
 * DocumentUploadBox Component
 * 
 * Provides an interactive drag-and-drop & file picker upload interface for policy PDFs.
 * Enforces client-side validation for PDF format and 10 MB maximum file size.
 * Tracks upload progress and surfaces clear server-side feedback.
 */
const DocumentUploadBox = ({ onUploadSuccess }) => {
  const [file, setFile] = useState(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [clientError, setClientError] = useState('');
  const [serverError, setServerError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const fileInputRef = useRef(null);

  const validateFile = (selectedFile) => {
    setClientError('');
    setServerError('');
    setSuccessMessage('');

    if (!selectedFile) return false;

    // 1. Validate file extension and MIME type
    const isPdfMime = selectedFile.type === 'application/pdf' || selectedFile.type === 'application/x-pdf';
    const isPdfExt = selectedFile.name.toLowerCase().endsWith('.pdf');
    if (!isPdfMime && !isPdfExt) {
      setClientError('Invalid file type: Please select a valid PDF document (.pdf).');
      return false;
    }

    // 2. Validate file size (10 MB maximum)
    if (selectedFile.size > MAX_FILE_SIZE_BYTES) {
      setClientError(
        `File too large: ${selectedFile.name} is ${formatFileSize(selectedFile.size)}. Maximum allowed size is 10 MB.`
      );
      return false;
    }

    if (selectedFile.size === 0) {
      setClientError('The selected file is empty.');
      return false;
    }

    return true;
  };

  const handleFileChange = (e) => {
    const selected = e.target.files?.[0];
    if (selected) {
      if (validateFile(selected)) {
        setFile(selected);
      } else {
        setFile(null);
        if (fileInputRef.current) fileInputRef.current.value = '';
      }
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);

    const droppedFile = e.dataTransfer.files?.[0];
    if (droppedFile) {
      if (validateFile(droppedFile)) {
        setFile(droppedFile);
      } else {
        setFile(null);
        if (fileInputRef.current) fileInputRef.current.value = '';
      }
    }
  };

  const handleClearFile = () => {
    setFile(null);
    setClientError('');
    setServerError('');
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    if (!file) {
      setClientError('Please choose a PDF document to upload.');
      return;
    }

    setIsUploading(true);
    setUploadProgress(0);
    setClientError('');
    setServerError('');
    setSuccessMessage('');

    try {
      const response = await uploadDocumentApi(file, (progressEvent) => {
        if (progressEvent.total) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          setUploadProgress(percent);
        }
      });

      const chunkCount = response.chunkCount || 0;
      const pageCount = response.pageCount || 0;
      setSuccessMessage(
        `Successfully ingested "${response.filename}" (${pageCount} ${pageCount === 1 ? 'page' : 'pages'}, ${chunkCount} ${chunkCount === 1 ? 'chunk' : 'chunks'}). Indexed into pgvector!`
      );
      handleClearFile();

      if (onUploadSuccess) {
        onUploadSuccess(response);
      }
    } catch (err) {
      // Backend error response structure: { timestamp, status, error, message, path }
      const backendMessage =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Failed to upload and process document.';
      setServerError(backendMessage);
    } finally {
      setIsUploading(false);
      setUploadProgress(0);
    }
  };

  return (
    <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm">
      <div className="flex items-center gap-2 mb-2">
        <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center font-bold">
          <UploadCloud className="w-4 h-4" />
        </div>
        <h3 className="text-base font-bold text-slate-900">Upload Policy Document</h3>
      </div>
      <p className="text-xs text-slate-500 mb-5 leading-relaxed">
        Upload store policies (returns, warranties, shipping) in PDF format. Extracted text will be split into overlapping chunks, vectorized via Gemini embeddings, and stored in pgvector.
      </p>

      {/* Success Notification */}
      {successMessage && (
        <div className="mb-4">
          <Alert
            type="success"
            title="Document Ingestion Complete"
            message={successMessage}
            onClose={() => setSuccessMessage('')}
          />
        </div>
      )}

      {/* Client Validation Error */}
      {clientError && (
        <div className="mb-4">
          <Alert
            type="error"
            title="Invalid Document"
            message={clientError}
            onClose={() => setClientError('')}
          />
        </div>
      )}

      {/* Server Processing Error */}
      {serverError && (
        <div className="mb-4">
          <Alert
            type="error"
            title="Upload Failed"
            message={serverError}
            onClose={() => setServerError('')}
          />
        </div>
      )}

      {/* Drag & Drop Area */}
      <div
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current?.click()}
        className={`relative border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-all duration-200 ${
          isDragging
            ? 'border-indigo-500 bg-indigo-50/60 scale-[1.005]'
            : 'border-slate-300 hover:border-indigo-400 hover:bg-slate-50/70'
        }`}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,application/pdf"
          onChange={handleFileChange}
          disabled={isUploading}
          className="hidden"
          id="policy-file-input"
        />

        <div className="flex flex-col items-center justify-center pointer-events-none">
          <div className="w-12 h-12 rounded-full bg-slate-100 text-slate-500 flex items-center justify-center mb-3">
            <UploadCloud className="w-6 h-6 text-slate-600" />
          </div>
          <p className="text-sm font-semibold text-slate-800">
            Click to choose a file <span className="font-normal text-slate-500">or drag and drop</span>
          </p>
          <p className="text-xs text-slate-400 mt-1">
            Adobe PDF (.pdf) only &bull; Maximum file size: 10 MB
          </p>
        </div>
      </div>

      {/* Selected File Details & Upload Action */}
      {file && (
        <div className="mt-4 p-4 rounded-xl bg-slate-50 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-10 h-10 rounded-lg bg-red-100 text-red-700 flex items-center justify-center shrink-0">
              <FileText className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <p className="text-sm font-semibold text-slate-900 truncate">{file.name}</p>
              <p className="text-xs text-slate-500">{formatFileSize(file.size)} &bull; Ready to ingest</p>
            </div>
          </div>

          <div className="flex items-center gap-2 self-end sm:self-center">
            <button
              type="button"
              onClick={handleClearFile}
              disabled={isUploading}
              className="p-2 text-slate-400 hover:text-slate-600 rounded-lg transition-colors disabled:opacity-50 cursor-pointer"
              title="Remove file"
            >
              <X className="w-4 h-4" />
            </button>

            <button
              type="button"
              onClick={handleUpload}
              disabled={isUploading}
              className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-xl text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm transition-colors disabled:opacity-60 cursor-pointer"
            >
              {isUploading ? (
                <>
                  <Spinner size="sm" className="text-white" />
                  <span>Ingesting PDF...</span>
                </>
              ) : (
                <>
                  <ArrowUpCircle className="w-4 h-4" />
                  <span>Upload & Vectorize</span>
                </>
              )}
            </button>
          </div>
        </div>
      )}

      {/* Upload Progress Bar */}
      {isUploading && (
        <div className="mt-4">
          <div className="flex items-center justify-between text-xs text-slate-600 mb-1">
            <span>Uploading to server & processing embeddings...</span>
            <span className="font-semibold">{uploadProgress}%</span>
          </div>
          <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-indigo-600 transition-all duration-300 ease-out"
              style={{ width: `${Math.max(5, uploadProgress)}%` }}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default DocumentUploadBox;
