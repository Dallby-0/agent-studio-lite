package com.agentworkflow.service.impl;

import com.agentworkflow.entity.KnowledgeBase;
import com.agentworkflow.service.AgentService;
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

    @Autowired
    private AgentService agentService;

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

    @Override
    public boolean deleteKnowledgeBase(Long knowledgeBaseId) {
        // 清理 Agent 关联，再删除向量库数据
        agentService.removeAgentKnowledgeByKnowledgeBaseId(knowledgeBaseId);
        return vectorStoreService.deleteKnowledgeBase(knowledgeBaseId);
    }

    /**
     * 优化的文本切分：优先按 Markdown 标题切分，其次按长度切分
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 统一换行符
        text = text.replace("\r\n", "\n");
        
        // 按行分割，以便检测 Markdown 标题
        String[] lines = text.split("\n");
        
        List<String> currentBuffer = new ArrayList<>();
        int currentLength = 0;

        for (String line : lines) {
            // 检测 Markdown 标题（# 开头）
            boolean isHeader = line.trim().startsWith("#");
            
            // 如果遇到标题，且缓冲区不为空，强制切分（确保标题是新块的开始）
            if (isHeader && !currentBuffer.isEmpty()) {
                chunks.add(String.join("\n", currentBuffer).trim());
                currentBuffer.clear();
                currentLength = 0;
            }
            
            // 如果单行过长（超过 chunk size），使用句子切分逻辑处理
            if (line.length() > chunkSize) {
                // 先提交当前缓冲区
                if (!currentBuffer.isEmpty()) {
                    chunks.add(String.join("\n", currentBuffer).trim());
                    currentBuffer.clear();
                    currentLength = 0;
                }
                // 对长行进行细粒度切分
                chunks.addAll(splitLongText(line, chunkSize, overlap));
                continue;
            }

            // 如果加上新行超过块大小
            if (currentLength + line.length() > chunkSize) {
                chunks.add(String.join("\n", currentBuffer).trim());
                
                // 处理重叠：保留最后一行作为上下文（对于结构化文档，少量重叠即可）
                if (!currentBuffer.isEmpty()) {
                    String lastLine = currentBuffer.get(currentBuffer.size() - 1);
                    currentBuffer.clear();
                    currentBuffer.add(lastLine);
                    currentLength = lastLine.length();
                } else {
                    currentBuffer.clear();
                    currentLength = 0;
                }
            }

            currentBuffer.add(line);
            currentLength += line.length(); // 这里简单计算，未包含换行符长度，误差可接受
        }

        // 处理剩余缓冲区
        if (!currentBuffer.isEmpty()) {
            chunks.add(String.join("\n", currentBuffer).trim());
        }

        return chunks;
    }

    /**
     * 辅助方法：对长文本按句子切分
     */
    private List<String> splitLongText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        // 按句子分隔符切分
        String[] sentences = text.split("(?<=[。！？!?])");
        
        List<String> buffer = new ArrayList<>();
        int len = 0;
        
        for (String sent : sentences) {
            if (sent.trim().isEmpty()) continue;
            
            if (len + sent.length() > chunkSize) {
                if (!buffer.isEmpty()) {
                    chunks.add(String.join("", buffer).trim());
                    // 简单重叠处理
                    while (len > overlap && !buffer.isEmpty()) {
                        len -= buffer.remove(0).length();
                    }
                }
                
                // 如果单个句子还是太长，强制切分
                if (sent.length() > chunkSize) {
                    int start = 0;
                    while (start < sent.length()) {
                        int end = Math.min(sent.length(), start + chunkSize);
                        chunks.add(sent.substring(start, end).trim());
                        start += chunkSize - overlap;
                    }
                    buffer.clear();
                    len = 0;
                    continue;
                }
            }
            buffer.add(sent);
            len += sent.length();
        }
        
        if (!buffer.isEmpty()) {
            chunks.add(String.join("", buffer).trim());
        }
        return chunks;
    }
}

