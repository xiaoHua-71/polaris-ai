package com.xiaohua.polaris.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;

@RestController
@RequestMapping("/chat")
public class ChatExtractController {

    private static final Logger log = LoggerFactory.getLogger(ChatExtractController.class);

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ChatClient structuredChatClient;
    private final Resource extractPrompt;
    private final BeanOutputConverter<ExtractedPerson> converter = new BeanOutputConverter<>(ExtractedPerson.class);

    public ChatExtractController(@Qualifier("structuredChatClient") ChatClient structuredChatClient,
            @Value("classpath:prompts/extract-person.st") Resource extractPrompt) {
        this.structuredChatClient = structuredChatClient;
        this.extractPrompt = extractPrompt;
    }

    @PostMapping("/extract")
    public ExtractedPerson extract(@RequestBody ExtractRequest request) {
        validate(request);

        String raw = structuredChatClient.prompt()
                .user(user -> user.text(extractPrompt)
                        .param("text", request.message())
                        .param("format", converter.getFormat()))
                .call()
                .content();

        ExtractedPerson person;
        try {
            person = converter.convert(raw);
        }
        catch (JacksonException ex) {
            log.debug("结构化抽取失败，原始输出={}", raw);
            log.warn("结构化抽取失败：模型输出无法解析为 JSON");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "模型输出无法解析为结构化数据", ex);
        }
        if (person == null || person.hasNoContent()) {
            log.debug("结构化抽取失败，原始输出={}", raw);
            log.warn("结构化抽取失败：模型未返回任何字段");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "模型未抽取到任何字段");
        }
        return person;
    }

    private static void validate(ExtractRequest request) {
        if (!StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }
        if (request.message().length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "message 长度不能超过 " + MAX_MESSAGE_LENGTH);
        }
    }
}
