package org.zexnocs.teanekoagent_old.agent.thinking;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Agent 结构化输出测试。
 * <br>验证最终答案、公开思考摘要和简单输出工厂方法的结果结构。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class AgentOutputTest {
    /**
     * 验证完整输出会同时保留最终答案和允许呈现的思考摘要。
     */
    @Test
    void keepsFinalAnswerAndPublicThoughtSummary() {
        var output = new AgentOutput(
                List.of(new AgentThoughtStep(1, AgentThoughtPhase.FINAL_CHECK, "checked")),
                "answer",
                AgentOutputMetadata.empty()
        );

        Assertions.assertEquals("answer", output.answer());
        Assertions.assertEquals("checked", output.thoughts().getFirst().summary());
    }

    /**
     * 验证简单输出只包含最终答案，不会创建额外思考步骤。
     */
    @Test
    void simpleOutputContainsOnlyAnswer() {
        var output = AgentOutput.simple("answer");

        Assertions.assertEquals("answer", output.answer());
        Assertions.assertTrue(output.thoughts().isEmpty());
    }
}
