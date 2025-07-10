# Finance RAG 📊💬

A Spring Boot 3.5.3 (Java 21) **Retrieval-Augmented Generation** system for analyzing financial PDF documents. Supports dual LLM backends (OpenAI GPT-4o / Google Gemini 1.5 Flash), vector search with **PostgreSQL + pgvector**, and complete Docker deployment.

---

## 🚀 Quick Start (Docker Only)

### Prerequisites
- **Docker** and **Docker Compose** 
- **OpenAI API Key** (from [platform.openai.com](https://platform.openai.com/api-keys))
- **Google AI Studio API Key** (from [aistudio.google.com](https://aistudio.google.com/app/apikey))

### Setup
1. **Clone the repository**
   ```bash
   git clone <your-repository-url>
   cd finance_RAG
   ```

2. **Configure API keys**
   ```bash
   # Copy the example environment file
   cp .env.example .env
   
   # Edit .env with your actual API keys
   nano .env  # or use your preferred editor
   ```

3. **Start the application**
   ```bash
   docker compose up --build
   ```

4. **Access the application**
   - 🌐 **Web Interface**: http://localhost:8080
   - Upload PDFs and start asking questions immediately!

---

## ✨ Features

- 🌐 **Modern Web Interface**: Clean, responsive frontend with Bootstrap styling
- 📄 **Smart Document Upload**: Drag & drop PDF uploads with 50MB limit
- 🤖 **Dual AI Models**: Toggle between OpenAI GPT-4o and Google Gemini 1.5 Flash
- 💬 **Real-time Chat**: Interactive chat interface with message history  
- 🔍 **Advanced RAG**: Multi-level hierarchical chunking with slide number attribution
- 💾 **Vector Storage**: PostgreSQL with pgvector for scalable semantic search
- 📊 **Source Citations**: Responses include [Source: Slide X] references
- ⚡ **Real-time Processing**: Live status updates and loading indicators

---

## 🛠 Technology Stack

- **Spring Boot 3.5.3** - Application framework
- **Spring AI 1.0.0** - AI integration framework  
- **OpenAI GPT-4o & Google Gemini 1.5 Flash** - Large language models
- **PostgreSQL + pgvector** - Vector database
- **Java 21** - Programming language
- **Docker Compose** - Container orchestration
- **Thymeleaf + Bootstrap** - Frontend

---

## 📖 How to Use

1. **Upload Documents**: Drag & drop PDF files (financial reports, presentations, etc.)
2. **Select AI Model**: Choose between OpenAI (blue badge) or Google Gemini (green badge)
3. **Ask Questions**: Type natural language questions about your documents
4. **Get AI Answers**: Receive detailed responses with slide number citations

### Example Questions
- "What are the key financial metrics mentioned?"
- "Summarize the quarterly performance trends"
- "What investment recommendations are made?"
- "Extract all revenue figures by quarter"

---

## 🔧 API Endpoints

### Upload Documents
```http
POST /api/upload
Content-Type: multipart/form-data
```

### Chat with Documents  
```http
POST /api/chat?model=openai
Content-Type: application/json

{
  "question": "What are the key takeaways from slide 15?"
}
```

---

## 🏗 Project Structure

```
src/
├── main/
│   ├── java/com/samcode/finance_rag/
│   │   ├── Application.java          # Main Spring Boot application
│   │   ├── ChatController.java       # REST API endpoints & web routes
│   │   ├── IngestionService.java     # Document processing & chunking
│   │   └── GeminiService.java        # Google Gemini integration
│   └── resources/
│       ├── application.properties    # Application configuration
│       ├── templates/index.html      # Frontend Thymeleaf template
│       └── docs/                     # Sample documents
├── compose.yaml                      # Docker Compose configuration
└── Dockerfile                        # Application container
```

---

## 🔐 Security

- ✅ API keys stored in `.env` file (not committed to git)
- ✅ Environment variables used in Docker Compose
- ✅ `.env` automatically ignored by git


## 🔄 Development Workflow

### Making Changes
```bash
# Rebuild after code changes
docker compose up --build

# View application logs
docker compose logs -f finance-rag-app

# Stop everything
docker compose down
```

### Database Access
```bash
# Connect to PostgreSQL
docker compose exec pgvector psql -U user -d markets

# View stored vectors
SELECT id, metadata->>'slide_number', metadata->>'chunk_type' FROM vector_store LIMIT 10;
```

---

## 🎯 RAG Implementation Details

- **Multi-level Chunking**: Paragraph + sentence-level for granular retrieval
- **Smart Metadata**: Slide numbers, chunk types, hierarchy relationships
- **Source Attribution**: Automatic [Source: Slide X] references in responses
- **Optimized Retrieval**: Semantic similarity search with metadata filtering

---

## 📄 License

This project is licensed under the MIT License. 
