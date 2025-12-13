package com.agentworkflow.service;

import com.agentworkflow.entity.KnowledgeBase;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理服务：负责文件解析、切分、向量化并写入 pgvector
 */
public interface KnowledgeService {

    /**
     * 通过上传文件创建一个新的知识库并完成切分+向量化
     *
     * @return 新建知识库的ID
     */
    Long createKnowledgeBaseWithFile(String name, String description, MultipartFile file);

    /**
     * 获取所有知识库列表
     */
    List<KnowledgeBase> listKnowledgeBases();

    /**
     * 删除知识库及其数据
     */
    boolean deleteKnowledgeBase(Long knowledgeBaseId);
}

