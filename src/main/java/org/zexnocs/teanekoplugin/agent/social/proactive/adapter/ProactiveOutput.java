package org.zexnocs.teanekoplugin.agent.social.proactive.adapter;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonType;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.JsonConstraint;

/**
 * 主动发言决策，生成成功与实际发送仍由原管道分别确认。
 * <br>2026/11/06：共享类型化 JSON 描述及解码，不扩大发送目标权限。
 * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
 *
 * @param speak 是否发言
 * @param text 有界正文或明确空字符串
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
@LLMJsonType(maxCharacters = 6000)
public record ProactiveOutput(
        @Description("无必要时为 false，不为了输出而强行发言。") boolean speak,
        @Description("speak=false 时必须为空字符串；true 时为非空简短发言，最多1000字。") @JsonConstraint(maxLength = 1000) String text) {
    /**
     * @param speak 明确发言决定
     * @param text 正文
     * @throws IllegalArgumentException 当发言决定与正文矛盾或超出容量时
     */
    public ProactiveOutput {
        if (text == null || !speak && !text.isEmpty() || speak && (text.isBlank() || text.length() > 1000)) {
            throw new IllegalArgumentException("主动发言的决定与正文不一致，或正文超过容量。");
        }
    }
}
