import React from 'react';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import { MessageSquare, Sparkles, CheckCircle } from 'lucide-react';

/**
 * ChatPage Component (Module 2 Placeholder)
 * 
 * Accessible by all authenticated users (CUSTOMER and ADMIN).
 * Features a friendly notice that the AI customer support chat interface
 * will be fully implemented in Module 4.
 */
const ChatPage = () => {
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Navbar />

      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-10 flex flex-col justify-center">
        {/* Welcome Banner */}
        <div className="bg-white rounded-3xl p-8 sm:p-12 border border-slate-200/80 shadow-xl shadow-slate-200/50 text-center relative overflow-hidden">
          {/* Subtle background decoration */}
          <div className="absolute -top-24 -right-24 w-64 h-64 bg-indigo-50 rounded-full blur-3xl pointer-events-none" />
          <div className="absolute -bottom-24 -left-24 w-64 h-64 bg-purple-50 rounded-full blur-3xl pointer-events-none" />

          <div className="relative z-10 max-w-2xl mx-auto flex flex-col items-center">
            {/* Icon */}
            <div className="w-16 h-16 rounded-2xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600 mb-6 shadow-sm">
              <MessageSquare className="w-8 h-8" />
            </div>

            {/* Badge */}
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200 mb-4">
              <Sparkles className="w-3.5 h-3.5 text-indigo-500" />
              <span>Module 2 Frontend Active</span>
            </div>

            <h1 className="text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
              Chat coming in Module 4
            </h1>

            <p className="mt-4 text-base text-slate-600 leading-relaxed">
              Hello, <span className="font-semibold text-slate-800">{user?.name}</span>! You have successfully authenticated. The AI customer support conversational interface, automated order tracking, and support ticket management will arrive in Module 4.
            </p>

            {/* User Session Snapshot */}
            <div className="mt-8 w-full max-w-md bg-slate-50 rounded-2xl p-4 border border-slate-200/70 text-left">
              <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-3">
                Active Session Details
              </h2>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between items-center">
                  <span className="text-slate-500">Email:</span>
                  <span className="font-medium text-slate-800">{user?.email}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-500">Assigned Role:</span>
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800">
                    {user?.role}
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-500">Authentication:</span>
                  <span className="inline-flex items-center gap-1 text-emerald-600 text-xs font-semibold">
                    <CheckCircle className="w-3.5 h-3.5" />
                    Verified Bearer JWT
                  </span>
                </div>
              </div>
            </div>

            {/* Roadmap Highlights */}
            <div className="mt-8 grid grid-cols-1 sm:grid-cols-3 gap-3 w-full text-left text-xs">
              <div className="p-3.5 rounded-xl border border-slate-200 bg-white">
                <span className="font-semibold text-slate-900 block mb-1">Module 3</span>
                <span className="text-slate-500">AI Knowledge Base & E-Commerce Integration</span>
              </div>
              <div className="p-3.5 rounded-xl border border-indigo-200 bg-indigo-50/40">
                <span className="font-semibold text-indigo-900 block mb-1">Module 4</span>
                <span className="text-indigo-700">Live Support Chat & Order Assistant</span>
              </div>
              <div className="p-3.5 rounded-xl border border-slate-200 bg-white">
                <span className="font-semibold text-slate-900 block mb-1">Module 5</span>
                <span className="text-slate-500">Automated Ticket Escalation</span>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};

export default ChatPage;
