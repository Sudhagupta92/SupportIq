import React, { useState } from 'react';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import { pingAdminApi } from '../api/authApi';
import Spinner from '../components/Spinner';
import Alert from '../components/Alert';
import {
  ShieldCheck,
  Activity,
  Server,
  Users,
  Sparkles,
  CheckCircle2,
} from 'lucide-react';

/**
 * AdminDashboardPage Component (Module 2 Placeholder)
 * 
 * Accessible exclusively to users with the ADMIN role.
 * Includes a live verification tool to ping GET /api/admin/ping.
 */
const AdminDashboardPage = () => {
  const { user } = useAuth();
  const [pingResult, setPingResult] = useState(null);
  const [isPinging, setIsPinging] = useState(false);
  const [pingError, setPingError] = useState('');

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

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Navbar />

      <main className="flex-1 max-w-6xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-10">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-8 border-b border-slate-200">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-bold bg-purple-100 text-purple-800 ring-1 ring-purple-300">
                <ShieldCheck className="w-3.5 h-3.5" />
                ADMINISTRATOR ACCESS
              </span>
            </div>
            <h1 className="text-3xl font-extrabold text-slate-900 tracking-tight">
              Admin Control Center
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              Welcome back, {user?.name}. Manage customer escalations and AI agent configurations.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={handlePingAdmin}
              disabled={isPinging}
              className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold rounded-xl text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm transition-colors disabled:opacity-60 cursor-pointer"
            >
              {isPinging ? (
                <>
                  <Spinner size="sm" className="text-white" />
                  <span>Testing API...</span>
                </>
              ) : (
                <>
                  <Activity className="w-4 h-4" />
                  <span>Verify Admin Ping API</span>
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

        {/* Feature Grid Placeholders */}
        <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center mb-4">
              <Users className="w-5 h-5" />
            </div>
            <h2 className="text-base font-bold text-slate-900 mb-1">Customer Management</h2>
            <p className="text-xs text-slate-500 mb-4 leading-relaxed">
              Inspect registered users, manage permissions, and view customer support history.
            </p>
            <span className="inline-flex items-center text-[11px] font-semibold text-slate-400 bg-slate-100 px-2.5 py-1 rounded-lg">
              Coming in future modules
            </span>
          </div>

          <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center mb-4">
              <Sparkles className="w-5 h-5" />
            </div>
            <h2 className="text-base font-bold text-slate-900 mb-1">AI Agent Tuner</h2>
            <p className="text-xs text-slate-500 mb-4 leading-relaxed">
              Configure system prompts, adjust AI temperature, and manage e-commerce tool integrations.
            </p>
            <span className="inline-flex items-center text-[11px] font-semibold text-slate-400 bg-slate-100 px-2.5 py-1 rounded-lg">
              Coming in future modules
            </span>
          </div>

          <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center mb-4">
              <Server className="w-5 h-5" />
            </div>
            <h2 className="text-base font-bold text-slate-900 mb-1">Audit & Telemetry</h2>
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
              <span className="font-bold">Role-Based Access Control Active:</span> This route is protected by both client-side route guards (<code className="bg-indigo-100/80 px-1 py-0.5 rounded">ProtectedRoute allowedRoles=[&apos;ADMIN&apos;]</code>) and backend Spring Security (<code className="bg-indigo-100/80 px-1 py-0.5 rounded">hasRole(&apos;ADMIN&apos;)</code>). Standard customers attempting to access this page will receive a 403 Forbidden response.
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};

export default AdminDashboardPage;
