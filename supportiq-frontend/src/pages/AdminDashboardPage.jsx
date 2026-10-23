import React, { useState, useEffect, useCallback } from 'react';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import { pingAdminApi } from '../api/authApi';
import { getDocumentsApi } from '../api/documentApi';
import Spinner from '../components/Spinner';
import Alert from '../components/Alert';
import DocumentUploadBox from '../components/admin/DocumentUploadBox';
import DocumentListTable from '../components/admin/DocumentListTable';
import RagSearchTester from '../components/admin/RagSearchTester';
import {
  ShieldCheck,
  Activity,
  Server,
  Users,
  Sparkles,
  CheckCircle2,
  Database,
  Layers,
  FileCheck2,
} from 'lucide-react';

/**
 * AdminDashboardPage Component (Module 2 + Module 3)
 * 
 * Accessible exclusively to users with the ADMIN role.
 * Includes:
 * - Module 2: Admin ping API verification tool
 * - Module 3: Policy document upload, chunking, status monitoring, deletion, and vector similarity retrieval testbed
 */
const AdminDashboardPage = () => {
  const { user } = useAuth();

  // Module 2 state: ping admin test
  const [pingResult, setPingResult] = useState(null);
  const [isPinging, setIsPinging] = useState(false);
  const [pingError, setPingError] = useState('');

  // Module 3 state: policy documents
  const [documents, setDocuments] = useState([]);
  const [isLoadingDocuments, setIsLoadingDocuments] = useState(true);
  const [documentsError, setDocumentsError] = useState('');

  const fetchDocuments = useCallback(async () => {
    setIsLoadingDocuments(true);
    setDocumentsError('');
    try {
      const data = await getDocumentsApi();
      setDocuments(data || []);
    } catch (err) {
      setDocumentsError(
        err.response?.data?.message || err.message || 'Failed to load policy documents.'
      );
    } finally {
      setIsLoadingDocuments(false);
    }
  }, []);

  useEffect(() => {
    fetchDocuments();
  }, [fetchDocuments]);

  const handlePingAdmin = async () => {
    setIsPinging(true);
    setPingError('');
    setPingResult(null);

    try {
      const data = await pingAdminApi();
      setPingResult(data);
    } catch (err) {
      setPingError(
        err.response?.data?.message || 'Failed to ping admin endpoint.'
      );
    } finally {
      setIsPinging(false);
    }
  };

  const handleUploadSuccess = (_newDoc) => {
    // Refresh list to show newly indexed document
    fetchDocuments();
  };

  const handleDocumentDeleted = (deletedId) => {
    setDocuments((prev) => prev.filter((d) => d.id !== deletedId));
  };

  // Derived metrics for quick stats
  const totalChunks = documents.reduce((acc, doc) => acc + (doc.chunkCount || 0), 0);
  const readyDocuments = documents.filter((doc) => doc.status === 'READY').length;

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Navbar />

      <main className="flex-1 max-w-6xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-10">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-6 border-b border-slate-200">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-bold bg-purple-100 text-purple-800 ring-1 ring-purple-300">
                <ShieldCheck className="w-3.5 h-3.5" />
                ADMINISTRATOR ACCESS
              </span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
              Admin Control Center
            </h1>
            <p className="mt-1 text-xs sm:text-sm text-slate-500">
              Welcome back, {user?.name}. Manage knowledge base policies, vector chunks, and system health.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={handlePingAdmin}
              disabled={isPinging}
              className="inline-flex items-center gap-2 px-4 py-2.5 text-xs sm:text-sm font-semibold rounded-xl text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm transition-colors disabled:opacity-60 cursor-pointer"
            >
              {isPinging ? (
                <>
                  <Spinner size="sm" className="text-white" />
                  <span>Testing Ping...</span>
                </>
              ) : (
                <>
                  <Activity className="w-4 h-4" />
                  <span>Verify Admin Ping</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Live Admin Endpoint Ping Test Results */}
        {pingResult && (
          <div className="mt-6">
            <Alert
              type="success"
              title="Backend Authorization Succeeded (GET /api/admin/ping)"
              message={`${pingResult.message} (Role: ${pingResult.role}, Status: ${pingResult.status})`}
              onClose={() => setPingResult(null)}
            />
          </div>
        )}

        {pingError && (
          <div className="mt-6">
            <Alert
              type="error"
              title="Admin Verification Error"
              message={pingError}
              onClose={() => setPingError('')}
            />
          </div>
        )}

        {/* Quick Knowledge Base Stats Bar */}
        <div className="mt-6 grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
              <Database className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">Policy Documents</p>
              <p className="text-xl font-bold text-slate-900">{documents.length}</p>
            </div>
          </div>

          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
              <Layers className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">Indexed Vector Chunks</p>
              <p className="text-xl font-bold text-slate-900">{totalChunks}</p>
            </div>
          </div>

          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
              <FileCheck2 className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">Ready in pgvector</p>
              <p className="text-xl font-bold text-emerald-700">{readyDocuments}</p>
            </div>
          </div>
        </div>

        {/* SECTION: MODULE 3 - POLICY DOCUMENTS & VECTOR KNOWLEDGE BASE */}
        <div className="mt-8 space-y-8">
          <div className="flex items-center justify-between border-b border-slate-200 pb-3">
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                Policy Document Management & Ingestion
              </h2>
              <p className="text-xs text-slate-500">
                Extract text with Apache PDFBox 3.x, compute 768-dim Gemini embeddings, and index into PostgreSQL pgvector.
              </p>
            </div>
          </div>

          {/* Upload Box */}
          <DocumentUploadBox onUploadSuccess={handleUploadSuccess} />

          {/* Documents Table */}
          <DocumentListTable
            documents={documents}
            isLoading={isLoadingDocuments}
            error={documentsError}
            onRefresh={fetchDocuments}
            onDocumentDeleted={handleDocumentDeleted}
          />

          {/* Test Retrieval Search */}
          <RagSearchTester />
        </div>

        {/* SECTION: FUTURE MODULES PLACEHOLDERS & SECURITY AUDIT */}
        <div className="mt-12 pt-8 border-t border-slate-200">
          <h2 className="text-sm font-bold text-slate-700 uppercase tracking-wider mb-4">
            System Modules & Platform Control
          </h2>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm">
              <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center mb-4">
                <Users className="w-5 h-5" />
              </div>
              <h3 className="text-base font-bold text-slate-900 mb-1">Customer Management</h3>
              <p className="text-xs text-slate-500 mb-4 leading-relaxed">
                Inspect registered users, manage permissions, and view customer support history.
              </p>
              <span className="inline-flex items-center text-[11px] font-semibold text-slate-400 bg-slate-100 px-2.5 py-1 rounded-lg">
                Coming in future modules
              </span>
            </div>

            <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm">
              <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center mb-4">
                <Sparkles className="w-5 h-5" />
              </div>
              <h3 className="text-base font-bold text-slate-900 mb-1">AI Agent Tuner</h3>
              <p className="text-xs text-slate-500 mb-4 leading-relaxed">
                Configure system prompts, adjust AI temperature, and manage e-commerce tool integrations.
              </p>
              <span className="inline-flex items-center text-[11px] font-semibold text-slate-400 bg-slate-100 px-2.5 py-1 rounded-lg">
                Module 4 Integration
              </span>
            </div>

            <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm">
              <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center mb-4">
                <Server className="w-5 h-5" />
              </div>
              <h3 className="text-base font-bold text-slate-900 mb-1">Audit & Telemetry</h3>
              <p className="text-xs text-slate-500 mb-4 leading-relaxed">
                Track latency, prompt token usage, and automated resolution rates.
              </p>
              <span className="inline-flex items-center text-[11px] font-semibold text-slate-400 bg-slate-100 px-2.5 py-1 rounded-lg">
                Coming in future modules
              </span>
            </div>
          </div>

          {/* Security & RBAC Notice */}
          <div className="mt-8 bg-indigo-50/60 rounded-2xl p-6 border border-indigo-100">
            <div className="flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-indigo-600 shrink-0 mt-0.5" />
              <div className="text-xs text-indigo-900 leading-relaxed">
                <span className="font-bold">Role-Based Access Control Active:</span> This route and all <code className="bg-indigo-100/80 px-1 py-0.5 rounded font-mono">/api/admin/**</code> endpoints are protected by both client-side route guards (<code className="bg-indigo-100/80 px-1 py-0.5 rounded font-mono">ProtectedRoute allowedRoles=[&apos;ADMIN&apos;]</code>) and backend Spring Security (<code className="bg-indigo-100/80 px-1 py-0.5 rounded font-mono">hasRole(&apos;ADMIN&apos;)</code>). Standard customers attempting to access this page or call document APIs receive a 403 Forbidden response.
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};

export default AdminDashboardPage;
