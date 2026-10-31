import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import TicketStatusBadge from '../components/TicketStatusBadge';
import TicketPriorityBadge from '../components/TicketPriorityBadge';
import Spinner from '../components/Spinner';
import Alert from '../components/Alert';
import { getAdminTicketsApi } from '../api/ticketApi';
import {
  ShieldCheck,
  Search,
  X,
  ChevronRight,
  Package,
  RefreshCw,
  Inbox,
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

const AdminTicketsPage = () => {
  const navigate = useNavigate();
  const [tickets, setTickets] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filters
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [activeSearch, setActiveSearch] = useState('');

  const pageSize = 15;

  const fetchTickets = useCallback(async (targetPage = 0) => {
    setLoading(true);
    setError(null);
    try {
      const data = await getAdminTicketsApi({
        page: targetPage,
        size: pageSize,
        status: statusFilter || undefined,
        priority: priorityFilter || undefined,
        category: categoryFilter || undefined,
        search: activeSearch || undefined,
      });

      setTickets(data.content || []);
      setPage(data.page || 0);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      console.error('Failed to load admin tickets:', err);
      setError(err.response?.data?.message || 'Unable to load tickets.');
    } finally {
      setLoading(false);
    }
  }, [statusFilter, priorityFilter, categoryFilter, activeSearch]);

  useEffect(() => {
    fetchTickets(page);
  }, [fetchTickets, page]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setActiveSearch(searchTerm);
    setPage(0);
  };

  const handleClearFilters = () => {
    setStatusFilter('');
    setPriorityFilter('');
    setCategoryFilter('');
    setSearchTerm('');
    setActiveSearch('');
    setPage(0);
  };

  const hasActiveFilters = Boolean(statusFilter || priorityFilter || categoryFilter || activeSearch);

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      <Navbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 sm:py-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-purple-100 text-purple-700 flex items-center justify-center shadow-xs">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-2xl font-bold text-slate-900 leading-tight">
                  Admin Support Tickets
                </h1>
                <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-purple-100 text-purple-800">
                  Agent Console
                </span>
              </div>
              <p className="text-xs text-slate-500">
                Manage, triage, and resolve incoming customer inquiries across SupportIQ
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => fetchTickets(page)}
              disabled={loading}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 bg-white hover:bg-slate-50 border border-slate-200 rounded-lg transition-colors cursor-pointer shadow-2xs"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        {/* Filter & Search Bar */}
        <div className="bg-white rounded-2xl border border-slate-200 p-4 mb-6 shadow-xs">
          <form onSubmit={handleSearchSubmit} className="flex flex-col lg:flex-row gap-3">
            {/* Search Input */}
            <div className="flex-1 relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search ticket # or subject..."
                className="w-full pl-9 pr-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              />
            </div>

            {/* Filter Dropdowns */}
            <div className="flex flex-wrap items-center gap-2">
              {/* Status */}
              <select
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setPage(0);
                }}
                className="px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                aria-label="Filter by Status"
              >
                <option value="">All Statuses</option>
                <option value="OPEN">Open</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="RESOLVED">Resolved</option>
                <option value="CLOSED">Closed</option>
              </select>

              {/* Priority */}
              <select
                value={priorityFilter}
                onChange={(e) => {
                  setPriorityFilter(e.target.value);
                  setPage(0);
                }}
                className="px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                aria-label="Filter by Priority"
              >
                <option value="">All Priorities</option>
                <option value="HIGH">High Priority</option>
                <option value="MEDIUM">Medium Priority</option>
                <option value="LOW">Low Priority</option>
              </select>

              {/* Category */}
              <select
                value={categoryFilter}
                onChange={(e) => {
                  setCategoryFilter(e.target.value);
                  setPage(0);
                }}
                className="px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                aria-label="Filter by Category"
              >
                <option value="">All Categories</option>
                <option value="ORDER_ISSUE">Order Issue</option>
                <option value="REFUND">Refund</option>
                <option value="DAMAGED_ITEM">Damaged Item</option>
                <option value="DELIVERY">Delivery</option>
                <option value="ACCOUNT">Account</option>
                <option value="OTHER">Other</option>
              </select>

              <button
                type="submit"
                className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-2xs transition-colors cursor-pointer"
              >
                Search
              </button>

              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={handleClearFilters}
                  className="inline-flex items-center gap-1 px-3 py-2 text-xs font-semibold text-slate-500 hover:text-slate-800 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer"
                  title="Clear all filters"
                >
                  <X className="w-3.5 h-3.5" />
                  <span>Reset</span>
                </button>
              )}
            </div>
          </form>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="mb-6">
            <Alert type="error" title="Error" message={error} />
          </div>
        )}

        {/* Tickets Content Table */}
        {loading && tickets.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-12 flex items-center justify-center min-h-[350px]">
            <Spinner text="Loading tickets..." />
          </div>
        ) : tickets.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-12 text-center max-w-lg mx-auto shadow-sm">
            <div className="w-14 h-14 rounded-2xl bg-slate-100 text-slate-400 flex items-center justify-center mx-auto mb-3">
              <Inbox className="w-7 h-7" />
            </div>
            <h2 className="text-base font-bold text-slate-900 mb-1">No Tickets Found</h2>
            <p className="text-xs text-slate-500 mb-4">
              {hasActiveFilters
                ? 'No tickets match the selected filters or search keyword.'
                : 'There are currently no tickets registered in the system.'}
            </p>
            {hasActiveFilters && (
              <button
                type="button"
                onClick={handleClearFilters}
                className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-indigo-600 hover:text-indigo-800 bg-indigo-50 rounded-lg transition-colors"
              >
                <X className="w-3.5 h-3.5" />
                <span>Clear Filters</span>
              </button>
            )}
          </div>
        ) : (
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-400 border-b border-slate-200">
                  <tr>
                    <th scope="col" className="px-5 py-3.5">Ticket #</th>
                    <th scope="col" className="px-5 py-3.5">Customer</th>
                    <th scope="col" className="px-5 py-3.5">Subject</th>
                    <th scope="col" className="px-5 py-3.5">Category</th>
                    <th scope="col" className="px-5 py-3.5">Status</th>
                    <th scope="col" className="px-5 py-3.5">Priority</th>
                    <th scope="col" className="px-5 py-3.5">Updated</th>
                    <th scope="col" className="px-5 py-3.5 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {tickets.map((t) => (
                    <tr
                      key={t.id || t.ticketNumber}
                      onClick={() => navigate(`/admin/tickets/${encodeURIComponent(t.ticketNumber)}`)}
                      className="hover:bg-slate-50/80 transition-colors group cursor-pointer"
                    >
                      <td className="px-5 py-4 font-mono font-bold text-xs text-indigo-600 whitespace-nowrap">
                        <Link
                          to={`/admin/tickets/${encodeURIComponent(t.ticketNumber)}`}
                          onClick={(e) => e.stopPropagation()}
                          className="hover:underline"
                        >
                          {t.ticketNumber}
                        </Link>
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap">
                        <div className="font-semibold text-xs text-slate-900">{t.customerName || 'Customer'}</div>
                        <div className="text-[11px] text-slate-400">{t.customerEmail}</div>
                      </td>
                      <td className="px-5 py-4">
                        <div className="font-semibold text-slate-900 line-clamp-1 max-w-xs lg:max-w-md">
                          {t.subject}
                        </div>
                        {t.relatedOrderNumber && (
                          <div className="inline-flex items-center gap-1 text-[11px] text-amber-700 mt-0.5">
                            <Package className="w-3 h-3 text-amber-600 shrink-0" />
                            <span>Order #{t.relatedOrderNumber}</span>
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap text-xs text-slate-700 font-medium">
                        {formatCategory(t.category)}
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap">
                        <TicketStatusBadge status={t.status} />
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap">
                        <TicketPriorityBadge priority={t.priority} />
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap text-xs text-slate-400">
                        {formatDate(t.updatedAt || t.createdAt)}
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap text-right">
                        <Link
                          to={`/admin/tickets/${encodeURIComponent(t.ticketNumber)}`}
                          onClick={(e) => e.stopPropagation()}
                          className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 group-hover:text-indigo-800"
                        >
                          <span>Manage</span>
                          <ChevronRight className="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="px-6 py-4 bg-slate-50/80 border-t border-slate-200 flex items-center justify-between text-xs text-slate-600">
                <div>
                  Showing page <span className="font-bold text-slate-900">{page + 1}</span> of{' '}
                  <span className="font-bold text-slate-900">{totalPages}</span> ({totalElements} total tickets)
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
    </div>
  );
};

export default AdminTicketsPage;
