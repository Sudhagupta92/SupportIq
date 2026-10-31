import React from 'react';
import { AlertCircle, Clock, CheckCircle2, Archive } from 'lucide-react';

const STATUS_CONFIG = {
  OPEN: {
    label: 'Open',
    classes: 'bg-sky-50 text-sky-700 border-sky-200 ring-sky-300',
    icon: AlertCircle,
  },
  IN_PROGRESS: {
    label: 'In Progress',
    classes: 'bg-amber-50 text-amber-700 border-amber-200 ring-amber-300',
    icon: Clock,
  },
  RESOLVED: {
    label: 'Resolved',
    classes: 'bg-emerald-50 text-emerald-700 border-emerald-200 ring-emerald-300',
    icon: CheckCircle2,
  },
  CLOSED: {
    label: 'Closed',
    classes: 'bg-slate-100 text-slate-600 border-slate-200 ring-slate-300',
    icon: Archive,
  },
};

const TicketStatusBadge = ({ status, size = 'sm' }) => {
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.OPEN;
  const Icon = config.icon;

  const sizeClasses = size === 'lg'
    ? 'px-3 py-1 text-xs gap-1.5'
    : 'px-2 py-0.5 text-[11px] gap-1';

  return (
    <span
      className={`inline-flex items-center font-bold uppercase tracking-wider rounded-full border ring-1 ${config.classes} ${sizeClasses}`}
    >
      <Icon className={size === 'lg' ? 'w-3.5 h-3.5' : 'w-3 h-3'} />
      <span>{config.label}</span>
    </span>
  );
};

export default TicketStatusBadge;
