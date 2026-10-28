package org.zexnocs.teanekoagent.llm.instance.deepseek;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelService;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoapp.TeaNekoAppApplication;

import java.util.List;

/**
 * DeepSeek 模型输出测试。
 * <br>该测试直接使用 file_config 中 {@code deepseek} 的默认 options，并把模型输出打印到控制台。
 * <br>只有显式设置 TEANEKO_LIVE_LLM_TESTS=true 才启动 Spring 并调用模型，密钥从 Spring 解析。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.1
 * @version 4.5.0-l
 */
@EnabledIfEnvironmentVariable(named = "TEANEKO_LIVE_LLM_TESTS", matches = "true")
@SpringBootTest(classes = TeaNekoAppApplication.class)
public class DeepSeekModelOutputTest {
    /**
     * LLM 模型服务。
     */
    @Autowired
    private LLMModelService llmModelService;

    @Autowired
    private ICredentialResolver credentialResolver;

    /**
     * 使用 DeepSeek file_config 默认 options 调用模型并打印输出。
     */
    @Test
    public void printDeepSeekOutputWithFileConfigDefaults() {
        System.out.println("-------------------------------------");
        var modelId = LLMModelId.of(DeepSeekChatModel.PROVIDER);
        var defaultOptions = llmModelService.getDefaultOptions(modelId);
        Assertions.assertTrue(defaultOptions.findModel().isPresent());
        credentialResolver.require(DeepSeekChatModel.PROVIDER);

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

}
