# SupportIQ Backend - Module 1, 2 & 3: RAG Knowledge Base & Authentication

SupportIQ is an AI-powered customer support agent platform for e-commerce stores. 
- **Module 1**: Core backend architecture, PostgreSQL persistence, Spring Security 6, and stateless JWT authentication.
- **Module 2**: React + Vite frontend with role-based routing and customer/admin dashboards.
- **Module 3**: Policy document ingestion, Apache PDFBox 3.x text extraction, boundary-aware chunking, Google Gemini embeddings (`gemini-embedding-001`), and PostgreSQL pgvector vector storage.

---

## 🛠️ Tech Stack & Requirements

- **Java**: 17+ (LTS)
- **Framework**: Spring Boot 3.3.5 (Spring Web, Spring Security, Spring Data JPA, Bean Validation)
- **Spring AI**: 1.0.0 (`spring-ai-bom`, `spring-ai-starter-vector-store-pgvector`)
- **Text Extraction**: Apache PDFBox 3.0.4 (`org.apache.pdfbox:pdfbox`)
- **Database**: PostgreSQL 16 with `pgvector/pgvector:pg16`
- **Embedding Provider**: Google Gemini (`gemini-embedding-001` with `outputDimensionality: 768`) behind Spring AI's `EmbeddingModel` interface
- **Security & Tokens**: Spring Security 6 + JJWT 0.12.x (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- **Build Tool**: Apache Maven

---

## 🧠 Embedding Model & Dimension Architecture

### Model Migration: `gemini-embedding-001`
Google sunset `text-embedding-004` on 14 January 2026. SupportIQ uses the current official model **`gemini-embedding-001`**.

### Dimensionality Reduction (3072 $\rightarrow$ 768) & L2 Normalization
1. **Native vs Truncated Dimensions**: `gemini-embedding-001` outputs 3072 dimensions by default. SupportIQ utilizes Matryoshka Representation Learning (MRL) by transmitting `outputDimensionality: 768`.
2. **Why 768?**: Truncating to 768 dimensions preserves vector database compatibility with our pgvector schema (`VECTOR(768)`), saves 75% in memory and index storage costs, and reduces similarity search latency while maintaining high semantic accuracy.
3. **L2 Normalization**: The official Gemini API documentation specifies that when output dimensionality is reduced below 3072 on `gemini-embedding-001`, the vectors must be L2-normalized client-side to unit length ($\|v\|_2 = 1.0$). SupportIQ automatically calculates the Euclidean norm and normalizes all vectors before persisting to pgvector.
4. **Runtime Verification**: `GeminiEmbeddingModel` verifies the returned vector length matches the configured dimension (768) and throws an immediate error if a mismatch is detected.

### Task Types
Downstream vector placement is optimized by sending explicit task types in the Gemini request:
* **`RETRIEVAL_DOCUMENT`**: Applied when chunking and indexing policy document chunks into pgvector.
* **`RETRIEVAL_QUERY`**: Applied when embedding search and retrieval queries (e.g. from the admin search testbed).

---

## ⚙️ Environment Variables Configuration

SupportIQ uses strict environment-variable-first configuration. **Never commit API keys into source control.**

| Environment Variable | Description | Default Value | Notes |
| :--- | :--- | :--- | :--- |
| `GEMINI_API_KEY` | Google Gemini API Key | *(None - Required for live embeddings)* | Obtain from Google AI Studio. Sent via `x-goog-api-key` header only. |
| `GEMINI_EMBEDDING_MODEL`| Gemini embedding model name | `gemini-embedding-001` | Active model; overrides `text-embedding-004` |
| `VECTOR_DIMENSION` | Dimension of embedding vectors | `768` | **MUST match pgvector column and outputDimensionality** |
| `CHUNK_SIZE` | Target chunk character count | `800` | Natural boundary splitting |
| `CHUNK_OVERLAP` | Target chunk overlap chars | `100` | Context boundary continuity |
| `MAX_FILE_SIZE` | Maximum upload file size | `10485760` (10 MB) | Enforced on client and server |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/supportiq` | |
| `DB_USERNAME` | Database username | `postgres` | |
| `DB_PASSWORD` | Database password | `postgres` | |
| `JWT_SECRET` | Secret key for signing HMAC-SHA JWTs | 512-bit hex key | >= 256 bits |
| `JWT_EXPIRATION_MS` | JWT validity duration in ms | `86400000` (24 hours) | |
| `ADMIN_EMAIL` | Default seeded admin email | `admin@supportiq.com` | |
| `ADMIN_PASSWORD` | Default seeded admin password | `Admin@12345` | |

---

## 🚀 Setting Environment Variables in Windows PowerShell

Open a PowerShell terminal in `supportiq-backend` and execute:

```powershell
# 1. Set Google Gemini API Key (Required for live vector ingestion)
$env:GEMINI_API_KEY="AIzaSyYourGeminiApiKeyHere"

# 2. Embedding Model (defaults to gemini-embedding-001)
$env:GEMINI_EMBEDDING_MODEL="gemini-embedding-001"

# 3. Vector Dimension (must match pgvector schema)
$env:VECTOR_DIMENSION="768"

# 4. Chunking parameters (defaults: 800 and 100)
$env:CHUNK_SIZE="800"
$env:CHUNK_OVERLAP="100"

# Verify the environment variable is set
Get-ChildItem env:GEMINI_API_KEY
```

---

## 🐳 Step-by-Step Setup & Running

### Step 1: Start PostgreSQL with pgvector Container
Ensure Docker Desktop is running:

```powershell
cd supportiq-backend
docker compose up -d
```

The container starts with:
- Image: `pgvector/pgvector:pg16`
- Port: `5432:5432`
- Database: `supportiq`
- Schema: Spring AI with `initialize-schema: true` automatically creates the `vector_store` table with `VECTOR(768)`. Hibernate creates `policy_documents` and `users`. An explicit SQL migration script is also available in `src/main/resources/schema-pgvector.sql`.

### Step 2: Build & Run the Backend
```powershell
# Clean, test, and package
mvn clean install

# Run the Spring Boot application
mvn spring-boot:run
```

The backend starts on **`http://localhost:8080`**.

---

## 📡 API Reference & Testing (cURL & PowerShell)

### Step 0: Obtain an Admin JWT Token

Both cURL and PowerShell examples below require an Admin token:

#### PowerShell:
```powershell
$loginBody = @{
    email = "admin@supportiq.com"
    password = "Admin@12345"
} | ConvertTo-Json

$loginResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$adminToken = $loginResponse.token
Write-Host "Admin Token: $adminToken"
```

#### cURL:
```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@supportiq.com","password":"Admin@12345"}'
```

---

### 1. Upload Policy PDF (`POST /api/admin/documents`)
- **Access**: `ADMIN` role only
- **Content-Type**: `multipart/form-data`
- **Field**: `file`
- **Validations**: Real PDF (`%PDF` magic header), maximum 10 MB, non-empty, contains extractable text.

#### PowerShell:
```powershell
$filePath = "..\docs\sample-return-policy.pdf" # path to your PDF
$uri = "http://localhost:8080/api/admin/documents"

$form = @{
    file = Get-Item -Path $filePath
}

$response = Invoke-RestMethod -Uri $uri -Method Post -Headers @{ Authorization = "Bearer $adminToken" } -Form $form
$response | ConvertTo-Json
```

#### cURL:
```bash
curl -X POST http://localhost:8080/api/admin/documents \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "file=@/path/to/return-policy.pdf"
```

#### Success Response (`201 Created`):
```json
{
  "id": 1,
  "filename": "return-policy.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 154200,
  "pageCount": 3,
  "chunkCount": 12,
  "status": "READY",
  "errorMessage": null,
  "uploadedBy": 1,
  "createdAt": "2026-10-22T08:30:00"
}
```

---

### 2. List Policy Documents (`GET /api/admin/documents`)
- **Access**: `ADMIN` role only
- Returns all documents ordered newest first.

#### PowerShell:
```powershell
$docs = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/documents" -Method Get -Headers @{ Authorization = "Bearer $adminToken" }
$docs | Format-Table id, filename, pageCount, chunkCount, status, sizeBytes
```

#### cURL:
```bash
curl -X GET http://localhost:8080/api/admin/documents \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

### 3. Test Vector Retrieval Search (`POST /api/admin/documents/search`)
- **Access**: `ADMIN` role only
- Performs similarity search across indexed chunks using `RETRIEVAL_QUERY` task type.

#### PowerShell:
```powershell
$searchBody = @{
    query = "What is the return policy for footwear and sneakers?"
    topK = 4
} | ConvertTo-Json

$results = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/documents/search" -Method Post -ContentType "application/json" -Headers @{ Authorization = "Bearer $adminToken" } -Body $searchBody
$results | Format-Table filename, pageNumber, chunkIndex, similarityScore
```

#### cURL:
```bash
curl -X POST http://localhost:8080/api/admin/documents/search \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What is the return policy for footwear and sneakers?",
    "topK": 4
  }'
