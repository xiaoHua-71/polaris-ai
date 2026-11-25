package com.xiaohua.polaris.rag;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 手动冒烟：验证本地 ONNX 模型可加载、向量维度与语义距离正常。
 * 默认跳过；需要模型文件时用 RAG_SMOKE=true 运行。
 */
@EnabledIfEnvironmentVariable(named = "RAG_SMOKE", matches = "true")
class RagEmbeddingSmokeTest {

    @Test
    void loadsLocalModelAndEmbedsChineseText() throws Exception {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setModelResource(new FileSystemResource("models/bge-small-zh-v1.5/model.onnx"));
        model.setTokenizerResource(new FileSystemResource("models/bge-small-zh-v1.5/tokenizer.json"));
        model.afterPropertiesSet();
        try {
            float[] related = model.embed("如何配置工具调用轮次上限");
            float[] similar = model.embed("工具最多调用几次");
            float[] unrelated = model.embed("今天的天气怎么样");

            assertThat(related).hasSize(512);
            assertThat(cosine(related, similar)).isGreaterThan(cosine(related, unrelated));
        }
        finally {
            model.close();
        }
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
