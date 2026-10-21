import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ShieldAlert, ArrowLeft, LogOut } from 'lucide-react';

/**
 * ForbiddenPage Component (403 Access Denied)
 * 
 * Displayed when an authenticated user attempts to access an endpoint or view
 * for which they lack appropriate role privileges (e.g. a CUSTOMER visiting /admin).
 */
const ForbiddenPage = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-center items-center px-4 py-12">
      <div className="max-w-md w-full bg-white rounded-3xl p-8 sm:p-10 border border-slate-200/80 shadow-xl shadow-slate-200/50 text-center">
        <div className="w-16 h-16 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center mx-auto mb-6 border border-amber-200">
          <ShieldAlert className="w-8 h-8" />
        </div>

        <span className="text-xs font-bold uppercase tracking-widest text-amber-600 bg-amber-50 px-3 py-1 rounded-full border border-amber-200">
          403 Forbidden
        </span>

        <h1 className="mt-4 text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
          Access Restricted
        </h1>

        <p className="mt-3 text-sm text-slate-600 leading-relaxed">
          You are signed in as <span className="font-semibold text-slate-800">{user?.name || user?.email}</span> with the role of{' '}
          <span className="font-bold text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded">
            {user?.role || 'CUSTOMER'}
          </span>
          . This section requires <span className="font-bold text-purple-700">ADMIN</span> privileges.
        </p>

        <div className="mt-8 flex flex-col gap-3">
          <Link
            to="/chat"
            className="w-full inline-flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to Chat</span>
          </Link>

          <button
            type="button"
            onClick={handleLogout}
            className="w-full inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl text-sm font-semibold text-slate-600 hover:text-red-600 hover:bg-red-50 border border-slate-200 hover:border-red-200 transition-colors cursor-pointer"
          >
            <LogOut className="w-4 h-4" />
            <span>Switch Account / Logout</span>
          </button>
        </div>
      </div>
    </div>
  );
};

export default ForbiddenPage;
