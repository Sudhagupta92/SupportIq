import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import Navbar from '../components/Navbar';
import TicketStatusBadge from '../components/TicketStatusBadge';
import TicketPriorityBadge from '../components/TicketPriorityBadge';
import Spinner from '../components/Spinner';
import {
  getAdminTicketApi,
  addAdminMessageApi,
  updateAdminTicketStatusApi,
} from '../api/ticketApi';
import {
  ArrowLeft,
  Send,
  Loader2,
  Package,
  ShieldCheck,
  User as UserIcon,
  Info,
  CheckCircle2,
  AlertCircle,
  Lock,
  RefreshCw,
  Mail,
} from 'lucide-react';

const formatCategory = (category) => {
  if (!category) return 'Other';
  return category
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

/**
 * Returns allowed next statuses adhering strictly to business transition rules:
 * - OPEN -> IN_PROGRESS, RESOLVED, CLOSED
 * - IN_PROGRESS -> RESOLVED, CLOSED
 * - RESOLVED -> OPEN, CLOSED
 * - CLOSED -> none (terminal state)
 */
const getValidNextStatuses = (currentStatus) => {
  switch (currentStatus) {
    case 'OPEN':
      return ['IN_PROGRESS', 'RESOLVED', 'CLOSED'];
    case 'IN_PROGRESS':
      return ['RESOLVED', 'CLOSED'];
    case 'RESOLVED':
      return ['OPEN', 'CLOSED'];
    case 'CLOSED':
    default:
      return [];
  }
};

const STATUS_LABELS = {
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

const AdminTicketDetailPage = () => {
  const { ticketNumber } = useParams();
  const [ticket, setTicket] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Status Change State
  const [targetStatus, setTargetStatus] = useState('');
  const [statusUpdating, setStatusUpdating] = useState(false);
  const [statusSuccessMsg, setStatusSuccessMsg] = useState(null);
  const [statusErrorMsg, setStatusErrorMsg] = useState(null);

  // Reply State
  const [replyText, setReplyText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [replyError, setReplyError] = useState(null);

  const messagesEndRef = useRef(null);

  const fetchTicket = useCallback(async () => {
    if (!ticketNumber) return;
    setLoading(true);
    setError(null);
    try {
      const data = await getAdminTicketApi(ticketNumber);
      setTicket(data);
      setTargetStatus('');
    } catch (err) {
      console.error('Failed to load admin ticket:', err);
      setError(err.response?.data?.message || 'Unable to load ticket details.');
    } finally {
      setLoading(false);
    }
  }, [ticketNumber]);

  useEffect(() => {
    fetchTicket();
  }, [fetchTicket]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [ticket?.messages]);

  const handleStatusChange = async (e) => {
    e.preventDefault();
    if (!targetStatus || statusUpdating) return;

    setStatusUpdating(true);
    setStatusErrorMsg(null);
    setStatusSuccessMsg(null);
    try {
      const updatedTicket = await updateAdminTicketStatusApi(ticketNumber, targetStatus);
      setTicket(updatedTicket);
      setStatusSuccessMsg(`Ticket status successfully updated to ${STATUS_LABELS[targetStatus]}.`);
      setTargetStatus('');
      setTimeout(() => setStatusSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Failed to update ticket status:', err);
      setStatusErrorMsg(err.response?.data?.message || 'Failed to update status.');
    } finally {
      setStatusUpdating(false);
    }
  };

  const handleSendReply = async (e) => {
    e.preventDefault();
    const message = replyText.trim();
    if (!message || isSubmitting) return;

    if (message.length > 2000) {
      setReplyError('Message must not exceed 2000 characters.');
      return;
    }

    setIsSubmitting(true);
    setReplyError(null);
    try {
      const updatedTicket = await addAdminMessageApi(ticketNumber, message);
      setTicket(updatedTicket);
      setReplyText('');
    } catch (err) {
      console.error('Failed to send admin reply:', err);
      setReplyError(err.response?.data?.message || 'Failed to send reply.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const validNextStatuses = ticket ? getValidNextStatuses(ticket.status) : [];
  const isClosed = ticket?.status === 'CLOSED';

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      <Navbar />

      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 py-6 sm:py-8 flex flex-col">
        {/* Navigation Breadcrumb */}
        <div className="mb-4 flex items-center justify-between">
          <Link
            to="/admin/tickets"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-indigo-600 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to Tickets Console</span>
          </Link>

          <button
            type="button"
            onClick={fetchTicket}
            disabled={loading}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 bg-white border border-slate-200 rounded-lg shadow-2xs transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh Thread</span>
          </button>
        </div>

        {/* Loading State */}
        {loading && (
          <div className="bg-white rounded-2xl border border-slate-200 p-12 flex items-center justify-center min-h-[350px]">
            <Spinner text="Loading ticket conversation..." />
          </div>
        )}

        {/* Error State */}
        {error && !loading && (
          <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center max-w-lg mx-auto shadow-sm">
            <div className="w-12 h-12 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center mx-auto mb-3">
              <AlertCircle className="w-6 h-6" />
            </div>
            <h2 className="text-base font-bold text-slate-900 mb-1">Unable to Load Ticket</h2>
            <p className="text-xs text-slate-500 mb-5">{error}</p>
            <Link
              to="/admin/tickets"
              className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-sm transition-colors"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Return to Tickets Console</span>
            </Link>
          </div>
        )}

        {/* Ticket Details & Conversation Thread */}
        {ticket && !loading && (
          <div className="bg-white rounded-2xl border border-slate-200/90 shadow-sm flex flex-col overflow-hidden">
            {/* Header & Status Management Panel */}
            <div className="p-5 sm:p-6 border-b border-slate-200 bg-slate-50/70">
              <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                {/* Left: Ticket title & badges */}
                <div>
                  <div className="flex items-center gap-2 mb-2 flex-wrap">
                    <span className="font-mono font-bold text-sm text-indigo-700 bg-indigo-50 px-2.5 py-0.5 rounded border border-indigo-200">
                      {ticket.ticketNumber}
                    </span>
                    <TicketStatusBadge status={ticket.status} size="lg" />
                    <TicketPriorityBadge priority={ticket.priority} />
                    <span className="text-xs font-medium text-slate-600 bg-slate-100 px-2 py-0.5 rounded">
                      {formatCategory(ticket.category)}
                    </span>
                  </div>
                  <h1 className="text-lg sm:text-xl font-bold text-slate-900 leading-snug">
                    {ticket.subject}
                  </h1>
                </div>

                {/* Right: Status Transition Controls */}
                <div className="bg-white p-3 rounded-xl border border-slate-200 shadow-2xs shrink-0">
                  <div className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1.5">
                    Update Status
                  </div>
                  {isClosed ? (
                    <div className="text-xs font-medium text-slate-500 flex items-center gap-1.5 py-1">
                      <Lock className="w-3.5 h-3.5 text-slate-400" />
                      <span>Ticket is Closed (Terminal state)</span>
                    </div>
                  ) : validNextStatuses.length === 0 ? (
                    <div className="text-xs text-slate-400">No further status transitions</div>
                  ) : (
                    <form onSubmit={handleStatusChange} className="flex items-center gap-2">
                      <select
                        value={targetStatus}
                        onChange={(e) => setTargetStatus(e.target.value)}
                        disabled={statusUpdating}
                        className="px-2.5 py-1.5 text-xs font-semibold border border-slate-300 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        aria-label="Select Next Status"
                      >
                        <option value="">Move status to...</option>
                        {validNextStatuses.map((st) => (
                          <option key={st} value={st}>
                            {STATUS_LABELS[st]}
                          </option>
                        ))}
                      </select>
                      <button
                        type="submit"
                        disabled={!targetStatus || statusUpdating}
                        className="px-3 py-1.5 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:bg-slate-300 rounded-lg transition-colors cursor-pointer"
                      >
                        {statusUpdating ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <span>Apply</span>
                        )}
                      </button>
                    </form>
                  )}
                </div>
              </div>

              {/* Status change feedback alerts */}
              {statusSuccessMsg && (
                <div className="mt-3 p-2.5 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>{statusSuccessMsg}</span>
                </div>
              )}
              {statusErrorMsg && (
                <div className="mt-3 p-2.5 rounded-lg bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                  <span>{statusErrorMsg}</span>
                </div>
              )}

              {/* Customer Info & Order Link Banner */}
              <div className="mt-4 pt-4 border-t border-slate-200 flex flex-wrap items-center justify-between gap-4 text-xs">
                {/* Customer Info (Name and Email only - strictly no password or token) */}
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-slate-200 text-slate-700 flex items-center justify-center font-bold text-xs">
                    <UserIcon className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="font-bold text-slate-900 text-xs flex items-center gap-2">
                      <span>{ticket.customerName || 'Customer'}</span>
                      <span className="text-[10px] uppercase font-bold text-slate-400 bg-slate-100 px-1.5 py-0.2 rounded">
                        Customer #{ticket.customerId}
                      </span>
                    </div>
                    <div className="text-[11px] text-slate-500 flex items-center gap-1">
                      <Mail className="w-3 h-3 text-slate-400" />
                      <span>{ticket.customerEmail}</span>
                    </div>
                  </div>
                </div>

                {/* Related Order Link */}
                {ticket.relatedOrderNumber && (
                  <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-amber-50 border border-amber-200 text-amber-900">
                    <Package className="w-4 h-4 text-amber-600 shrink-0" />
                    <div>
                      <span className="text-[10px] uppercase font-bold text-amber-700 block">Related Order</span>
                      <span className="font-bold text-xs">Order #{ticket.relatedOrderNumber}</span>
                    </div>
                  </div>
                )}

                {/* Timestamps */}
                <div className="text-right text-[11px] text-slate-400">
                  <div>Opened: {formatDate(ticket.createdAt)}</div>
                  {ticket.resolvedAt && (
                    <div className="text-emerald-600 font-semibold">
                      Resolved: {formatDate(ticket.resolvedAt)}
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Conversation Thread */}
            <div className="p-5 sm:p-6 space-y-4 max-h-[500px] overflow-y-auto bg-slate-50/30">
              {ticket.messages && ticket.messages.map((msg, idx) => {
                const isSystem = msg.senderType === 'SYSTEM';
                const isAdmin = msg.senderType === 'ADMIN';

                if (isSystem) {
                  return (
                    <div key={msg.id || idx} className="flex items-center justify-center my-3">
                      <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-medium bg-slate-100 text-slate-600 border border-slate-200 shadow-2xs">
                        <Info className="w-3 h-3 text-slate-400" />
                        {/* Plain text React escaping */}
                        <span>{msg.body}</span>
                        <span className="text-slate-400 font-normal">({formatDate(msg.createdAt)})</span>
                      </div>
                    </div>
                  );
                }

                if (isAdmin) {
                  return (
                    <div key={msg.id || idx} className="flex items-start gap-3 justify-end max-w-2xl ml-auto">
                      <div className="bg-purple-50/70 border border-purple-200/80 rounded-2xl rounded-tr-sm p-4 shadow-xs">
                        <div className="flex items-center justify-between gap-3 mb-1.5 pb-1 border-b border-purple-100">
                          <span className="text-xs font-bold text-purple-900 flex items-center gap-1">
                            <span>Agent Reply ({msg.senderName || 'Support Agent'})</span>
                          </span>
                          <span className="text-[10px] text-slate-400">{formatDate(msg.createdAt)}</span>
                        </div>
                        {/* Plain text React escaping */}
                        <p className="text-sm text-slate-800 whitespace-pre-wrap">{msg.body}</p>
                      </div>
                      <div className="w-8 h-8 rounded-lg bg-purple-600 text-white flex items-center justify-center shrink-0 mt-0.5 shadow-2xs">
                        <ShieldCheck className="w-4 h-4" />
                      </div>
                    </div>
                  );
                }

                // Customer message
                return (
                  <div key={msg.id || idx} className="flex items-start gap-3 justify-start max-w-2xl">
                    <div className="w-8 h-8 rounded-lg bg-slate-200 text-slate-700 flex items-center justify-center shrink-0 mt-0.5">
                      <UserIcon className="w-4 h-4" />
                    </div>
                    <div className="bg-white border border-slate-200 rounded-2xl rounded-tl-sm p-4 shadow-xs">
                      <div className="flex items-center justify-between gap-3 mb-1.5 pb-1 border-b border-slate-100">
                        <span className="text-xs font-bold text-slate-900">{ticket.customerName || 'Customer'}</span>
                        <span className="text-[10px] text-slate-400">{formatDate(msg.createdAt)}</span>
                      </div>
                      {/* Plain text React escaping */}
                      <p className="text-sm text-slate-800 whitespace-pre-wrap">{msg.body}</p>
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            {/* Admin Reply Box */}
            <div className="p-4 sm:p-5 border-t border-slate-200 bg-white">
              {replyError && (
                <div className="mb-3 p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                  <span>{replyError}</span>
                </div>
              )}

              <form onSubmit={handleSendReply}>
                <div className="relative">
                  <textarea
                    rows={3}
                    value={replyText}
                    onChange={(e) => setReplyText(e.target.value)}
                    maxLength={2000}
                    disabled={isSubmitting}
                    placeholder="Type an official support agent reply to the customer..."
                    className="w-full px-3.5 py-2.5 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-purple-500 resize-none pr-28"
                  />
                  <div className="absolute right-3 bottom-3 flex items-center gap-2">
                    <span className={`text-[10px] ${replyText.length > 2000 ? 'text-rose-600 font-bold' : 'text-slate-400'}`}>
                      {replyText.length}/2000
                    </span>
                    <button
                      type="submit"
                      disabled={!replyText.trim() || isSubmitting}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white bg-purple-600 hover:bg-purple-700 disabled:bg-slate-300 rounded-lg shadow-xs transition-colors cursor-pointer"
                    >
                      {isSubmitting ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <>
                          <Send className="w-3.5 h-3.5" />
                          <span>Send Reply</span>
                        </>
                      )}
                    </button>
                  </div>
                </div>
              </form>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default AdminTicketDetailPage;
