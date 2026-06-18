package org.zexnocs.teanekoagent_old.llm.instance.deepseek;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekoagent_old.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelService;
import org.zexnocs.teanekoagent_old.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoapp.TeaNekoAppApplication;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 模型输出测试。
 * <br>该测试直接使用 file_config 中 {@code deepseek} 的默认 options，并把模型输出打印到控制台。
 * <br>如果未配置 DeepSeek 的 {@code apiKey}，测试会自动跳过，避免无配置环境下误失败。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@SpringBootTest(classes = TeaNekoAppApplication.class)
public class DeepSeekModelOutputTest {
    /**
     * LLM 模型服务。
     */
    @Autowired
    private LLMModelService llmModelService;

    /**
     * 使用 DeepSeek file_config 默认 options 调用模型并打印输出。
     */
    @Test
    public void printDeepSeekOutputWithFileConfigDefaults() {
        System.out.println("-------------------------------------");
        var modelId = LLMModelId.of(DeepSeekChatModel.PROVIDER);
        var defaultOptions = llmModelService.getDefaultOptions(modelId);
        Assertions.assertInstanceOf(DeepSeekModelOptions.class, defaultOptions,
                "DeepSeek default options should keep DeepSeekModelOptions type.");
        assumeDeepSeekConfigured(defaultOptions.getMetadata());

        var messages = List.<ILLMMessage>of(
                LLMSystemMessage.builder()
                        .contents(LLMContentListBuilder.builder()
                                .addText("你是 TeaNeko Agent 的 DeepSeek 集成测试助手。")
                                .build())
                        .build(),
                LLMUserMessage.builder()
                        .contents(LLMContentListBuilder.builder()
                                .addText("你好，请做一个简短的自我介绍。")
                                .build())
                        .build()
        );
        var result = llmModelService.call(modelId, new LLMPrompt(messages))
                .finish()
                .join();
        Assertions.assertNotNull(result, "DeepSeek result should not be null.");
        printResult(result);
        System.out.println("-------------------------------------");
    }

    /**
     * 假定 DeepSeek 已经配置访问参数。
     *
     * @param metadata 默认 options metadata
     */
    private static void assumeDeepSeekConfigured(Map<String, Object> metadata) {
        var apiKey = metadataValue(metadata, DeepSeekChatModel.API_KEY_METADATA);
        Assumptions.assumeTrue(isConfigured(apiKey),
                "Skip DeepSeek live test: config/llm/main-config.yml missing deepseek api-key.");
    }

    /**
     * 打印 DeepSeek 响应结果。
     *
     * @param result LLM 响应结果
     */
    private static void printResult(ILLMResult result) {
        System.out.println("========== DeepSeek LLM Result ==========");
        System.out.println("id: " + result.getId());
        System.out.println("object: " + result.getObject());
        System.out.println("created: " + result.getCreated());
        System.out.println("model: " + result.getModel());
        result.getFirstMessage().ifPresent(message ->
                System.out.println("content: " + assistantText(message)));
        var usage = result.getUsage();
        if (usage != null) {
            System.out.println("prompt tokens: " + usage.getPromptTokens());
            System.out.println("completion tokens: " + usage.getCompletionTokens());
            System.out.println("total tokens: " + usage.getTotalTokens());
            System.out.println("reasoning tokens: " + usage.getReasoningTokens());
        }
        System.out.println("=========================================");
    }

    /**
     * 提取 assistant 消息中的文本内容。
     *
     * @param message assistant 消息
     * @return 文本内容
     */
    private static String assistantText(ILLMAssistantMessage message) {
        var text = new StringBuilder();
        if (message.getContents() == null) {
            return "";
        }
        for (var content : message.getContents()) {
            if (content != null && content.getContentPart() instanceof TextLLMContentPart textPart) {
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(textPart.getText());
            }
        }
        return text.toString();
    }

    /**
     * 读取 metadata 字符串值。
     *
     * @param metadata metadata 映射
     * @param key 字段名
     * @return 字符串值
     */
    private static String metadataValue(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        var value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 判断配置值是否可用于真实 API 调用。
     *
     * @param value 配置值
     * @return 如果配置值可用则返回 {@code true}
     */
    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank() && !value.startsWith("${");
    }
}
