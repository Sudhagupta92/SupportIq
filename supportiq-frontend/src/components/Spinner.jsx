import React from 'react';

/**
 * Reusable Spinner Component
 * 
 * Supports inline rendering (e.g. inside a submit button) or full-screen loading state.
 */
const Spinner = ({ size = 'md', className = '', fullScreen = false, text = 'Loading...' }) => {
  const sizeClasses = {
    sm: 'w-4 h-4 border-2',
    md: 'w-6 h-6 border-2',
    lg: 'w-10 h-10 border-3',
  }[size] || 'w-6 h-6 border-2';

  const spinnerElement = (
    <div
      role="status"
      aria-label={text}
      className={`inline-block animate-spin rounded-full border-solid border-current border-r-transparent align-[-0.125em] motion-reduce:animate-[spin_1.5s_linear_infinite] ${sizeClasses} ${className}`}
    >
      <span className="sr-only">{text}</span>
    </div>
  );

  if (fullScreen) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-slate-50">
        <div className="flex flex-col items-center gap-3">
          <div className="w-12 h-12 rounded-2xl bg-indigo-600 flex items-center justify-center shadow-lg shadow-indigo-200 mb-2">
            <span className="text-white font-bold text-xl tracking-tight">IQ</span>
          </div>
          <div className="inline-block animate-spin rounded-full border-4 border-indigo-600 border-r-transparent w-10 h-10" />
          <p className="text-sm font-medium text-slate-500 animate-pulse">{text}</p>
        </div>
      </div>
    );
  }

  return spinnerElement;
};

export default Spinner;
