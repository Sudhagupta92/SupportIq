import React, { useState, useRef, useEffect } from 'react';
import { Link } from 'react-router-dom';
import Navbar from '../components/Navbar';
import CreateTicketModal from '../components/CreateTicketModal';
import { useAuth } from '../context/AuthContext';
import { sendChatMessageApi } from '../api/chatApi';
import {
  Bot,
  Send,
  Trash2,
  Sparkles,
  FileText,
  AlertCircle,
  HelpCircle,
  Loader2,
  X,
  User as UserIcon,
  Package,
  Info,
  LifeBuoy,
  CheckCircle2,
} from 'lucide-react';

/**
 * Example starter questions for the customer welcome view.
 */
const EXAMPLE_QUESTIONS = [
  'What is your return policy for damaged items?',
  'How long does standard shipping take?',
  'Mera order 1001 kahan hai?',
  'Show my recent orders',
];

const generateId = () => {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
};

const formatTime = () => {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

/**
 * ChatPage Component (Module 4 RAG Chat)
 * 
 * Accessible by all authenticated users (CUSTOMER and ADMIN).
 * Features:
 * - Single-turn RAG retrieval against indexed policy documents
 * - Instant citation of distinct (filename, page) source chunks
 * - Neutral styling when policy is not available (answered=false)
 * - Enter to send, Shift+Enter for new line
 * - Auto-scroll to latest message
 * - Clear chat button
 * - In-memory conversation state (resets on page refresh)
 * - 1000-character input validation with live character counter
 * - Comprehensive error handling (429 rate limit, 502 model unavailable, network errors)
 */
const ChatPage = () => {
  const { user } = useAuth();
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showSlowHint, setShowSlowHint] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  // Ticket creation modal state
  const [isTicketModalOpen, setIsTicketModalOpen] = useState(false);
  const [ticketModalData, setTicketModalData] = useState({});

  const messagesEndRef = useRef(null);
  const textareaRef = useRef(null);
  const isSendingRef = useRef(false);
  const slowHintTimerRef = useRef(null);

  // Auto-scroll to the newest message whenever messages or loading state changes
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  // Focus textarea on initial load
  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

  // Clean up timer on unmount
  useEffect(() => {
    return () => {
      if (slowHintTimerRef.current) {
        clearTimeout(slowHintTimerRef.current);
      }
    };
  }, []);

  const handleSendMessage = async (textToSend) => {
    if (isSendingRef.current || isLoading) return;

    const query = (textToSend || inputMessage).trim();
    if (!query) return;

    if (query.length > 1000) {
      setErrorMessage('Your message exceeds the 1000-character limit. Please shorten your question.');
      return;
    }

    isSendingRef.current = true;
    setErrorMessage(null);

    // Append user message immediately
    const userMessage = {
      id: generateId(),
      role: 'user',
      text: query,
      timestamp: formatTime(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputMessage('');
    setIsLoading(true);
    setShowSlowHint(false);

    // Start 8-second timer for slow response hint
    if (slowHintTimerRef.current) {
      clearTimeout(slowHintTimerRef.current);
    }
    slowHintTimerRef.current = setTimeout(() => {
      setShowSlowHint(true);
    }, 8000);

    try {
      const response = await sendChatMessageApi(query);

      // Assemble sources and ensure TICKET chip is represented if created via tool
      let sources = response.sources ? [...response.sources] : [];
      if (response.ticket && response.ticket.ticketNumber) {
        const ticketLabel = `Ticket #${response.ticket.ticketNumber}`;
        if (!sources.some((s) => s.type === 'TICKET' || s.label === ticketLabel)) {
          sources.push({
            type: 'TICKET',
            label: ticketLabel,
            ticketNumber: response.ticket.ticketNumber,
          });
        }
      }

      const assistantMessage = {
        id: generateId(),
        role: 'assistant',
        text: response.answer,
        answered: response.answered,
        sources,
        escalation: response.escalation || null,
        ticket: response.ticket || null,
        userQuery: query,
        timestamp: formatTime(),
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err) {
      console.error('Chat request failed:', err);
      const isTimeout =
        err.code === 'ECONNABORTED' ||
        err.message?.toLowerCase().includes('timeout') ||
        err.response?.status === 504;

      // Extract backend error message if present (429, 502, 504, 400)
      const backendMessage =
        (isTimeout
          ? 'The assistant took too long to respond. Please try again.'
          : err.response?.data?.message) ||
        'Unable to reach the SupportIQ assistant. Please check your network connection.';

      setErrorMessage(backendMessage);
    } finally {
      if (slowHintTimerRef.current) {
        clearTimeout(slowHintTimerRef.current);
        slowHintTimerRef.current = null;
      }
      setShowSlowHint(false);
      setIsLoading(false);
      isSendingRef.current = false;
      // Re-focus the input textarea
      setTimeout(() => textareaRef.current?.focus(), 100);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!isLoading && !isSendingRef.current) {
        handleSendMessage();
      }
    }
  };

  const handleClearChat = () => {
    setMessages([]);
    setErrorMessage(null);
    setInputMessage('');
    textareaRef.current?.focus();
  };

  const handleSelectExample = (question) => {
    if (!isLoading && !isSendingRef.current) {
      handleSendMessage(question);
    }
  };

  const extractRelatedOrder = (msg) => {
    if (msg?.sources) {
      for (const s of msg.sources) {
        if (s.type === 'ORDER' && s.label) {
          const m = s.label.match(/\b(\d{3,12})\b/);
          if (m) return m[1];
        }
      }
    }
    if (msg?.userQuery) {
      const m = msg.userQuery.match(/\b(10\d\d|\d{3,12})\b/);
      if (m) return m[1];
    }
    return '';
  };

  const handleOpenTicketModal = (msg) => {
    const relatedOrder = extractRelatedOrder(msg);
    setTicketModalData({
      messageId: msg.id,
      description: msg.userQuery || '',
      escalation: msg.escalation || {},
      relatedOrderNumber: relatedOrder,
    });
    setIsTicketModalOpen(true);
  };

  const handleTicketCreatedSuccess = (result) => {
    if (!result) return;
    setMessages((prev) =>
      prev.map((m) => {
        if (m.id === ticketModalData.messageId) {
          const ticketLabel = `Ticket #${result.ticketNumber}`;
          const updatedSources = [...(m.sources || [])];
          if (!updatedSources.some((s) => s.type === 'TICKET' || s.label === ticketLabel)) {
            updatedSources.push({
              type: 'TICKET',
              label: ticketLabel,
              ticketNumber: result.ticketNumber,
            });
          }
          return {
            ...m,
            ticket: {
              ticketNumber: result.ticketNumber,
              subject: result.subject,
              existing: result.existing,
            },
            ticketCreated: true,
            sources: updatedSources,
          };
        }
        return m;
      })
    );
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      <Navbar />

      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 py-4 sm:py-6 flex flex-col">
        {/* Chat Window Container */}
        <div className="flex-1 bg-white rounded-2xl sm:rounded-3xl border border-slate-200/90 shadow-xl shadow-slate-200/50 flex flex-col overflow-hidden min-h-[550px] max-h-[calc(100vh-8.5rem)]">
          {/* Header Bar */}
          <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between bg-white/95 backdrop-blur-sm sticky top-0 z-10">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-500 flex items-center justify-center text-white shadow-sm shadow-indigo-200">
                <Bot className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="text-base font-bold text-slate-900 leading-tight">
                    Support Assistant
                  </h1>
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                    Online
                  </span>
                </div>
                <p className="text-xs text-slate-500">
                  Answers verified strictly from official store policy documents
                </p>
              </div>
            </div>

            {/* Clear Chat Button */}
            {messages.length > 0 && (
              <button
                type="button"
                onClick={handleClearChat}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-red-600 hover:bg-red-50 border border-slate-200 hover:border-red-200 rounded-lg transition-colors cursor-pointer"
                title="Clear conversation"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Clear chat</span>
              </button>
            )}
          </div>

          {/* Messages Scroll Area */}
          <div className="flex-1 overflow-y-auto px-4 sm:px-6 py-6 space-y-5">
            {messages.length === 0 ? (
              /* Welcome Screen with Example Questions */
              <div className="h-full flex flex-col items-center justify-center text-center max-w-xl mx-auto py-8">
                <div className="w-14 h-14 rounded-2xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600 mb-4 shadow-sm">
                  <Sparkles className="w-7 h-7" />
                </div>
                <h2 className="text-xl sm:text-2xl font-extrabold text-slate-900 tracking-tight">
                  Welcome to SupportIQ Assistant
                </h2>
                <p className="mt-2 text-sm text-slate-600 leading-relaxed">
                  Hi <span className="font-semibold text-slate-800">{user?.name || 'there'}</span>! Ask any question regarding store policies, refunds, shipping, or returns. I'll search our official policy documents and answer with cited sources.
                </p>

                {/* Example Questions */}
                <div className="mt-6 w-full space-y-2">
                  <span className="text-xs font-bold uppercase tracking-wider text-slate-400 block mb-3">
                    Suggested Questions
                  </span>
                    {EXAMPLE_QUESTIONS.map((question, index) => (
                    <button
                      key={index}
                      type="button"
                      disabled={isLoading}
                      onClick={() => handleSelectExample(question)}
                      className="w-full text-left p-3.5 rounded-xl border border-slate-200 hover:border-indigo-300 bg-white hover:bg-indigo-50/40 text-slate-700 hover:text-indigo-900 text-sm font-medium transition-all shadow-sm flex items-center justify-between group cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <span className="flex items-center gap-2.5">
                        <HelpCircle className="w-4 h-4 text-slate-400 group-hover:text-indigo-500 shrink-0" />
                        <span>{question}</span>
                      </span>
                      <Send className="w-3.5 h-3.5 text-slate-300 group-hover:text-indigo-600 opacity-0 group-hover:opacity-100 transition-opacity shrink-0" />
                    </button>
                  ))}
                </div>

                {/* Development Mode Demo Notice */}
                {import.meta.env.DEV && (
                  <div className="mt-5 p-3 rounded-xl bg-indigo-50/70 border border-indigo-100/80 text-indigo-900 text-xs text-left w-full flex items-start gap-2.5 shadow-xs">
                    <Info className="w-4 h-4 text-indigo-600 shrink-0 mt-0.5" />
                    <div className="text-[12px] leading-relaxed">
                      <span className="font-semibold text-indigo-950">Demo Accounts Available: </span>
                      <span className="text-slate-600">
                        Use <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-indigo-200 text-indigo-700">demo1@example.com</code> to <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-indigo-200 text-indigo-700">demo3@example.com</code> (password: <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-indigo-200 text-indigo-700">Demo@12345</code>) to test order tracking.
                      </span>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* Conversation Messages */
              messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex items-start gap-3 ${
                    msg.role === 'user' ? 'justify-end' : 'justify-start'
                  }`}
                >
                  {/* Assistant Avatar */}
                  {msg.role === 'assistant' && (
                    <div className="w-8 h-8 rounded-lg bg-indigo-100 text-indigo-700 flex items-center justify-center shrink-0 mt-0.5">
                      <Bot className="w-4 h-4" />
                    </div>
                  )}

                  <div className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'} max-w-[85%] sm:max-w-[75%]`}>
                    {/* Message Bubble */}
                    <div
                      className={`px-4 py-3 rounded-2xl shadow-sm text-sm leading-relaxed whitespace-pre-wrap break-words ${
                        msg.role === 'user'
                          ? 'bg-gradient-to-r from-indigo-600 to-indigo-700 text-white rounded-tr-sm'
                          : msg.answered === false
                          ? 'bg-amber-50/80 border border-amber-200/90 text-amber-950 rounded-tl-sm'
                          : 'bg-white border border-slate-200/90 text-slate-800 rounded-tl-sm'
                      }`}
                    >
                      {/* Notice Header for unanswered / below threshold messages */}
                      {msg.role === 'assistant' && msg.answered === false && (
                        <div className="flex items-center gap-1.5 text-amber-800 font-semibold text-xs mb-2 pb-1.5 border-b border-amber-200/70">
                          <AlertCircle className="w-3.5 h-3.5 text-amber-600 shrink-0" />
                          <span>Not Covered by Policy Documents</span>
                        </div>
                      )}

                      <p>{msg.text}</p>

                      {/* Friendly note when ticket already open */}
                      {msg.ticket?.existing && (
                        <div className="mt-2.5 p-2.5 rounded-xl bg-amber-50/80 border border-amber-200 text-amber-900 text-xs flex items-center gap-2">
                          <CheckCircle2 className="w-4 h-4 text-amber-600 shrink-0" />
                          <span>Ticket {msg.ticket.ticketNumber} is already open, we added your note.</span>
                        </div>
                      )}

                      {/* Escalation Prompt Box: shown when escalation suggested and no ticket created */}
                      {msg.role === 'assistant' &&
                        msg.escalation?.suggested === true &&
                        !msg.ticket &&
                        !msg.ticketCreated && (
                          <div className="mt-3 p-3 rounded-xl bg-violet-50/90 border border-violet-200/90 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 shadow-2xs">
                            <div className="flex items-center gap-2 text-violet-900">
                              <LifeBuoy className="w-4 h-4 text-violet-600 shrink-0" />
                              <span className="text-xs font-semibold">Need a human to look at this?</span>
                            </div>
                            <button
                              type="button"
                              onClick={() => handleOpenTicketModal(msg)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-violet-600 hover:bg-violet-700 text-white shadow-2xs transition-colors cursor-pointer"
                            >
                              <span>Create support ticket</span>
                            </button>
                          </div>
                      )}

                      {/* Source Citations */}
                      {msg.sources && msg.sources.length > 0 && (
                        <div className="mt-3 pt-2.5 border-t border-slate-100">
                          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 block mb-1.5">
                            Cited Sources:
                          </span>
                          <div className="flex flex-wrap gap-1.5">
                            {msg.sources.map((source, idx) => {
                              const isTicket =
                                source.type === 'TICKET' ||
                                (source.label && source.label.startsWith('Ticket #'));

                              if (isTicket) {
                                const tktNum =
                                  source.ticketNumber ||
                                  (source.label ? source.label.replace('Ticket #', '').trim() : '');
                                const targetUrl =
                                  user?.role === 'ADMIN'
                                    ? `/admin/tickets/${encodeURIComponent(tktNum)}`
                                    : `/tickets/${encodeURIComponent(tktNum)}`;

                                return (
                                  <Link
                                    key={idx}
                                    to={targetUrl}
                                    className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-violet-50 text-violet-900 border border-violet-200 hover:bg-violet-100/90 transition-colors shadow-2xs"
                                    title={`Support Ticket: ${source.label}`}
                                  >
                                    <LifeBuoy className="w-3.5 h-3.5 text-violet-600 shrink-0" />
                                    <span>{source.label}</span>
                                  </Link>
                                );
                              }

                              const isOrder =
                                source.type === 'ORDER' || (!source.filename && source.label);
                              if (isOrder) {
                                return (
                                  <span
                                    key={idx}
                                    className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-amber-50 text-amber-900 border border-amber-200 hover:bg-amber-100/80 transition-colors shadow-2xs"
                                    title={`Order Reference: ${source.label}`}
                                  >
                                    <Package className="w-3.5 h-3.5 text-amber-600 shrink-0" />
                                    <span>{source.label}</span>
                                  </span>
                                );
                              }
                              return (
                                <span
                                  key={idx}
                                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200/80 hover:bg-slate-200/70 transition-colors"
                                  title={`Document: ${source.filename}, Page: ${source.pageNumber || 'N/A'}${source.score ? ` (Score: ${source.score})` : ''}`}
                                >
                                  <FileText className="w-3 h-3 text-slate-500" />
                                  <span>
                                    {source.filename}
                                    {source.pageNumber ? `, page ${source.pageNumber}` : ''}
                                  </span>
                                </span>
                              );
                            })}
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Timestamp */}
                    <span className="text-[10px] text-slate-400 mt-1 px-1">
                      {msg.timestamp}
                    </span>
                  </div>

                  {/* Customer Avatar */}
                  {msg.role === 'user' && (
                    <div className="w-8 h-8 rounded-lg bg-slate-200 text-slate-700 flex items-center justify-center shrink-0 mt-0.5">
                      <UserIcon className="w-4 h-4" />
                    </div>
                  )}
                </div>
              ))
            )}

            {/* Typing / Loading Indicator */}
            {isLoading && (
              <div className="flex items-start gap-3 justify-start">
                <div className="w-8 h-8 rounded-lg bg-indigo-100 text-indigo-700 flex items-center justify-center shrink-0 mt-0.5">
                  <Bot className="w-4 h-4" />
                </div>
                <div className="bg-white border border-slate-200 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm flex flex-col gap-1.5">
                  <div className="flex items-center gap-2">
                    <Loader2 className="w-4 h-4 text-indigo-600 animate-spin" />
                    <span className="text-xs text-slate-500 font-medium">
                      Searching store policies & formulating answer...
                    </span>
                  </div>
                  {showSlowHint && (
                    <div className="flex items-center gap-1.5 text-[11px] text-indigo-600 font-medium pl-6 animate-pulse">
                      <span>Still working on it...</span>
                    </div>
                  )}
                </div>
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>

          {/* Error Banner */}
          {errorMessage && (
            <div className="mx-4 sm:mx-6 mb-2 p-3 rounded-xl bg-red-50 border border-red-200 text-red-800 text-xs flex items-center justify-between gap-3 shadow-sm animate-in fade-in">
              <div className="flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
                <span className="font-medium">{errorMessage}</span>
              </div>
              <button
                type="button"
                onClick={() => setErrorMessage(null)}
                className="text-red-500 hover:text-red-700 p-0.5 rounded-md hover:bg-red-100 transition-colors"
                title="Dismiss"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          )}

          {/* Input Box Footer */}
          <div className="p-4 sm:p-5 border-t border-slate-100 bg-white">
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!isLoading && !isSendingRef.current) {
                  handleSendMessage();
                }
              }}
              className="relative"
            >
              <div className="flex items-end gap-2 bg-slate-50 rounded-2xl border border-slate-200 focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-500/20 transition-all p-2">
                <textarea
                  ref={textareaRef}
                  value={inputMessage}
                  onChange={(e) => setInputMessage(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder="Ask a question about store policies... (Enter to send, Shift+Enter for new line)"
                  rows={2}
                  maxLength={1000}
                  disabled={isLoading}
                  className="flex-1 bg-transparent border-0 resize-none text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-0 px-2 py-1 disabled:opacity-50"
                />

                <div className="flex flex-col items-end gap-1.5 shrink-0">
                  {/* Character Counter */}
                  <span
                    className={`text-[10px] font-mono pr-1 ${
                      inputMessage.length >= 950
                        ? 'text-red-600 font-bold'
                        : inputMessage.length >= 800
                        ? 'text-amber-600 font-medium'
                        : 'text-slate-400'
                    }`}
                  >
                    {inputMessage.length}/1000
                  </span>

                  {/* Send Button */}
                  <button
                    type="submit"
                    disabled={!inputMessage.trim() || isLoading}
                    className="w-9 h-9 rounded-xl bg-indigo-600 hover:bg-indigo-700 disabled:bg-slate-200 text-white disabled:text-slate-400 flex items-center justify-center transition-all cursor-pointer disabled:cursor-not-allowed shadow-sm shadow-indigo-200 disabled:shadow-none"
                    title="Send message"
                  >
                    {isLoading ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <Send className="w-4 h-4" />
                    )}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      </main>

      {/* Create Ticket Modal */}
      <CreateTicketModal
        isOpen={isTicketModalOpen}
        onClose={() => setIsTicketModalOpen(false)}
        initialData={ticketModalData}
        onSuccess={handleTicketCreatedSuccess}
      />
    </div>
  );
};

export default ChatPage;
