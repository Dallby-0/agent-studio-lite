package com.agentworkflow.service.impl;

import com.agentworkflow.entity.KnowledgeBase;
import com.agentworkflow.service.EmbeddingService;
import com.agentworkflow.service.KnowledgeService;
import com.agentworkflow.service.VectorStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理实现：解析上传文件，进行切分和向量化，并写入 pgvector
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final Tika tika = new Tika();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private EmbeddingService embeddingService;

    @Override
    public Long createKnowledgeBaseWithFile(String name, String description, MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            String text = tika.parseToString(is);
            List<String> chunks = splitText(text, 500, 100);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("解析后的文本为空，无法创建知识库");
            }

            Long kbId = vectorStoreService.createKnowledgeBase(name, description);

            List<float[]> embeddings = embeddingService.embedBatch(chunks);
            for (int i = 0; i < chunks.size(); i++) {
                String content = chunks.get(i).trim();
                if (content.isEmpty()) {
                    continue;
                }
                float[] emb = (embeddings != null && i < embeddings.size()) ? embeddings.get(i) : null;
                if (emb == null || emb.length == 0) {
                    continue;
                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", file.getOriginalFilename());
                metadata.put("index", i);
                String metadataJson = objectMapper.writeValueAsString(metadata);

                vectorStoreService.insertChunk(kbId, content, metadataJson, emb);
            }

            return kbId;
        } catch (Exception e) {
            System.err.println("处理知识库文件失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("处理知识库文件失败", e);
        }
    }

    @Override
    public List<KnowledgeBase> listKnowledgeBases() {
        return vectorStoreService.listKnowledgeBases();
    }

    /**
     * 简单的文本切分：按字符数固定窗口并设置重叠
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null) {
            return chunks;
        }
        text = text.trim();
        int len = text.length();
        if (len == 0) {
            return chunks;
        }

        int start = 0;
        while (start < len) {
            int end = Math.min(len, start + chunkSize);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= len) {
                break;
            }
            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
        }
        return chunks;
    }
}