```

#### Success Response (`200 OK`):
```json
[
  {
    "documentId": 1,
    "filename": "return-policy.pdf",
    "pageNumber": 1,
    "chunkIndex": 2,
    "similarityScore": 0.884,
    "text": "Footwear must be returned inside the original protective shoebox with no scuffs or marks on the outer soles. Standard return window is thirty days."
  }
]
```

---

### 4. Delete Document & Purge Vectors (`DELETE /api/admin/documents/{id}`)
- **Access**: `ADMIN` role only
- Deletes the metadata row and purges all associated vector chunks from `vector_store`.

#### PowerShell:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/documents/1" -Method Delete -Headers @{ Authorization = "Bearer $adminToken" }
```

#### cURL:
```bash
curl -X DELETE http://localhost:8080/api/admin/documents/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Response: `204 No Content` (or `404 Not Found` if id does not exist).

---

## 🛡️ Error Handling & Status Codes

All errors return the global standard JSON error response:
```json
{
  "timestamp": "2026-10-22T08:35:10",
  "status": 502,
  "error": "Bad Gateway",
  "message": "Embedding model not available. Check GEMINI_EMBEDDING_MODEL and the API key",
  "path": "/api/admin/documents"
}
```

| HTTP Status | Condition | Example Trigger / Message |
| :--- | :--- | :--- |
| `400 Bad Request` | Invalid file or params | Uploading a `.txt` file disguised as `.pdf` (invalid magic bytes) |
| `401 Unauthorized` | Missing / invalid JWT or API key | Calling endpoint without token, or missing/invalid `GEMINI_API_KEY` |
| `403 Forbidden` | Insufficient role | Accessing `/api/admin/**` with a `CUSTOMER` role JWT |
| `404 Not Found` | Document not found | `DELETE /api/admin/documents/999999` |
| `413 Payload Too Large` | Exceeds 10 MB | Uploading a PDF larger than 10,485,760 bytes |
| `422 Unprocessable` | No extractable text | Uploading a scanned image-only PDF |
| `502 Bad Gateway` | Model unavailable / 404 / 403 | `"Embedding model not available. Check GEMINI_EMBEDDING_MODEL and the API key"` |

---

## 🧪 Automated Testing

Automated tests run with an isolated in-memory H2 database (PostgreSQL mode) and a mocked Spring AI `EmbeddingModel` so **no real API key or running database is required to run the test suite**.

```powershell
mvn test
```

### Test Coverage Highlights:
- **`GeminiEmbeddingModelTest`**: Tests default model `gemini-embedding-001`, `outputDimensionality: 768`, dimension mismatch checking, L2 normalization to unit length, task types (`RETRIEVAL_DOCUMENT` and `RETRIEVAL_QUERY`), 404/403 mapping to clear messages, and header authentication.
- **`GeminiEmbeddingModelIntegrationTest`**: Optional live integration test against real Google Gemini API. Automatically skipped when `GEMINI_API_KEY` is not present.
- **`ChunkingServiceTest`**: Tests 800-char chunking with 100-char overlap, boundary splitting at paragraph/sentence markers, empty text skipping, and page number tracking.
- **`PdfTextExtractorTest`**: Tests Apache PDFBox 3.x text extraction, `%PDF` magic byte enforcement, non-PDF rejection, scanned image (blank text) detection, and file size limits.
- **`DocumentAdminControllerTest`**: Tests RBAC (403 for CUSTOMER), invalid format (400), scanned PDF (422), oversized file (413), upload (201), list (200), search (200), and delete (204 / 404).
