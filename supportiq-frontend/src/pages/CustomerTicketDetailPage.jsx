import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import Navbar from '../components/Navbar';
import TicketStatusBadge from '../components/TicketStatusBadge';
import TicketPriorityBadge from '../components/TicketPriorityBadge';
import Spinner from '../components/Spinner';
import { getMyTicketApi, addCustomerMessageApi } from '../api/ticketApi';
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

const CustomerTicketDetailPage = () => {
  const { ticketNumber } = useParams();
  const [ticket, setTicket] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [replyText, setReplyText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionError, setActionError] = useState(null);
  const messagesEndRef = useRef(null);

  const fetchTicket = useCallback(async () => {
    if (!ticketNumber) return;
    setLoading(true);
    setError(null);
    try {
      const data = await getMyTicketApi(ticketNumber);
      setTicket(data);
    } catch (err) {
      console.error('Failed to load ticket:', err);
      if (err.response?.status === 404) {
        setError('Ticket not found. It may not exist or belongs to another customer account.');
      } else {
        setError(err.response?.data?.message || 'Unable to load ticket details.');
      }
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

  const handleSendReply = async (e) => {
    e.preventDefault();
    const message = replyText.trim();
    if (!message || isSubmitting) return;

    if (message.length > 2000) {
      setActionError('Message must not exceed 2000 characters.');
      return;
    }

    setIsSubmitting(true);
    setActionError(null);
    try {
      const updatedTicket = await addCustomerMessageApi(ticketNumber, message);
      setTicket(updatedTicket);
      setReplyText('');
    } catch (err) {
      console.error('Failed to send reply:', err);
      setActionError(err.response?.data?.message || 'Failed to send message. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const isClosed = ticket?.status === 'CLOSED';
  const isResolved = ticket?.status === 'RESOLVED';

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      <Navbar />

      <main className="flex-1 max-w-4xl w-full mx-auto px-4 sm:px-6 py-6 sm:py-8 flex flex-col">
        {/* Navigation Breadcrumb */}
        <div className="mb-4">
          <Link
            to="/tickets"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-indigo-600 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to My Tickets</span>
          </Link>
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
              to="/tickets"
              className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-sm transition-colors"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Return to My Tickets</span>
            </Link>
          </div>
        )}

        {/* Ticket Details & Thread Container */}
        {ticket && !loading && (
          <div className="bg-white rounded-2xl border border-slate-200/90 shadow-sm flex flex-col overflow-hidden">
            {/* Ticket Header Banner */}
            <div className="p-5 sm:p-6 border-b border-slate-100 bg-slate-50/60">
              <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                    <span className="font-mono font-bold text-sm text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded border border-indigo-200/70">
                      {ticket.ticketNumber}
                    </span>
                    <TicketStatusBadge status={ticket.status} size="lg" />
                    <TicketPriorityBadge priority={ticket.priority} />
                  </div>
                  <h1 className="text-lg sm:text-xl font-bold text-slate-900 leading-snug">
                    {ticket.subject}
                  </h1>
                </div>

                {/* Timestamps */}
                <div className="text-right text-xs text-slate-400 shrink-0 space-y-1">
                  <div>Opened: {formatDate(ticket.createdAt)}</div>
                  {ticket.resolvedAt && (
                    <div className="text-emerald-600 font-medium">
                      Resolved: {formatDate(ticket.resolvedAt)}
                    </div>
                  )}
                </div>
              </div>

              {/* Metadata Pills */}
              <div className="mt-4 pt-3 border-t border-slate-200/60 flex flex-wrap items-center gap-4 text-xs text-slate-600">
                <div>
                  <span className="text-slate-400 uppercase text-[10px] font-bold tracking-wider block">Category</span>
                  <span className="font-semibold text-slate-800">{formatCategory(ticket.category)}</span>
                </div>
                {ticket.relatedOrderNumber && (
                  <div>
                    <span className="text-slate-400 uppercase text-[10px] font-bold tracking-wider block">Related Order</span>
                    <span className="inline-flex items-center gap-1 font-semibold text-amber-800">
                      <Package className="w-3.5 h-3.5 text-amber-600" />
                      <span>Order #{ticket.relatedOrderNumber}</span>
                    </span>
                  </div>
                )}
                {ticket.escalationReason && (
                  <div>
                    <span className="text-slate-400 uppercase text-[10px] font-bold tracking-wider block">Source</span>
                    <span className="font-semibold text-slate-700">{ticket.source} ({ticket.escalationReason})</span>
                  </div>
                )}
              </div>
            </div>

            {/* Message Thread Scroll Area */}
            <div className="p-5 sm:p-6 space-y-4 max-h-[500px] overflow-y-auto bg-slate-50/30">
              {ticket.messages && ticket.messages.map((msg, idx) => {
                const isSystem = msg.senderType === 'SYSTEM';
                const isAdmin = msg.senderType === 'ADMIN';

                if (isSystem) {
                  return (
                    <div key={msg.id || idx} className="flex items-center justify-center my-3">
                      <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-medium bg-slate-100 text-slate-600 border border-slate-200 shadow-2xs">
                        <Info className="w-3 h-3 text-slate-400" />
                        {/* Render strictly as plain text (React escaping) */}
                        <span>{msg.body}</span>
                        <span className="text-slate-400 font-normal">({formatDate(msg.createdAt)})</span>
                      </div>
                    </div>
                  );
                }

                if (isAdmin) {
                  return (
                    <div key={msg.id || idx} className="flex items-start gap-3 justify-start max-w-2xl">
                      <div className="w-8 h-8 rounded-lg bg-indigo-600 text-white flex items-center justify-center shrink-0 mt-0.5 shadow-2xs">
                        <ShieldCheck className="w-4 h-4" />
                      </div>
                      <div className="bg-white border border-indigo-100 rounded-2xl rounded-tl-sm p-4 shadow-xs">
                        <div className="flex items-center justify-between gap-3 mb-1.5 pb-1 border-b border-indigo-50">
                          <span className="text-xs font-bold text-indigo-900 flex items-center gap-1">
                            <span>Support Specialist</span>
                            <span className="text-[10px] uppercase font-bold px-1.5 py-0.2 rounded bg-indigo-50 text-indigo-700">SupportIQ</span>
                          </span>
                          <span className="text-[10px] text-slate-400">{formatDate(msg.createdAt)}</span>
                        </div>
                        {/* Render body strictly as plain text */}
                        <p className="text-sm text-slate-800 whitespace-pre-wrap">{msg.body}</p>
                      </div>
                    </div>
                  );
                }

                // Customer message
                return (
                  <div key={msg.id || idx} className="flex items-start gap-3 justify-end max-w-2xl ml-auto">
                    <div className="bg-indigo-50/70 border border-indigo-200/60 rounded-2xl rounded-tr-sm p-4 shadow-xs">
                      <div className="flex items-center justify-between gap-3 mb-1.5 pb-1 border-b border-indigo-100/50">
                        <span className="text-xs font-bold text-slate-900">You</span>
                        <span className="text-[10px] text-slate-400">{formatDate(msg.createdAt)}</span>
                      </div>
                      {/* Render body strictly as plain text */}
                      <p className="text-sm text-slate-800 whitespace-pre-wrap">{msg.body}</p>
                    </div>
                    <div className="w-8 h-8 rounded-lg bg-slate-200 text-slate-700 flex items-center justify-center shrink-0 mt-0.5">
                      <UserIcon className="w-4 h-4" />
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            {/* Reply Section */}
            <div className="p-4 sm:p-5 border-t border-slate-200 bg-white">
              {actionError && (
                <div className="mb-3 p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                  <span>{actionError}</span>
                </div>
              )}

              {isClosed ? (
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-slate-500 text-center text-xs flex items-center justify-center gap-2">
                  <Lock className="w-4 h-4 text-slate-400" />
                  <span>This ticket is closed. Further replies are disabled. If you need new assistance, please raise a new ticket.</span>
                </div>
              ) : (
                <form onSubmit={handleSendReply}>
                  {isResolved && (
                    <div className="mb-3 p-2.5 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-center gap-2">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                      <span>This ticket is marked resolved. Sending a reply will automatically reopen it to Open.</span>
                    </div>
                  )}

                  <div className="relative">
                    <textarea
                      rows={3}
                      value={replyText}
                      onChange={(e) => setReplyText(e.target.value)}
                      maxLength={2000}
                      disabled={isSubmitting}
                      placeholder="Type a follow-up reply..."
                      className="w-full px-3.5 py-2.5 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 resize-none pr-24"
                    />
                    <div className="absolute right-3 bottom-3 flex items-center gap-2">
                      <span className={`text-[10px] ${replyText.length > 2000 ? 'text-rose-600 font-bold' : 'text-slate-400'}`}>
                        {replyText.length}/2000
                      </span>
                      <button
                        type="submit"
                        disabled={!replyText.trim() || isSubmitting}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:bg-slate-300 rounded-lg shadow-xs transition-colors cursor-pointer"
                      >
                        {isSubmitting ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <>
                            <Send className="w-3.5 h-3.5" />
                            <span>Reply</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                </form>
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default CustomerTicketDetailPage;
