package com.samcode.finance_rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ChatController {

    private final ChatClient chatClient;
    private final GeminiService geminiService;
    private final IngestionService ingestionService;
    private final VectorStore vectorStore;

    private static final String FINANCIAL_SYSTEM_PROMPT = """
        You are a sophisticated financial analyst AI assistant specializing in analyzing financial documents, presentations, and reports.
        
        CORE EXPERTISE:
        - Financial markets analysis (equity, fixed income, alternatives, currencies, commodities)
        - Investment strategies and portfolio management
        - Economic indicators and market trends
        - Risk assessment and valuation analysis
        - Corporate earnings and performance metrics
        
        RESPONSE GUIDELINES:
        1. ALWAYS provide accurate, data-driven analysis based on the retrieved context
        2. MANDATORY: Include specific source citations using [Source: Slide X] format for every key point
        3. Present numerical data with proper context and units (%, bps, $, etc.)
        4. Explain financial concepts clearly when asked
        5. Acknowledge limitations when data is insufficient
        
        CITATION REQUIREMENTS:
        - Use [Source: Slide X] for single source references
        - Use [Sources: Slide X, Y, Z] for multiple sources
        - Be specific about slide numbers from the document metadata
        - If no slide number is available, use [Source: Document section]
        
        FINANCIAL ANALYSIS FOCUS:
        - Interpret trends, correlations, and performance metrics
        - Explain market movements and economic implications
        - Provide context for investment decisions and risk factors
        - Highlight key insights and actionable information
        
        If the provided context doesn't contain sufficient information to answer the question accurately, 
        clearly state the limitation rather than speculating.
        """;

    public ChatController(ChatClient.Builder builder, PgVectorStore vectorStore, 
                         GeminiService geminiService, IngestionService ingestionService) {
        this.chatClient = builder
                .defaultSystem(FINANCIAL_SYSTEM_PROMPT)
                .build(); // Removed default advisor; we'll do manual RAG below
        this.vectorStore = vectorStore;
        this.geminiService = geminiService;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, String> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("status", "error");
                response.put("message", "Please select a file to upload");
                return ResponseEntity.badRequest().body(response);
            }

            ingestionService.ingestDocument(file);
            
            response.put("status", "success");
            response.put("message", "Document uploaded and processed successfully!");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error processing file: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/api/chat")
    @ResponseBody
    public ResponseEntity<Map<String, String>> chat(@RequestParam("question") String question,
                                                   @RequestParam(value = "model", defaultValue = "openai") String model) {
        Map<String, String> response = new HashMap<>();
        
        try {
            String answer;
            String modelUsed;
            
            if ("gemini".equals(model)) {
                answer = geminiService.chat(question);
                modelUsed = "Google Gemini 1.5 Flash";
            } else {
                // OpenAI path with custom RAG context
                List<Document> docs = vectorStore.similaritySearch(question);
                String context = buildEnhancedContext(docs);
                String userPrompt = buildPromptWithContext(question, context);
                answer = chatClient.prompt()
                        .system(FINANCIAL_SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .content();
                modelUsed = "OpenAI GPT-4.1";
            }
            
            response.put("status", "success");
            response.put("answer", answer);
            response.put("model", modelUsed);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("answer", "I apologize, but I encountered an error while processing your question. Please try again.");
            response.put("model", "Error");
            return ResponseEntity.badRequest().body(response);
        }
    }

    private String buildEnhancedContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (Document doc : documents) {
            String slideNumber = extractSlideNumber(doc.getMetadata());
            String chunkType = doc.getMetadata().getOrDefault("chunk_type", "content").toString();
            context.append("--- Document Section ---\n");
            context.append("Slide: ").append(slideNumber).append("\n");
            context.append("Type: ").append(chunkType).append("\n");
            context.append("Content: ").append(doc.getText()).append("\n\n");
        }
        return context.toString();
    }

    private String extractSlideNumber(Map<String, Object> metadata) {
        if (metadata.containsKey("slide_number") && !"Unknown".equals(metadata.get("slide_number"))) {
            return metadata.get("slide_number").toString();
        }
        if (metadata.containsKey("slide")) {
            return metadata.get("slide").toString();
        }
        if (metadata.containsKey("page")) {
            return metadata.get("page").toString();
        }
        return "Unknown";
    }

    private String buildPromptWithContext(String question, String context) {
        return String.format("""
            Context information from financial documents is below:
            ---------------------
            %s
            ---------------------
            Instructions:
            - Analyze the provided context thoroughly using your financial expertise.
            - Answer the user's question with specific data-driven insights.
            - MANDATORY: Include [Source: Slide X] citations for every key point you reference.
            - When referencing numerical data, include proper units and context.
            - If analyzing trends or performance, provide meaningful interpretation.
            - If the context lacks sufficient information, clearly state the limitation.
            
            Question: %s
            
            Financial Analysis:""", context, question);
    }
}
