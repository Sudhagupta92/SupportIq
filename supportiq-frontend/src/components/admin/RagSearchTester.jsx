import React, { useState } from 'react';
import { Search, Sparkles, FileText, HelpCircle } from 'lucide-react';
import Spinner from '../Spinner';
import Alert from '../Alert';
import { searchDocumentsApi } from '../../api/documentApi';

/**
 * Formats similarity score into percentage and human badge.
 */
const formatSimilarityScore = (score) => {
  if (score === null || score === undefined) return { label: '0.00', percent: '0%', badgeColor: 'bg-slate-100 text-slate-700' };
  const num = typeof score === 'number' ? score : parseFloat(score);
  const percent = Math.min(100, Math.max(0, Math.round(num * 100)));

  let badgeColor = 'bg-slate-100 text-slate-700';
  if (num >= 0.75) {
    badgeColor = 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200';
  } else if (num >= 0.5) {
    badgeColor = 'bg-indigo-50 text-indigo-700 ring-1 ring-indigo-200';
  } else {
    badgeColor = 'bg-amber-50 text-amber-700 ring-1 ring-amber-200';
  }

  return {
    label: num.toFixed(3),
    percent: `${percent}%`,
    badgeColor,
  };
};

/**
 * RagSearchTester Component
 * 
 * Allows administrators to test semantic vector retrieval against stored policy chunks.
 * Simulates Module 4's document retrieval stage before LLM response generation.
 */
const RagSearchTester = () => {
  const [query, setQuery] = useState('');
  const [topK, setTopK] = useState(4);
  const [isSearching, setIsSearching] = useState(false);
  const [results, setResults] = useState(null);
  const [searchError, setSearchError] = useState('');

  const handleSearch = async (e) => {
    e?.preventDefault();
    if (!query.trim()) {
      setSearchError('Please enter a search query or question.');
      return;
    }

    setIsSearching(true);
    setSearchError('');

    try {
      const data = await searchDocumentsApi(query.trim(), topK);
      setResults(data);
    } catch (err) {
      setSearchError(
        err.response?.data?.message || err.message || 'Retrieval search failed.'
      );
      setResults(null);
    } finally {
      setIsSearching(false);
    }
  };

  const handleClear = () => {
    setQuery('');
    setResults(null);
    setSearchError('');
  };

  return (
    <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm">
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center font-bold">
            <Search className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-base font-bold text-slate-900">Test Vector Retrieval Search</h3>
          </div>
        </div>

        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
          <Sparkles className="w-3 h-3 text-emerald-600" />
          RAG Testbed
        </span>
      </div>

      <p className="text-xs text-slate-500 mb-5 leading-relaxed">
        Verify how customer questions retrieve semantic chunks from your indexed policy documents. This validates embedding accuracy and pgvector similarity scores before Module 4 builds the conversational AI layer.
      </p>

      {/* Error Banner */}
      {searchError && (
        <div className="mb-4">
          <Alert
            type="error"
            title="Retrieval Error"
            message={searchError}
            onClose={() => setSearchError('')}
          />
        </div>
      )}

      {/* Search Input Form */}
      <form onSubmit={handleSearch} className="space-y-4">
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
              <Search className="w-4 h-4" />
            </div>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="e.g., Can I return shoes after 45 days? What is the international shipping fee?"
              className="w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-600 transition-colors"
            />
          </div>

          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1.5 px-3 py-2 bg-slate-50 border border-slate-300 rounded-xl text-xs text-slate-600">
              <span className="font-semibold">Top K:</span>
              <select
                value={topK}
                onChange={(e) => setTopK(parseInt(e.target.value, 10))}
                className="bg-transparent font-bold text-slate-900 focus:outline-none cursor-pointer"
              >
                {[1, 2, 3, 4, 5, 6, 8, 10].map((k) => (
                  <option key={k} value={k}>
                    {k}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="submit"
              disabled={isSearching || !query.trim()}
              className="inline-flex items-center gap-2 px-4 py-2.5 text-xs font-semibold rounded-xl text-white bg-emerald-600 hover:bg-emerald-700 shadow-sm transition-colors disabled:opacity-50 cursor-pointer"
            >
              {isSearching ? (
                <>
                  <Spinner size="sm" className="text-white" />
                  <span>Searching...</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-4 h-4" />
                  <span>Search Vectors</span>
                </>
              )}
            </button>

            {results !== null && (
              <button
                type="button"
                onClick={handleClear}
                className="px-3 py-2.5 text-xs font-semibold rounded-xl text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                Clear
              </button>
            )}
          </div>
        </div>
      </form>

      {/* Results Container */}
      {results !== null && (
        <div className="mt-6 pt-6 border-t border-slate-200">
          <div className="flex items-center justify-between mb-4">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Top Retrieved Chunks ({results.length})
            </h4>
            <span className="text-xs text-slate-400">
              Cosine similarity distance ranking
            </span>
          </div>

          {results.length === 0 ? (
            <div className="p-8 text-center bg-slate-50 rounded-xl border border-dashed border-slate-200">
              <div className="w-10 h-10 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mx-auto mb-2">
                <HelpCircle className="w-5 h-5" />
              </div>
              <p className="text-sm font-semibold text-slate-700">No matching chunks found</p>
              <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                No policy chunks closely matched your search query. Try rephrasing your question or uploading additional policy documents.
              </p>
            </div>
          ) : (
            <div className="space-y-4">
              {results.map((item, index) => {
                const scoreInfo = formatSimilarityScore(item.similarityScore);

                return (
                  <div
                    key={`${item.documentId || 'doc'}-${item.chunkIndex}-${index}`}
                    className="p-4 rounded-xl bg-slate-50/70 border border-slate-200 hover:border-slate-300 transition-colors"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2 mb-2.5">
                      <div className="flex items-center gap-2">
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[11px] font-bold bg-slate-200 text-slate-800">
                          #{index + 1}
                        </span>

                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[11px] font-semibold bg-red-50 text-red-700 ring-1 ring-red-200">
                          <FileText className="w-3 h-3 text-red-500" />
                          {item.filename || 'Unknown Document'}
                        </span>

                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-medium bg-slate-100 text-slate-600">
                          Page {item.pageNumber ?? 1}
                        </span>

                        <span className="text-[11px] text-slate-400">
                          Chunk {item.chunkIndex ?? 0}
                        </span>
                      </div>

                      <span
                        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold ${scoreInfo.badgeColor}`}
                        title={`Cosine Similarity Score: ${item.similarityScore}`}
                      >
                        Similarity: {scoreInfo.label} ({scoreInfo.percent})
                      </span>
                    </div>

                    <div className="p-3 bg-white rounded-lg border border-slate-200 text-xs text-slate-700 leading-relaxed font-mono whitespace-pre-wrap select-text">
                      {item.text}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default RagSearchTester;
