-- 初始化 pgvector 数据库：向量扩展 + 知识库表

CREATE EXTENSION IF NOT EXISTS vector;

-- 知识库表（元信息）
CREATE TABLE IF NOT EXISTS knowledge_base (
  id           BIGSERIAL PRIMARY KEY,
  name         TEXT        NOT NULL,
  description  TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 知识块表：每一段文本 + 对应向量
-- 注意：embedding 向量维度需要与模型 BAAI/bge-small-zh-v1.5 对齐（512）
CREATE TABLE IF NOT EXISTS kb_chunk (
  id                BIGSERIAL PRIMARY KEY,
  knowledge_base_id BIGINT      NOT NULL,
  content           TEXT        NOT NULL,
  metadata          JSONB,
  embedding         VECTOR(512)
);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_kb_id
  ON kb_chunk(knowledge_base_id);

-- 使用 ivfflat 索引加速相似度搜索（余弦距离）
CREATE INDEX IF NOT EXISTS idx_kb_chunk_embedding
  ON kb_chunk
  USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 100);

