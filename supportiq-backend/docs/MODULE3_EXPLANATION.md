# SupportIQ Module 3: Policy Document Upload, Chunking, Embeddings & Vector Storage

Welcome to **Module 3** of the SupportIQ platform! 

In **Module 1**, we built the backend authentication foundation with JWTs and roles (`CUSTOMER` and `ADMIN`). In **Module 2**, we created the React frontend with secure routing and role-based guards.

Now in **Module 3**, we build the **RAG (Retrieval-Augmented Generation) knowledge engine**. This guide explains every concept in simple, beginner-friendly terms.

---

## 📚 Table of Contents
1. [The Big Picture: Why RAG?](#1-the-big-picture-why-rag)
2. [What is Chunking, and Why Do We Need It?](#2-what-is-chunking-and-why-do-we-need-it)
3. [What is Overlap, and Why is It Critical?](#3-what-is-overlap-and-why-is-it-critical)
4. [What is an Embedding? (gemini-embedding-001)](#4-what-is-an-embedding-gemini-embedding-001)
5. [How Does Vector Similarity Search Work?](#5-how-does-vector-similarity-search-work)
6. [Why Must Vector Dimensions Match? (3072 vs 768 MRL & Normalization)](#6-why-must-vector-dimensions-match-3072-vs-768-mrl--normalization)
7. [Task Types: RETRIEVAL_DOCUMENT vs RETRIEVAL_QUERY](#7-task-types-retrieval_document-vs-retrieval_query)
8. [The Complete End-to-End Upload Flow](#8-the-complete-end-to-end-upload-flow)
9. [Failure Handling, Zero-Orphan Cleanup & Error Codes](#9-failure-handling-zero-orphan-cleanup--error-codes)

---

## 1. The Big Picture: Why RAG?

Large Language Models (like Gemini, GPT-4, or Claude) are incredibly smart, but they **do not know your specific e-commerce store's policies**—such as your 30-day return window, shipping exceptions, or restocking fees.

There are two bad ways to solve this:
1. **Fine-tuning the model**: Very slow, very expensive, and every time you update your policy, you'd have to retrain the model.
2. **Pasting your whole 50-page policy manual into every user prompt**: Burns huge amounts of tokens, slows down responses, and often exceeds token budgets.

### The Winning Solution: RAG (Retrieval-Augmented Generation)
1. You upload your policy PDFs once.
2. The system splits the policy into bite-sized **chunks** and converts them into mathematical representations called **embeddings**.
3. We store those embeddings inside PostgreSQL using the **pgvector** extension.
4. When a customer later asks: *"Can I return my shoes after 35 days?"*, we search our vector database for only the **top 3 or 4 chunks** about footwear and return windows, and feed only those relevant chunks to the AI!

---

## 2. What is Chunking, and Why Do We Need It?

### What is Chunking?
**Chunking** is the process of breaking down a large text document (such as a 10-page PDF) into smaller, coherent text segments (e.g., 500 to 1,000 characters each).

### Why Can't We Just Store the Entire Document in One Piece?
- **Granularity & Precision**: If you search for *"What is the international shipping rate?"*, you don't want the search engine to return a 20,000-word document. You want the exact 2 paragraphs discussing international shipping.
- **Embedding Quality**: Embedding models produce a single vector for an entire input. If you pass 20 pages of mixed topics into an embedding model, the vector becomes an average of everything, washing out the specific details. Smaller chunks have razor-sharp semantic meaning.
- **Prompt Size Optimization**: In Module 4, we inject retrieved chunks into the prompt context. Small, targeted chunks keep LLM token costs minimal and response times fast.

In SupportIQ:
- **Chunk Size**: Configured at `~800 characters` (`app.rag.chunk-size: 800`).
- **Boundary Awareness**: Instead of blindly chopping text at character 800, SupportIQ searches backward for a natural pause:
  1. Paragraph break (`\n\n`)
  2. Sentence break (`. `, `! `, `? `)
  3. Newline (`\n`)
  4. Word space (` `)

---

## 3. What is Overlap, and Why is It Critical?

### What is Chunk Overlap?
**Overlap** means that when Chunk 1 ends, Chunk 2 does not start from scratch; instead, it starts slightly **before** Chunk 1 ended, repeating the last ~100 characters.

```
Original Text: "All shoes must be returned unworn with tags attached. Returns after 30 days are subject to a fee."

Chunk 1: [All shoes must be returned unworn with tags attached. Returns after 30 days]
Chunk 2:                                         [Returns after 30 days are subject to a fee.]
                                                 <---- OVERLAP ---->
```

### Why is Overlap Critical?
Imagine a key sentence sits right across a chunk boundary:
- *"Refunds are strictly denied for..."* (End of Chunk 1)
- *"...items that have been worn outdoors."* (Start of Chunk 2)

Without overlap:
- Chunk 1 says: *"Refunds are strictly denied for"* (Incomplete, lacks context).
- Chunk 2 says: *"items that have been worn outdoors"* (Lacks the word 'denied').

Neither chunk will score well in a search for *"outdoor return rules"*.

**With overlap**, the full idea is preserved in at least one of the chunks, ensuring no meaning is lost at arbitrary cut-off boundaries.

In SupportIQ:
- **Overlap Size**: Configured at `~100 characters` (`app.rag.chunk-overlap: 100`).

---

## 4. What is an Embedding? (gemini-embedding-001)

### Words into Numbers
Computers cannot directly "understand" the meaning of English words like *"shoe"*, *"sneaker"*, or *"boot"*. They can only do math.

An **embedding model** takes a string of text and converts it into a long list of numbers called a **vector** (an array of floating-point numbers).

Google sunset `text-embedding-004` on 14 January 2026. SupportIQ utilizes the official production model **`gemini-embedding-001`**.

For example:
```json
"Can I return my sneakers?" -> [ 0.0241, -0.0512, 0.0893, ..., 0.0114 ]  (768 numbers long)
"What is the footwear return policy?" -> [ 0.0238, -0.0498, 0.0881, ..., 0.0120 ] (768 numbers long)
"How do I bake sourdough bread?" -> [ -0.1982, 0.4312, -0.0051, ..., -0.2104 ] (768 numbers long)
```

Notice:
- Even though the words *"sneakers"* and *"footwear"* are completely different strings of letters, their vectors are **extremely close to each other** in numbers!
- A completely unrelated topic like *"sourdough bread"* gets numbers pointing in an entirely different direction.

---

## 5. How Does Vector Similarity Search Work?

### Geometric Distance in 768-Dimensional Space
Imagine a 2D graph with an X and Y axis. You plot two arrows (vectors) starting from the center:
- If the arrows point in the exact same direction, the angle between them is $0^\circ$, and their **Cosine Similarity** is `1.0` (identical meaning).
- If the arrows are perpendicular ($90^\circ$), their similarity is `0.0` (unrelated).
- If they point in opposite directions ($180^\circ$), their similarity is `-1.0` (opposite meaning).

In SupportIQ, vectors live in **768 dimensions**. The mathematical formula used is **Cosine Distance**:

$$\text{Cosine Similarity}(A, B) = \frac{A \cdot B}{\|A\| \|B\|}$$

### Why pgvector with HNSW?
Checking a query vector against millions of document vectors one-by-one is slow.

SupportIQ uses PostgreSQL with the **pgvector** extension and creates an **HNSW (Hierarchical Navigable Small World)** index:
```sql
CREATE INDEX vector_store_hnsw_cosine_idx 
ON vector_store 
USING HNSW (embedding vector_cosine_ops);
```
HNSW organizes vectors into interconnected layers like a multi-level road network (highways -> local streets), enabling lightning-fast similarity lookups in single-digit milliseconds!

---

## 6. Why Must Vector Dimensions Match? (3072 vs 768 MRL & Normalization)

### Default Dimensions vs Matryoshka Representation Learning (MRL)
* By default, `gemini-embedding-001` generates **3072 dimensions**.
* Storing 3072 dimensions in PostgreSQL takes 4x more disk space and RAM, and increases index build times.
* Google engineered `gemini-embedding-001` with **Matryoshka Representation Learning (MRL)**, allowing applications to request truncated output dimensions (such as **768**) without noticeable loss in semantic accuracy.
* SupportIQ sends `outputDimensionality: 768` in the request payload to match the database column `VECTOR(768)`.

### Why Dimensions Must Match Exactly
In PostgreSQL, the `vector_store` table defines:
```sql
embedding VECTOR(768)
```
If you generate a 3072-dimensional vector and try to insert it into a `VECTOR(768)` column, PostgreSQL throws an immediate fatal SQL error:
```
ERROR: vector dimensions do not match (3072 != 768)
```
Vector math operations (dot products, vector distances) require every single component index to line up.

### L2 Normalization Requirement
The official Google Gemini API documentation states that while native 3072-dimensional vectors are pre-normalized, when you request a truncated dimension like `768` on `gemini-embedding-001`, **client-side L2 normalization** is required before calculating cosine similarity.

SupportIQ performs Euclidean L2 normalization on every returned vector:
$$\|v\|_2 = \sqrt{\sum_{i=1}^{768} v_i^2}, \quad v_i \leftarrow \frac{v_i}{\|v\|_2}$$
Once normalized to unit length ($\|v\|_2 = 1.0$), cosine similarity equals the dot product, making similarity calculations fast and mathematically precise.

---

## 7. Task Types: RETRIEVAL_DOCUMENT vs RETRIEVAL_QUERY

The Gemini embedding model optimizes the vector space based on how the text will be used:

| Task Type | Usage in SupportIQ | Description |
| :--- | :--- | :--- |
| **`RETRIEVAL_DOCUMENT`** | Document Chunks (Ingestion) | Optimizes chunk vectors for being retrieved in a knowledge base corpus. |
| **`RETRIEVAL_QUERY`** | Search / Question (Retrieval) | Optimizes query vectors for matching against document chunk vectors. |

SupportIQ passes `taskType` on every embedding call:
- When ingesting a PDF: chunks are embedded as `RETRIEVAL_DOCUMENT`.
- When an admin runs the test search: the query is embedded as `RETRIEVAL_QUERY`.

---

## 8. The Complete End-to-End Upload Flow

Here is the exact journey of a policy PDF when uploaded by an Administrator:

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Administrator (React UI)
    participant Ctrl as DocumentAdminController
    participant Ext as PdfTextExtractor (PDFBox 3.x)
    participant Svc as DocumentIngestionService
    participant Chunk as ChunkingService
    participant Gem as GeminiEmbeddingModel
    participant DB as PostgreSQL + pgvector

    Admin->>Ctrl: POST /api/admin/documents (file: "return-policy.pdf")
    Note over Ctrl: Check ADMIN role, validate magic bytes (%PDF) & size <= 10MB
    Ctrl->>Svc: ingestDocument(file, adminUserId)
    Svc->>Ext: extractPages(bytes)
    Note over Ext: PDFBox 3.x Loader.loadPDF extracts page by page
    Ext-->>Svc: List<PageText>
    Svc->>DB: INSERT into policy_documents (status: 'PROCESSING')
    Svc->>Chunk: chunkPages(pages)
    Note over Chunk: Split into ~800 char chunks with ~100 char overlap at boundaries
    Chunk-->>Svc: List<DocumentChunk>
    Svc->>Gem: batchEmbedContents (gemini-embedding-001, RETRIEVAL_DOCUMENT, outputDimensionality: 768)
    Note over Gem: Sends x-goog-api-key header; performs L2 normalization
    Gem-->>Svc: List<float[768]> (normalized)
    Svc->>DB: INSERT into vector_store (content, metadata, embedding)
    Svc->>DB: UPDATE policy_documents SET status = 'READY', chunk_count = N
    Svc-->>Ctrl: PolicyDocumentResponse (201 Created)
    Ctrl-->>Admin: 201 Created JSON
```

---

## 9. Failure Handling, Zero-Orphan Cleanup & Error Codes

### Model & API Failure Mapping
If the Google Gemini API returns an error:
* **404 (Model not found)** or **403 (Forbidden)**: SupportIQ maps this to a clear, actionable error:
  ```json
  {
    "status": 502,
    "error": "Bad Gateway",
    "message": "Embedding model not available. Check GEMINI_EMBEDDING_MODEL and the API key",
    "path": "/api/admin/documents"
  }
  ```
  This prevents opaque, unhelpful 500 Internal Server Errors.
* **401 (Unauthorized)**: Returns `"Google Gemini API authentication failed. Check GEMINI_API_KEY."`
* **Dimension Mismatch**: Throws `"Returned vector length does not match configured vector dimension (768)"`.

### Zero-Orphan Cleanup
If ingestion fails at any stage:
1. **Status Set to FAILED**: The row in `policy_documents` is updated with `status = 'FAILED'` and the exact error explanation is recorded in `error_message`.
2. **Purge Vectors**:
   ```sql
   DELETE FROM vector_store WHERE (metadata->>'documentId') = :docId;
   ```
   All partially written chunks from that document are immediately purged. No orphaned, unreferenced chunks are left in storage.
3. **Auditability**: Administrators can inspect the failure in the UI table, read the cause, and re-upload once resolved.
