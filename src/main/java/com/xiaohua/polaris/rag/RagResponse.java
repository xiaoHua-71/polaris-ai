package com.xiaohua.polaris.rag;

import java.util.List;

public record RagResponse(String content, List<RagSource> sources) {
}
