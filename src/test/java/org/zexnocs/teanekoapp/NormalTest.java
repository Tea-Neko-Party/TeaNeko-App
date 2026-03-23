package org.zexnocs.teanekoapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekoagent.llm_api_framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm_api_framework.message.content.LLMContentListBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * 常规测试类，用于测试任何想要测试的功能。
 * <br>建议测试完删除。
 *
 * @author zExNocs
 * @date 2026/02/10
 * @since 4.0.0
 */
@SpringBootTest
public class NormalTest {
    @Test
    public void run() {
        var mapper = new ObjectMapper();
        var messageA = LLMAssistantMessage.builder()
                .contents(LLMContentListBuilder.builder().addText("hello").build())
                .build();
        var messageB = LLMAssistantMessage.builder()
                .contents(LLMContentListBuilder.builder().addText("hello").addText("world").build())
                .build();
        System.out.println(mapper.writeValueAsString(messageA));
        System.out.println(mapper.writeValueAsString(messageB));
    }
}