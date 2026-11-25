package com.xiaohua.polaris.rag;

public record RagSource(String source, String title, Integer chunkIndex, Double score) {
}
