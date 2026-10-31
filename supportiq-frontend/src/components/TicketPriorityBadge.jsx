import React from 'react';

const PRIORITY_CONFIG = {
  LOW: {
    label: 'Low',
    classes: 'bg-slate-100 text-slate-700 border-slate-200',
    dot: 'bg-slate-400',
  },
  MEDIUM: {
    label: 'Medium',
    classes: 'bg-amber-50 text-amber-800 border-amber-200',
    dot: 'bg-amber-500',
  },
  HIGH: {
    label: 'High',
    classes: 'bg-rose-50 text-rose-800 border-rose-200 font-semibold',
    dot: 'bg-rose-500',
  },
};

const TicketPriorityBadge = ({ priority }) => {
  const config = PRIORITY_CONFIG[priority] || PRIORITY_CONFIG.MEDIUM;

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs border ${config.classes}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${config.dot}`} />
      <span>{config.label}</span>
    </span>
  );
};

export default TicketPriorityBadge;
