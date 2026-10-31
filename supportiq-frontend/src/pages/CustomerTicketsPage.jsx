import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import TicketStatusBadge from '../components/TicketStatusBadge';
import TicketPriorityBadge from '../components/TicketPriorityBadge';
import CreateTicketModal from '../components/CreateTicketModal';
import Spinner from '../components/Spinner';
import Alert from '../components/Alert';
import { getMyTicketsApi } from '../api/ticketApi';
import {
  LifeBuoy,
  Plus,
  ChevronRight,
  Package,
  Clock,
  Inbox,
  RefreshCw,
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

const CustomerTicketsPage = () => {
  const navigate = useNavigate();
  const [tickets, setTickets] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const pageSize = 10;

  const fetchTickets = useCallback(async (targetPage = 0) => {
    setLoading(true);
    setError(null);
    try {
      const data = await getMyTicketsApi(targetPage, pageSize);
      setTickets(data.content || []);
      setPage(data.page || 0);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      console.error('Failed to load tickets:', err);
      setError(err.response?.data?.message || 'Unable to load tickets. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchTickets(page);
  }, [fetchTickets, page]);

  const handleTicketCreated = (newTicket) => {
    fetchTickets(0);
    if (newTicket?.ticketNumber) {
      navigate(`/tickets/${encodeURIComponent(newTicket.ticketNumber)}`);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      <Navbar />

      <main className="flex-1 max-w-6xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 sm:py-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
          <div>
            <div className="flex items-center gap-2.5">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-500 flex items-center justify-center text-white shadow-sm shadow-indigo-200">
                <LifeBuoy className="w-5 h-5" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-slate-900 leading-tight">
                  My Support Tickets
                </h1>
                <p className="text-xs text-slate-500">
                  Track ongoing inquiries, agent responses, and escalations
                </p>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => fetchTickets(page)}
              disabled={loading}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 bg-white hover:bg-slate-50 border border-slate-200 rounded-lg transition-colors cursor-pointer"
              title="Refresh tickets"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              <span className="hidden sm:inline">Refresh</span>
            </button>

            <button
              type="button"
              onClick={() => setIsModalOpen(true)}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm shadow-indigo-200 rounded-lg transition-colors cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Raise Ticket</span>
            </button>
          </div>
        </div>

        {/* Error Banner */}
        {error && (
          <div className="mb-6">
            <Alert type="error" title="Error" message={error} />
          </div>
        )}

        {/* Content Area */}
        {loading && tickets.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-12 flex items-center justify-center min-h-[300px]">
            <Spinner text="Loading tickets..." />
          </div>
        ) : tickets.length === 0 ? (
          /* Empty State */
          <div className="bg-white rounded-2xl border border-slate-200 p-12 text-center max-w-lg mx-auto shadow-sm">
            <div className="w-14 h-14 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center mx-auto mb-4">
              <Inbox className="w-7 h-7" />
            </div>
            <h2 className="text-base font-bold text-slate-900 mb-1">
              No Support Tickets Yet
            </h2>
            <p className="text-xs text-slate-500 mb-6 max-w-sm mx-auto">
              You haven't opened any support tickets. If you need assistance with an order or product, raise a ticket below.
            </p>
            <button
              type="button"
              onClick={() => setIsModalOpen(true)}
              className="inline-flex items-center gap-2 px-5 py-2.5 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-sm transition-colors cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Create Your First Ticket</span>
            </button>
          </div>
        ) : (
          /* Tickets List / Table */
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            {/* Desktop Table View */}
            <div className="hidden md:block overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-400 border-b border-slate-200">
                  <tr>
                    <th scope="col" className="px-6 py-3.5">Ticket #</th>
                    <th scope="col" className="px-6 py-3.5">Subject</th>
                    <th scope="col" className="px-6 py-3.5">Category</th>
                    <th scope="col" className="px-6 py-3.5">Status</th>
                    <th scope="col" className="px-6 py-3.5">Priority</th>
                    <th scope="col" className="px-6 py-3.5">Last Updated</th>
                    <th scope="col" className="px-6 py-3.5 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {tickets.map((t) => (
                    <tr
                      key={t.id || t.ticketNumber}
                      className="hover:bg-slate-50/70 transition-colors group cursor-pointer"
                      onClick={() => navigate(`/tickets/${encodeURIComponent(t.ticketNumber)}`)}
                    >
                      <td className="px-6 py-4 font-mono font-bold text-xs text-indigo-600 whitespace-nowrap">
                        <Link
                          to={`/tickets/${encodeURIComponent(t.ticketNumber)}`}
                          className="hover:underline flex items-center gap-1.5"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <span>{t.ticketNumber}</span>
                        </Link>
                      </td>
                      <td className="px-6 py-4">
                        <div className="font-semibold text-slate-900 line-clamp-1 max-w-xs sm:max-w-md">
                          {t.subject}
                        </div>
                        {t.relatedOrderNumber && (
                          <div className="inline-flex items-center gap-1 text-[11px] text-amber-700 mt-0.5">
                            <Package className="w-3 h-3 text-amber-600 shrink-0" />
                            <span>Order #{t.relatedOrderNumber}</span>
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-xs font-medium text-slate-700">
                        {formatCategory(t.category)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <TicketStatusBadge status={t.status} />
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <TicketPriorityBadge priority={t.priority} />
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-xs text-slate-400">
                        <div className="flex items-center gap-1">
                          <Clock className="w-3 h-3 text-slate-400" />
                          <span>{formatDate(t.updatedAt || t.createdAt)}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-right">
                        <Link
                          to={`/tickets/${encodeURIComponent(t.ticketNumber)}`}
                          className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 group-hover:text-indigo-800"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <span>View</span>
                          <ChevronRight className="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile Cards View */}
            <div className="md:hidden divide-y divide-slate-100">
              {tickets.map((t) => (
                <Link
                  key={t.id || t.ticketNumber}
                  to={`/tickets/${encodeURIComponent(t.ticketNumber)}`}
                  className="block p-4 hover:bg-slate-50 transition-colors"
                >
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <span className="font-mono font-bold text-xs text-indigo-600">
                      {t.ticketNumber}
                    </span>
                    <TicketStatusBadge status={t.status} />
                  </div>
                  <h3 className="text-sm font-semibold text-slate-900 mb-1.5 line-clamp-2">
                    {t.subject}
                  </h3>
                  <div className="flex flex-wrap items-center gap-2 mb-2 text-xs">
                    <span className="text-slate-500 font-medium">
                      {formatCategory(t.category)}
                    </span>
                    <span className="text-slate-300">•</span>
                    <TicketPriorityBadge priority={t.priority} />
                    {t.relatedOrderNumber && (
                      <>
                        <span className="text-slate-300">•</span>
                        <span className="inline-flex items-center gap-1 text-amber-700 font-medium">
                          <Package className="w-3 h-3 text-amber-600" />
                          <span>Order #{t.relatedOrderNumber}</span>
                        </span>
                      </>
                    )}
                  </div>
                  <div className="flex items-center justify-between text-[11px] text-slate-400 pt-2 border-t border-slate-100">
                    <span className="flex items-center gap-1">
                      <Clock className="w-3 h-3 text-slate-400" />
                      <span>{formatDate(t.updatedAt || t.createdAt)}</span>
                    </span>
                    <span className="font-semibold text-indigo-600 flex items-center gap-0.5">
                      <span>Details</span>
                      <ChevronRight className="w-3.5 h-3.5" />
                    </span>
                  </div>
                </Link>
              ))}
            </div>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="px-6 py-4 bg-slate-50/80 border-t border-slate-200 flex items-center justify-between text-xs text-slate-600">
                <div>
                  Showing page <span className="font-bold text-slate-900">{page + 1}</span> of{' '}
                  <span className="font-bold text-slate-900">{totalPages}</span> ({totalElements} tickets)
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0 || loading}
                    className="px-3 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-100 disabled:opacity-50 disabled:cursor-not-allowed font-medium transition-colors"
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1 || loading}
                    className="px-3 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-100 disabled:opacity-50 disabled:cursor-not-allowed font-medium transition-colors"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </main>

      {/* Create Ticket Modal */}
      <CreateTicketModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={handleTicketCreated}
      />
    </div>
  );
};

export default CustomerTicketsPage;
