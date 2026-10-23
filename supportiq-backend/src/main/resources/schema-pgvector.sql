-- =============================================================================
-- SupportIQ Module 3: Vector Store & Policy Document Schema Migration
-- =============================================================================
-- This script enables pgvector and creates the tables necessary for Module 3.
-- Note: Spring AI with 'initialize-schema: true' automatically manages the
-- vector_store table creation. This migration script is provided for explicit
-- manual migrations, DBA review, and reproducible staging environments.
-- =============================================================================

-- 1. Enable pgvector extension and UUID generators in PostgreSQL
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. Create the policy_documents metadata table
CREATE TABLE IF NOT EXISTS policy_documents (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    page_count INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL, -- PROCESSING, READY, FAILED
    error_message TEXT,
    uploaded_by BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_policy_documents_status ON policy_documents(status);
CREATE INDEX IF NOT EXISTS idx_policy_documents_created_at ON policy_documents(created_at DESC);

-- 3. Create the vector_store table for Spring AI PgVector integration
-- CRITICAL: Vector dimension MUST match the embedding model output dimension.
-- Google Gemini gemini-embedding-001 with outputDimensionality=768 produces 768-dimensional embeddings.
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding VECTOR(768)
);

-- 4. Create an HNSW index for fast approximate nearest neighbor (ANN) cosine similarity search
CREATE INDEX IF NOT EXISTS vector_store_hnsw_cosine_idx
ON vector_store
USING HNSW (embedding vector_cosine_ops);

-- Index on documentId in metadata for efficient batch deletion and cleanup
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_doc_id
ON vector_store (((metadata->>'documentId')::bigint));
