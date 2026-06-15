package org.zexnocs.teanekoagent.agent.thinking;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zexnocs.teanekocore.utils.JsonDescriptionUtil;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 受控思考服务测试。
 * <br>验证结构化决策解析、工具调用约束、纯文本回退和思考 Prompt 生成。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class AgentThinkingServiceTest {
    /**
     * JSON 描述生成器测试替身。
     */
    private final JsonDescriptionUtil jsonDescriptionUtil = Mockito.mock(JsonDescriptionUtil.class);

    /**
     * 待测试的 Agent 思考服务。
     */
    private final AgentThinkingService service = new AgentThinkingService(
            new ObjectMapper(),
            jsonDescriptionUtil
    );

    /**
     * 验证结构化模型决策能够解析，并按配置截断公开思考摘要。
     */
    @Test
    void parsesStructuredDecisionAndLimitsThoughtSummary() {
        var decision = service.parseDecision(
                "{\"thoughtSummary\":\"123456\",\"answer\":\"done\",\"confidence\":0.8}",
                false,
                4
        );

        Assertions.assertEquals("1234", decision.getThoughtSummary());
        Assertions.assertEquals("done", decision.getAnswer());
        Assertions.assertEquals(0.8, decision.getConfidence());
    }

    /**
     * 验证模型请求调用工具时，尚未完成的答案不会被当作最终答案返回。
     */
    @Test
    void removesPrematureAnswerWhenModelRequestsTool() {
        var decision = service.parseDecision(
                "{\"thoughtSummary\":\"need data\",\"answer\":\"premature\",\"confidence\":1}",
                true,
                100
        );

        Assertions.assertEquals("need data", decision.getThoughtSummary());
        Assertions.assertEquals("", decision.getAnswer());
    }

    /**
     * 验证最终步骤收到非 JSON 文本时，会将原始文本作为最终答案回退使用。
     */
    @Test
    void fallsBackToPlainTextForFinalAnswer() {
        var decision = service.parseDecision("plain answer", false, 100);

        Assertions.assertEquals("plain answer", decision.getAnswer());
        Assertions.assertEquals("", decision.getThoughtSummary());
    }

    /**
     * 验证思考 Prompt 包含步骤预算、最终步骤工具限制、摘要长度和 JSON schema。
     */
    @Test
    void promptRequiresBoundedUserSafeSummary() {
        Mockito.when(jsonDescriptionUtil.toJsonDescription(AgentModelDecision.class))
                .thenReturn("{schema}");

        var component = service.buildPromptComponent(2, 3, 120, true);

        Assertions.assertTrue(component.content().contains("Current step: 2 of 3"));
        Assertions.assertTrue(component.content().contains("Do not call tools"));
        Assertions.assertTrue(component.content().contains("no longer than 120 characters"));
        Assertions.assertTrue(component.content().contains("{schema}"));
    }
}
