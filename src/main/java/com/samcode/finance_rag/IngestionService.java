package com.samcode.finance_rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Component
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private final VectorStore vectorStore;
    
    // Enhanced patterns for content extraction
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z])");
    private static final Pattern SLIDE_NUMBER_PATTERN = Pattern.compile("Slide\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?m)^.+\\s+[\\d%$,.-]+\\s+[\\d%$,.-]+.*$");
    private static final Pattern LIST_PATTERN = Pattern.compile("(?m)^\\s*[•\\-\\*]\\s+.+$|^\\s*\\d+\\.\\s+.+$");
    private static final Pattern NUMBER_DATA_PATTERN = Pattern.compile("\\b\\d+\\.?\\d*%?\\b|\\$[\\d,]+(?:\\.\\d{2})?\\b|\\b[\\d,]+\\s*(?:bps|basis\\s+points?)\\b", Pattern.CASE_INSENSITIVE);
    
    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    
    public void ingestDocument(MultipartFile file) throws IOException {
        log.info("Starting granular ingestion of document: {}", file.getOriginalFilename());
        
        // Convert MultipartFile to Resource
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        
        // Process the PDF with paragraph-level reading
        var pdfReader = new ParagraphPdfDocumentReader(resource);
        var documents = pdfReader.get();
        
        // Create multi-level granular chunks
        List<Document> granularChunks = createGranularChunks(documents, file.getOriginalFilename());
        
        // Add documents to vector store
        vectorStore.accept(granularChunks);
        
        log.info("Document '{}' ingested with {} granular chunks (from {} source paragraphs)!", 
                file.getOriginalFilename(), granularChunks.size(), documents.size());
    }
    
    private List<Document> createGranularChunks(List<Document> paragraphs, String filename) {
        List<Document> granularChunks = new ArrayList<>();
        
        for (int i = 0; i < paragraphs.size(); i++) {
            Document paragraph = paragraphs.get(i);
            String content = paragraph.getText();
            Map<String, Object> originalMetadata = paragraph.getMetadata();
            
            // Extract slide number from content or metadata
            String slideNumber = extractSlideNumber(content, originalMetadata);
            
            // 1. Create paragraph-level chunk with enhanced metadata
            granularChunks.add(createEnhancedChunk(
                content, originalMetadata, filename, slideNumber, 
                "paragraph", i, -1, "primary"
            ));
            
            // 2. Extract and create table chunks
            granularChunks.addAll(extractTableChunks(
                content, originalMetadata, filename, slideNumber, i
            ));
            
            // 3. Extract and create list chunks
            granularChunks.addAll(extractListChunks(
                content, originalMetadata, filename, slideNumber, i
            ));
            
            // 4. Create sentence-level chunks for longer paragraphs
            if (content.length() > 300) {
                granularChunks.addAll(extractSentenceChunks(
                    content, originalMetadata, filename, slideNumber, i
                ));
            }
            
            // 5. Extract numerical data chunks
            granularChunks.addAll(extractNumericalDataChunks(
                content, originalMetadata, filename, slideNumber, i
            ));
        }
        
        return granularChunks;
    }
    
    private String extractSlideNumber(String content, Map<String, Object> metadata) {
        // First try to get from metadata
        if (metadata.containsKey("slide") || metadata.containsKey("page")) {
            Object slide = metadata.getOrDefault("slide", metadata.get("page"));
            if (slide != null) {
                return slide.toString();
            }
        }
        
        // Then try to extract from content
        Matcher matcher = SLIDE_NUMBER_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "Unknown";
    }
    
    private Document createEnhancedChunk(String content, Map<String, Object> originalMetadata, 
                                       String filename, String slideNumber, String chunkType, 
                                       int paragraphIndex, int subIndex, String hierarchyLevel) {
        Map<String, Object> metadata = new java.util.HashMap<>(originalMetadata);
        metadata.put("filename", filename);
        metadata.put("slide_number", slideNumber);
        metadata.put("chunk_type", chunkType);
        metadata.put("paragraph_index", paragraphIndex);
        metadata.put("hierarchy_level", hierarchyLevel);
        metadata.put("content_length", content.length());
        
        if (subIndex >= 0) {
            metadata.put("sub_index", subIndex);
            metadata.put("parent_paragraph", paragraphIndex);
        }
        
        // Add content analysis flags
        metadata.put("contains_numbers", NUMBER_DATA_PATTERN.matcher(content).find());
        metadata.put("contains_percentages", content.contains("%"));
        metadata.put("contains_currency", content.contains("$"));
        
        return new Document(content, metadata);
    }
    
    private List<Document> extractTableChunks(String content, Map<String, Object> originalMetadata, 
                                            String filename, String slideNumber, int paragraphIndex) {
        List<Document> tableChunks = new ArrayList<>();
        Matcher matcher = TABLE_PATTERN.matcher(content);
        int tableIndex = 0;
        
        while (matcher.find()) {
            String tableRow = matcher.group().trim();
            if (tableRow.length() > 20) { // Filter out too short matches
                Document tableChunk = createEnhancedChunk(
                    tableRow, originalMetadata, filename, slideNumber,
                    "table_row", paragraphIndex, tableIndex, "secondary"
                );
                tableChunks.add(tableChunk);
                tableIndex++;
            }
        }
        
        return tableChunks;
    }
    
    private List<Document> extractListChunks(String content, Map<String, Object> originalMetadata,
                                           String filename, String slideNumber, int paragraphIndex) {
        List<Document> listChunks = new ArrayList<>();
        Matcher matcher = LIST_PATTERN.matcher(content);
        int listIndex = 0;
        
        while (matcher.find()) {
            String listItem = matcher.group().trim();
            if (listItem.length() > 10) { // Filter out too short matches
                Document listChunk = createEnhancedChunk(
                    listItem, originalMetadata, filename, slideNumber,
                    "list_item", paragraphIndex, listIndex, "secondary"
                );
                listChunks.add(listChunk);
                listIndex++;
            }
        }
        
        return listChunks;
    }
    
    private List<Document> extractSentenceChunks(String content, Map<String, Object> originalMetadata,
                                               String filename, String slideNumber, int paragraphIndex) {
        List<Document> sentenceChunks = new ArrayList<>();
        String[] sentences = SENTENCE_PATTERN.split(content);
        
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].trim();
            if (sentence.length() > 50) { // Only meaningful sentences
                Document sentenceChunk = createEnhancedChunk(
                    sentence, originalMetadata, filename, slideNumber,
                    "sentence", paragraphIndex, i, "tertiary"
                );
                sentenceChunks.add(sentenceChunk);
            }
        }
        
        return sentenceChunks;
    }
    
    private List<Document> extractNumericalDataChunks(String content, Map<String, Object> originalMetadata,
                                                    String filename, String slideNumber, int paragraphIndex) {
        List<Document> numericalChunks = new ArrayList<>();
        Matcher matcher = NUMBER_DATA_PATTERN.matcher(content);
        int numberIndex = 0;
        
        // Extract sentences containing numerical data
        String[] sentences = SENTENCE_PATTERN.split(content);
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].trim();
            if (NUMBER_DATA_PATTERN.matcher(sentence).find() && sentence.length() > 30) {
                Map<String, Object> numericalMetadata = new java.util.HashMap<>(originalMetadata);
                numericalMetadata.putAll(createEnhancedChunk(
                    sentence, originalMetadata, filename, slideNumber,
                    "numerical_data", paragraphIndex, numberIndex, "analytical"
                ).getMetadata());
                numericalMetadata.put("contains_financial_data", true);
                
                Document numericalChunk = new Document(sentence, numericalMetadata);
                numericalChunks.add(numericalChunk);
                numberIndex++;
            }
        }
        
        return numericalChunks;
    }
}
