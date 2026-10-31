import React, { useState, useEffect } from 'react';
import { createTicketApi } from '../api/ticketApi';
import { X, LifeBuoy, AlertCircle, Loader2 } from 'lucide-react';

const CATEGORIES = [
  { value: 'ORDER_ISSUE', label: 'Order Issue' },
  { value: 'REFUND', label: 'Refund' },
  { value: 'DAMAGED_ITEM', label: 'Damaged Item' },
  { value: 'DELIVERY', label: 'Delivery' },
  { value: 'ACCOUNT', label: 'Account' },
  { value: 'OTHER', label: 'Other' },
];

const PRIORITIES = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
];

const inferCategoryFromReason = (reason) => {
  if (!reason) return 'ORDER_ISSUE';
  const r = reason.toUpperCase();
  if (r.includes('REFUND')) return 'REFUND';
  if (r.includes('DAMAGE')) return 'DAMAGED_ITEM';
  if (r.includes('DELIVERY') || r.includes('OVERDUE')) return 'DELIVERY';
  return 'ORDER_ISSUE';
};

const inferDefaultSubject = (reason, description) => {
  if (reason) {
    if (reason.includes('REFUND')) return 'Refund inquiry';
    if (reason.includes('DAMAGE')) return 'Damaged item complaint';
    if (reason.includes('ORDER_CANCELLED')) return 'Cancelled order inquiry';
    if (reason.includes('OVERDUE')) return 'Delayed delivery inquiry';
  }
  if (description) {
    const clean = description.trim();
    if (clean.length <= 60) return clean;
    return clean.slice(0, 57) + '...';
  }
  return 'Customer Support Inquiry';
};

const CreateTicketModal = ({
  isOpen,
  onClose,
  initialData = {},
  onSuccess,
}) => {
  const [subject, setSubject] = useState('');
  const [category, setCategory] = useState('ORDER_ISSUE');
  const [priority, setPriority] = useState('MEDIUM');
  const [relatedOrderNumber, setRelatedOrderNumber] = useState('');
  const [description, setDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (isOpen) {
      const desc = (initialData.description || '').trim();
      const reason = initialData.escalation?.reason || '';
      const initialCat = inferCategoryFromReason(reason);
      const initialSubj = inferDefaultSubject(reason, desc);
      const initialPri = initialData.escalation?.priority || 'MEDIUM';
      const orderNum = initialData.relatedOrderNumber || '';

      setSubject(initialSubj.slice(0, 120));
      setCategory(initialCat);
      setPriority(initialPri);
      setRelatedOrderNumber(orderNum);
      setDescription(desc.slice(0, 2000));
      setErrorMessage(null);
      setIsSubmitting(false);
    }
  }, [isOpen, initialData]);

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage(null);

    const trimmedSubject = subject.trim();
    const trimmedDesc = description.trim();

    if (!trimmedSubject) {
      setErrorMessage('Subject is required.');
      return;
    }
    if (trimmedSubject.length > 120) {
      setErrorMessage('Subject must not exceed 120 characters.');
      return;
    }
    if (!trimmedDesc) {
      setErrorMessage('Description is required.');
      return;
    }
    if (trimmedDesc.length > 2000) {
      setErrorMessage('Description must not exceed 2000 characters.');
      return;
    }

    setIsSubmitting(true);
    try {
      const payload = {
        subject: trimmedSubject,
        description: trimmedDesc,
        category,
        priority,
        relatedOrderNumber: relatedOrderNumber.trim() || null,
      };

      const result = await createTicketApi(payload);
      if (onSuccess) {
        onSuccess(result);
      }
      onClose();
    } catch (err) {
      console.error('Failed to create ticket:', err);
      const msg = err.response?.data?.message || 'Failed to create support ticket. Please try again.';
      setErrorMessage(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-xs flex items-center justify-center p-4">
      <div
        className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-200 relative animate-in fade-in zoom-in-95 duration-150"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ticket-modal-title"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-100 mb-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-violet-100 text-violet-700 flex items-center justify-center">
              <LifeBuoy className="w-5 h-5" />
            </div>
            <div>
              <h2 id="ticket-modal-title" className="text-lg font-bold text-slate-900 leading-tight">
                Create Support Ticket
              </h2>
              <p className="text-xs text-slate-500">
                A human support agent will review and follow up with you
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
            aria-label="Close modal"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Error Alert */}
        {errorMessage && (
          <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-2">
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
            <span>{errorMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Subject */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="ticket-subject" className="block text-xs font-bold uppercase tracking-wider text-slate-700">
                Subject <span className="text-rose-500">*</span>
              </label>
              <span className={`text-[11px] ${subject.length > 120 ? 'text-rose-600 font-bold' : 'text-slate-400'}`}>
                {subject.length}/120
              </span>
            </div>
            <input
              id="ticket-subject"
              type="text"
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              maxLength={120}
              required
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              placeholder="e.g. Refund inquiry for order #1004"
            />
          </div>

          {/* Category & Priority Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="ticket-category" className="block text-xs font-bold uppercase tracking-wider text-slate-700 mb-1">
                Category <span className="text-rose-500">*</span>
              </label>
              <select
                id="ticket-category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 bg-white"
              >
                {CATEGORIES.map((c) => (
                  <option key={c.value} value={c.value}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="ticket-priority" className="block text-xs font-bold uppercase tracking-wider text-slate-700 mb-1">
                Priority
              </label>
              <select
                id="ticket-priority"
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 bg-white"
              >
                {PRIORITIES.map((p) => (
                  <option key={p.value} value={p.value}>
                    {p.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Related Order Number (Optional) */}
          <div>
            <label htmlFor="ticket-order-number" className="block text-xs font-bold uppercase tracking-wider text-slate-700 mb-1">
              Related Order # <span className="text-slate-400 text-[11px] font-normal">(optional)</span>
            </label>
            <input
              id="ticket-order-number"
              type="text"
              value={relatedOrderNumber}
              onChange={(e) => setRelatedOrderNumber(e.target.value.replace(/\D/g, '').slice(0, 12))}
              placeholder="e.g. 1004"
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
            <p className="text-[11px] text-slate-400 mt-1">
              If an open ticket already exists for this order, your note will be appended to it.
            </p>
          </div>

          {/* Description */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="ticket-description" className="block text-xs font-bold uppercase tracking-wider text-slate-700">
                Description <span className="text-rose-500">*</span>
              </label>
              <span className={`text-[11px] ${description.length > 2000 ? 'text-rose-600 font-bold' : 'text-slate-400'}`}>
                {description.length}/2000
              </span>
            </div>
            <textarea
              id="ticket-description"
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={2000}
              required
              placeholder="Describe your issue or question in detail..."
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 resize-none"
            />
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="px-4 py-2 text-sm font-semibold text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:bg-indigo-400 rounded-lg shadow-sm transition-colors cursor-pointer"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Submitting...</span>
                </>
              ) : (
                <span>Submit Ticket</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateTicketModal;
