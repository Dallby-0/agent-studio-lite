package com.agentworkflow.controller;

import com.agentworkflow.entity.AgentKnowledge;
import com.agentworkflow.entity.KnowledgeBase;
import com.agentworkflow.service.AgentService;
import com.agentworkflow.service.EmbeddingService;
import com.agentworkflow.service.KnowledgeService;
import com.agentworkflow.service.VectorStoreService;
import com.agentworkflow.service.VectorStoreService.SimilarChunk;
import com.agentworkflow.utils.ApiResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库相关接口：上传文件、列出知识库等
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    /**
     * 向量检索测试接口
     */
    @GetMapping("/vector-search")
    public ApiResponse vectorSearch(
            @RequestParam("query") String query,
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ApiResponse.fail(400, "查询内容不能为空");
            }
            // 1. 向量化
            float[] embedding = embeddingService.embed(query);
            if (embedding == null || embedding.length == 0) {
                return ApiResponse.fail(500, "向量化失败");
            }
            // 2. 检索
            List<SimilarChunk> results = vectorStoreService.searchTopK(knowledgeBaseId, embedding, topK);
            return ApiResponse.success(results);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.fail(500, "检索失败: " + e.getMessage());
        }
    }

    /**
     * 上传知识文件（PDF/Word/Markdown 等），自动解析 + 切分 + 向量化
     *
     * @param file        知识文件
     * @param agentId     可选，若传入则自动把新知识库绑定到该 Agent
     * @param name        可选，知识库名称；不传则使用文件名
     * @param description 可选，知识库描述
     */
    @PostMapping("/upload")
    public ApiResponse uploadKnowledge(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "agentId", required = false) Long agentId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description) {
        try {
            if (file == null || file.isEmpty()) {
                return ApiResponse.fail(400, "上传文件不能为空");
            }

            String kbName = (name != null && !name.isEmpty()) ? name : file.getOriginalFilename();
            Long kbId = knowledgeService.createKnowledgeBaseWithFile(kbName, description, file);

            Long agentKnowledgeId = null;
            if (agentId != null) {
                AgentKnowledge ak = new AgentKnowledge();
                ak.setAgentId(agentId);
                ak.setKnowledgeBaseId(kbId);
                ak.setCreatedAt(new Date());
                AgentKnowledge saved = agentService.addAgentKnowledge(ak);
                agentKnowledgeId = saved.getId();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("knowledgeBaseId", kbId);
            data.put("agentId", agentId);
            data.put("agentKnowledgeId", agentKnowledgeId);

            return ApiResponse.success(data);
        } catch (Exception e) {
            System.err.println("上传知识库失败: " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.fail(500, "上传知识库失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有知识库列表
     */
    @GetMapping("/bases")
    public ApiResponse listKnowledgeBases() {
        List<KnowledgeBase> bases = knowledgeService.listKnowledgeBases();
        return ApiResponse.success(bases);
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/bases/{id}")
    public ApiResponse deleteKnowledgeBase(@PathVariable("id") Long knowledgeBaseId) {
        try {
            boolean deleted = knowledgeService.deleteKnowledgeBase(knowledgeBaseId);
            if (deleted) {
                return ApiResponse.success("删除成功");
            }
            return ApiResponse.fail(404, "知识库不存在或已删除");
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.fail(500, "删除知识库失败: " + e.getMessage());
        }
    }
}

