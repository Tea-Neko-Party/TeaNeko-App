package org.zexnocs.teanekoagent.agent.prompt;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;

import java.util.List;
import java.util.Objects;

/**
 * 已经验证的实际交互与历史投影，只能由布局校验工厂生成，不用任意跳过条数充当证明。
 * 此值仅含不可变数据；通用序列化不重复导出整份交互和临时资料。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@JsonIgnoreType
public final class AgentPromptTranscript {
    private final AgentPromptInput input;
    private final LLMInteractionResult interaction;
    private final List<ILLMMessage> messages;

    /**
     * 保存工厂已经验证的固定布局和交互，不持有转换器、控制或服务。
     * @param input 原输入布局
     * @param interaction 被绑定的不可变交互
     * @param messages 已验证的历史投影
     */
    private AgentPromptTranscript(AgentPromptInput input, LLMInteractionResult interaction, List<ILLMMessage> messages) {
        this.input = input; this.interaction = interaction; this.messages = messages;
    }

    /**
     * 在真实交互完成后验证投影，失败不生成可提交历史。
     * @param input 本轮实际布局
     * @param interaction 原始成功交互
     * @param codec 本轮协议度量器
     * @return 与该交互绑定的只读投影
     * @throws NullPointerException 当输入、交互或度量器为空时
     * @throws IllegalArgumentException 当实际输入或响应合同不成立时
     */
    public static AgentPromptTranscript create(AgentPromptInput input, LLMInteractionResult interaction, LLMMessageCodec codec) {
        Objects.requireNonNull(input, "Prompt 布局不能为空。"); Objects.requireNonNull(interaction, "Prompt 交互不能为空。");
        return new AgentPromptTranscript(input, interaction, input.project(interaction, Objects.requireNonNull(codec, "Prompt 度量器不能为空。")));
    }

    /**
     * 提供原始消息与临时片段布局，供会话独立复核，不公开可执行对象。
     * @return 固定输入布局
     */
    public AgentPromptInput input() { return input; }

    /**
     * 取得本投影唯一绑定的原交互，结果容器不得改配另一份响应。
     * @return 原始不可变交互
     */
    public LLMInteractionResult interaction() { return interaction; }

    /**
     * 返回已验证且不含本轮临时资料的历史，提交仍需当前引用与会话代次仲裁。
     * @return 深层冻结的完整原消息及新响应
     */
    public List<ILLMMessage> messages() { return messages; }
}
