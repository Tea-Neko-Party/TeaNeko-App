package org.zexnocs.teanekoagent.integration.monitor;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMRequestSnapshot;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 独立诊断记录保留完整统一输入与响应，并明确服务端未知边界。
 * <br>2026/11/08：验证系统提示、工具声明、长正文及敏感状态的脱敏。
 *
 * @author zExNocs
 * @date 2026/11/08
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class InteractionDiagnosticLogTest {
    /** 完整诊断不受预览长度影响，秘密、供应商加密状态与签名媒体仍隐藏。 */
    @Test
    void retainsCompleteProtocolWithoutCredentialsOrEncryptedState() {
        var user = LLMUserMessage.builder().contents(new LLMContentListBuilder()
                .addText("真实输入".repeat(1000) + "末尾证据")
                .addText("{\"api_key\":\"diagnostic-secret\"}").build()).build();
        var system = LLMSystemMessage.builder().contents(new LLMContentListBuilder().addText("完整系统提示").build()).build();
        var answer = LLMAssistantMessage.builder().contents(new LLMContentListBuilder()
                .addText("完整输出".repeat(1000) + "末尾答案").build())
                .providerMetadata(Map.of("encrypted_content", "diagnostic-encrypted")).build();
        var response = LLMResult.builder().model("test-model").choices(List.of(LLMChoice.builder()
                .message(answer).finishReason("stop").build())).build();
        var input = new LLMRequestSnapshot(List.of(system, user), "{\"tools\":[{\"name\":\"完整工具\"}]}");
        var attempt = new LLMInteractionResult.ModelAttempt(1, Optional.of(response), Optional.empty(), Optional.of(input));
        var result = new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(system, user, answer),
                answer, List.of(attempt), List.of(), Map.of());
        var request = UUID.randomUUID();
        var record = InteractionDiagnosticLog.record(request, result, attempt);
        assertTrue(record.contains("末尾证据"));
        assertTrue(record.contains("末尾答案"));
        assertTrue(record.contains("完整系统提示"));
        assertTrue(record.contains("完整工具"));
        assertFalse(record.contains("diagnostic-secret"));
        assertFalse(record.contains("diagnostic-encrypted"));
        assertFalse(record.contains("\n"));
        var document = new JsonMapper().readTree(record);
        assertEquals(request.toString(), document.path("requestId").asString());
        assertEquals("APPLICATION_MODEL_PORT", document.path("boundary").asString());
        assertFalse(document.path("serverTokenContextAvailable").asBoolean());
        assertEquals(2, document.at("/request/messages").size());
        assertEquals(1, document.at("/response/choices").size());
    }
}
