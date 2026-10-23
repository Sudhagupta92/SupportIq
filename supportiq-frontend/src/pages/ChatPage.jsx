import React, { useState, useRef, useEffect } from 'react';
import Navbar from '../components/Navbar';
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
} from 'lucide-react';

/**
 * Example starter questions for the customer welcome view.
 */
const EXAMPLE_QUESTIONS = [
  'What is your return policy for damaged items?',
  'How long does standard shipping take?',
  'Can I cancel or modify an order after placing it?',
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
  const [errorMessage, setErrorMessage] = useState(null);

  const messagesEndRef = useRef(null);
  const textareaRef = useRef(null);

  // Auto-scroll to the newest message whenever messages or loading state changes
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  // Focus textarea on initial load
  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

  const handleSendMessage = async (textToSend) => {
    const query = (textToSend || inputMessage).trim();
    if (!query || isLoading) return;

    if (query.length > 1000) {
      setErrorMessage('Your message exceeds the 1000-character limit. Please shorten your question.');
      return;
    }

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

    try {
      const response = await sendChatMessageApi(query);

      const assistantMessage = {
        id: generateId(),
        role: 'assistant',
        text: response.answer,
        answered: response.answered,
        sources: response.sources || [],
        timestamp: formatTime(),
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err) {
      console.error('Chat request failed:', err);
      // Extract backend error message if present (429, 502, 504, 400)
      const backendMessage =
        err.response?.data?.message ||
        (err.code === 'ECONNABORTED'
          ? 'The assistant took too long to respond. Please try again.'
          : 'Unable to reach the SupportIQ assistant. Please check your network connection.');

      setErrorMessage(backendMessage);
    } finally {
      setIsLoading(false);
      // Re-focus the input textarea
      setTimeout(() => textareaRef.current?.focus(), 100);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handleClearChat = () => {
    setMessages([]);
    setErrorMessage(null);
    setInputMessage('');
    textareaRef.current?.focus();
  };

  const handleSelectExample = (question) => {
    handleSendMessage(question);
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
                      onClick={() => handleSelectExample(question)}
                      className="w-full text-left p-3.5 rounded-xl border border-slate-200 hover:border-indigo-300 bg-white hover:bg-indigo-50/40 text-slate-700 hover:text-indigo-900 text-sm font-medium transition-all shadow-sm flex items-center justify-between group cursor-pointer"
                    >
                      <span className="flex items-center gap-2.5">
                        <HelpCircle className="w-4 h-4 text-slate-400 group-hover:text-indigo-500 shrink-0" />
                        <span>{question}</span>
                      </span>
                      <Send className="w-3.5 h-3.5 text-slate-300 group-hover:text-indigo-600 opacity-0 group-hover:opacity-100 transition-opacity shrink-0" />
                    </button>
                  ))}
                </div>
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

                      {/* Source Citations */}
                      {msg.sources && msg.sources.length > 0 && (
                        <div className="mt-3 pt-2.5 border-t border-slate-100">
                          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 block mb-1.5">
                            Cited Policy Sources:
                          </span>
                          <div className="flex flex-wrap gap-1.5">
                            {msg.sources.map((source, idx) => (
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
                            ))}
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
                <div className="bg-white border border-slate-200 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm flex items-center gap-2">
                  <Loader2 className="w-4 h-4 text-indigo-600 animate-spin" />
                  <span className="text-xs text-slate-500 font-medium">
                    Searching store policies & formulating answer...
                  </span>
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
                handleSendMessage();
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
    </div>
  );
};

export default ChatPage;
