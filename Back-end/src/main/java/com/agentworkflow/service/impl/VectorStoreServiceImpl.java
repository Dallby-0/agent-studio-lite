package com.agentworkflow.service.impl;

import com.agentworkflow.entity.KnowledgeBase;
import com.agentworkflow.service.VectorStoreService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 基于 PostgreSQL + pgvector 的向量存储实现
 */
@Service
public class VectorStoreServiceImpl implements VectorStoreService {

    @Autowired
    @Qualifier("vectorJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Override
    public Long createKnowledgeBase(String name, String description) {
        String sql = "INSERT INTO knowledge_base (name, description) VALUES (?, ?) RETURNING id";
        return jdbcTemplate.queryForObject(sql, Long.class, name, description);
    }

    @Override
    public List<KnowledgeBase> listKnowledgeBases() {
        String sql = "SELECT id, name, description, created_at FROM knowledge_base ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new RowMapper<KnowledgeBase>() {
            @Override
            public KnowledgeBase mapRow(ResultSet rs, int rowNum) throws SQLException {
                KnowledgeBase kb = new KnowledgeBase();
                kb.setId(rs.getLong("id"));
                kb.setName(rs.getString("name"));
                kb.setDescription(rs.getString("description"));
                kb.setCreatedAt(rs.getTimestamp("created_at"));
                return kb;
            }
        });
    }

    @Override
    public void insertChunk(Long knowledgeBaseId, String content, String metadataJson, float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return;
        }
        String vectorLiteral = toVectorLiteral(embedding);
        String sql = "INSERT INTO kb_chunk (knowledge_base_id, content, metadata, embedding) " +
                "VALUES (?, ?, CAST(? AS jsonb), ?::vector)";
        jdbcTemplate.update(sql, knowledgeBaseId, content, metadataJson, vectorLiteral);
    }

    @Override
    public List<SimilarChunk> searchTopK(Long knowledgeBaseId, float[] embedding, int topK) {
        List<SimilarChunk> results = new ArrayList<>();
        if (embedding == null || embedding.length == 0) {
            return results;
        }
        String vectorLiteral = toVectorLiteral(embedding);
        String sql = "SELECT id, knowledge_base_id, content, metadata, " +
                "(embedding <-> ?::vector) AS distance " +
                "FROM kb_chunk WHERE knowledge_base_id = ? " +
                "ORDER BY embedding <-> ?::vector LIMIT ?";

        return jdbcTemplate.query(sql, new Object[]{vectorLiteral, knowledgeBaseId, vectorLiteral, topK},
                new RowMapper<SimilarChunk>() {
                    @Override
                    public SimilarChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
                        SimilarChunk chunk = new SimilarChunk();
                        chunk.setId(rs.getLong("id"));
                        chunk.setKnowledgeBaseId(rs.getLong("knowledge_base_id"));
                        chunk.setContent(rs.getString("content"));
                        chunk.setMetadata(rs.getString("metadata"));
                        chunk.setScore(rs.getDouble("distance"));
                        return chunk;
                    }
                });
    }

    @Override
    public boolean deleteKnowledgeBase(Long knowledgeBaseId) {
        // 先删除知识块，再删除知识库元信息
        jdbcTemplate.update("DELETE FROM kb_chunk WHERE knowledge_base_id = ?", knowledgeBaseId);
        int affected = jdbcTemplate.update("DELETE FROM knowledge_base WHERE id = ?", knowledgeBaseId);
        return affected > 0;
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}

