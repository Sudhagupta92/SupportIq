import React from 'react';
import { AlertCircle, CheckCircle2, Info, AlertTriangle, X } from 'lucide-react';

/**
 * Reusable Alert Component
 * Displays system messages, API errors, and confirmation banners.
 */
const Alert = ({
  type = 'info',
  title,
  message,
  errors = null,
  onClose,
  className = '',
}) => {
  if (!message && (!errors || Object.keys(errors).length === 0)) {
    return null;
  }

  const styles = {
    error: {
      container: 'bg-red-50 border-red-200 text-red-900',
      icon: <AlertCircle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />,
      titleColor: 'text-red-800',
      bodyColor: 'text-red-700',
    },
    success: {
      container: 'bg-emerald-50 border-emerald-200 text-emerald-900',
      icon: <CheckCircle2 className="w-5 h-5 text-emerald-500 shrink-0 mt-0.5" />,
      titleColor: 'text-emerald-800',
      bodyColor: 'text-emerald-700',
    },
    warning: {
      container: 'bg-amber-50 border-amber-200 text-amber-900',
      icon: <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />,
      titleColor: 'text-amber-800',
      bodyColor: 'text-amber-700',
    },
    info: {
      container: 'bg-blue-50 border-blue-200 text-blue-900',
      icon: <Info className="w-5 h-5 text-blue-500 shrink-0 mt-0.5" />,
      titleColor: 'text-blue-800',
      bodyColor: 'text-blue-700',
    },
  }[type] || styles.info;

  return (
    <div
      role="alert"
      className={`flex items-start gap-3 p-3.5 rounded-xl border text-sm transition-all duration-200 ${styles.container} ${className}`}
    >
      {styles.icon}
      <div className="flex-1 min-w-0">
        {title && <h4 className={`font-semibold mb-0.5 ${styles.titleColor}`}>{title}</h4>}
        {message && <p className={`leading-relaxed ${styles.bodyColor}`}>{message}</p>}
        {errors && typeof errors === 'object' && Object.keys(errors).length > 0 && (
          <ul className="mt-1.5 list-disc list-inside space-y-0.5 text-xs text-red-600">
            {Object.entries(errors).map(([field, errText]) => (
              <li key={field}>
                <span className="font-medium capitalize">{field}</span>: {errText}
              </li>
            ))}
          </ul>
        )}
      </div>
      {onClose && (
        <button
          type="button"
          onClick={onClose}
          aria-label="Dismiss alert"
          className="text-slate-400 hover:text-slate-600 transition-colors p-1 -mr-1 -mt-1 rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-400"
        >
          <X className="w-4 h-4" />
        </button>
      )}
    </div>
  );
};

export default Alert;
