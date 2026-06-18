package org.zexnocs.teanekoagent_old.agent.thinking;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * Agent 每次模型调用要求返回的受控结构化决策。
 * <br>该对象只允许模型提供简短的可公开思考摘要，不接收或保存完整私有链式思考。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentModelDecision {
    /**
     * 本步骤的简短、高层思考摘要。
     */
    @Description("简短说明当前判断、所依据的信息以及下一步行动，不得包含完整私有链式思考。")
    private String thoughtSummary = "";

    /**
     * 最终用户答案；发起工具调用时必须为空。
     */
    @Description("最终能够直接呈现给用户的答案；如果本步骤需要调用工具则填写空字符串。")
    private String answer = "";

    /**
     * 对当前最终答案的置信度。
     */
    @Description("对最终答案的置信度，范围为 0 到 1；尚未形成最终答案时使用 0。")
    private double confidence;
}
