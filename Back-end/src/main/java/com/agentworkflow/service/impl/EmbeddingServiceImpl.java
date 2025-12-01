package com.agentworkflow.service.impl;

import com.agentworkflow.config.properties.EmbeddingProperties;
import com.agentworkflow.service.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Embedding 服务实现：调用本地 sentence-transformers HTTP 服务
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private EmbeddingProperties embeddingProperties;

    @Override
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        List<float[]> list = embedBatch(Collections.singletonList(text));
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String url = embeddingProperties.getBaseUrl() + "/embeddings";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of("input", texts);
            String json = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                System.err.println("Embedding 服务返回非 2xx 状态: " + response.getStatusCode());
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return Collections.emptyList();
            }

            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.get("embedding");
                if (embeddingNode == null || !embeddingNode.isArray()) {
                    continue;
                }
                float[] vec = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vec[i] = (float) embeddingNode.get(i).asDouble();
                }
                vectors.add(vec);
            }

            return vectors;
        } catch (Exception e) {
            System.err.println("调用 Embedding 服务失败: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}

