package com.xiaohua.polaris.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

final class StubEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 64;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(vector(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private static float[] vector(String text) {
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < text.length(); i++) {
            vector[Math.floorMod(Character.hashCode(text.charAt(i)), DIMENSIONS)] += 1.0f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= (float) Math.sqrt(norm);
            }
        }
        return vector;
    }
}
