package com.samcode.finance_rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {
    
    @Value("${google.ai.api-key}")
    private String apiKey;
    
    private final VectorStore vectorStore;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
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
    
    public GeminiService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    public String chat(String question) {
        try {
            // Get relevant context from vector store
            List<Document> similarDocuments = vectorStore.similaritySearch(question);
            
            // Build enhanced context with metadata
            String context = buildEnhancedContext(similarDocuments);
            
            // Create prompt with RAG context and system prompt
            String prompt = buildPromptWithContext(question, context);
            
            // Call Google AI Studio API
            return callGeminiAPI(prompt);
            
        } catch (Exception e) {
            return "I apologize, but I encountered an error while processing your question. Please try again.";
        }
    }
    
    private String buildEnhancedContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        
        for (Document doc : documents) {
            Map<String, Object> metadata = doc.getMetadata();
            String slideNumber = extractSlideNumber(metadata);
            String chunkType = metadata.getOrDefault("chunk_type", "content").toString();
            
            context.append("--- Document Section ---\n");
            context.append("Slide: ").append(slideNumber).append("\n");
            context.append("Type: ").append(chunkType).append("\n");
            context.append("Content: ").append(doc.getText()).append("\n\n");
        }
        
        return context.toString();
    }
    
    private String extractSlideNumber(Map<String, Object> metadata) {
        if (metadata.containsKey("slide_number")) {
            Object slideNum = metadata.get("slide_number");
            if (slideNum != null && !slideNum.toString().equals("Unknown")) {
                return slideNum.toString();
            }
        }
        
        // Fallback to other metadata keys
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
            %s
            
            Context information from financial documents is below:
            ---------------------
            %s
            ---------------------
            
            Instructions: 
            - Analyze the provided context thoroughly using your financial expertise
            - Answer the user's question with specific data-driven insights
            - MANDATORY: Include [Source: Slide X] citations for every key point you reference
            - When referencing numerical data, include proper units and context
            - If analyzing trends or performance, provide meaningful interpretation
            - If the context lacks sufficient information, clearly state the limitation
            
            Question: %s
            
            Financial Analysis:""", FINANCIAL_SYSTEM_PROMPT, context, question);
    }
    
    private String callGeminiAPI(String prompt) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
            
            // Build request body
            GeminiRequest request = new GeminiRequest();
            request.contents = List.of(new Content(List.of(new Part(prompt))));
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);
            
            // Make API call
            GeminiResponse response = restTemplate.exchange(url, HttpMethod.POST, entity, GeminiResponse.class).getBody();
            
            if (response != null && response.candidates != null && !response.candidates.isEmpty()) {
                return response.candidates.get(0).content.parts.get(0).text;
            }
            
            return "I apologize, but I couldn't generate a response. Please try again.";
            
        } catch (Exception e) {
            return "I encountered an error while calling the Gemini API. Please check your API key and try again.";
        }
    }
    
    // DTOs for Google AI Studio API
    static class GeminiRequest {
        public List<Content> contents;
    }
    
    static class Content {
        public List<Part> parts;
        
        public Content(List<Part> parts) {
            this.parts = parts;
        }
    }
    
    static class Part {
        public String text;
        
        public Part(String text) {
            this.text = text;
        }
    }
    
    static class GeminiResponse {
        public List<Candidate> candidates;
    }
    
    static class Candidate {
        public Content content;
    }
} 