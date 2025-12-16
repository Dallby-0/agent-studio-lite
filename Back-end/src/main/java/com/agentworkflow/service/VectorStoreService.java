package com.agentworkflow.service;

import com.agentworkflow.entity.KnowledgeBase;
import java.util.List;

/**
 * 向量存储服务接口（基于 PostgreSQL + pgvector）
 */
public interface VectorStoreService {

    /**
     * 新建一个知识库
     */
    Long createKnowledgeBase(String name, String description);

    /**
     * 列出所有知识库
     */
    List<KnowledgeBase> listKnowledgeBases();

    /**
     * 删除知识库以及其下的所有向量块
     *
     * @param knowledgeBaseId 知识库ID
     * @return 是否删除成功
     */
    boolean deleteKnowledgeBase(Long knowledgeBaseId);

    /**
     * 向知识库中插入一条知识块
     *
     * @param knowledgeBaseId 知识库ID
     * @param content         文本内容
     * @param metadataJson    元数据（JSON 字符串，可为 null）
     * @param embedding       向量
     */
    void insertChunk(Long knowledgeBaseId, String content, String metadataJson, float[] embedding);

    /**
     * 在指定知识库中检索与查询向量最相近的若干条知识块
     */
    List<SimilarChunk> searchTopK(Long knowledgeBaseId, float[] embedding, int topK);

    /**
     * 检索结果数据结构
     */
    class SimilarChunk {
        private Long id;
        private Long knowledgeBaseId;
        private String content;
        private String metadata;
        /**
         * 距离（越小越相似）
         */
        private double score;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getKnowledgeBaseId() {
            return knowledgeBaseId;
        }

        public void setKnowledgeBaseId(Long knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getMetadata() {
            return metadata;
        }

        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }
}

