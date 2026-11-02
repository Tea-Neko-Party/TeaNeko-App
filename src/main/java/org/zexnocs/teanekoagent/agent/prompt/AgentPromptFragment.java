package org.zexnocs.teanekoagent.agent.prompt;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 本轮临时参考资料；一个片段必须是可独立移除的完整证据或查询结果，不承载运行规则和授权。
 * @param name 稳定来源名称，只用于追溯，不解释为用户身份
 * @param text 完整数据正文，不能包含执行资源或凭据
 * @param expiresAt 可选最早证据到期时间，动态来源在使用/重放时必须复核
 * @param transientToolResults 只供原轮次使用的工具结果，历史投影保留配对并用占位文本替换正文
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentPromptFragment(String name, String text, @Nullable Instant expiresAt, Set<String> transientToolResults) {
    /**
     * 保留没有到期约束的静态资料构造方式。
     * @param name 来源名称
     * @param text 完整资料正文
     */
    public AgentPromptFragment(String name, String text) { this(name, text, null, Set.of()); }
    /**
     * 保留既有资料的到期语义，默认完整保留工具结果。
     * @param name 来源名称
     * @param text 完整资料
     * @param expiresAt 可选到期时刻
     */
    public AgentPromptFragment(String name, String text, @Nullable Instant expiresAt) { this(name, text, expiresAt, Set.of()); }
    /**
     * 校验来源和有界正文，不自动截断证据或将其解释为系统指令。
     * @param name 小写字母开头、最长四十八位的来源名称
     * @param text 非空资料，最多一百万个 Java 字符
     * @param expiresAt 可选最早到期时刻，不自动刷新旧证据
     * @param transientToolResults 仅供原轮次使用的工具名称
     * @throws NullPointerException 当来源或正文为空时
     * @throws IllegalArgumentException 当来源格式或正文范围非法时
     */
    public AgentPromptFragment {
        Objects.requireNonNull(name, "Prompt 资料来源不能为空。"); Objects.requireNonNull(text, "Prompt 资料正文不能为空。");
        transientToolResults = Set.copyOf(transientToolResults);
        if (!name.matches("[a-z][a-z0-9_]{0,47}") || text.isBlank() || text.length() > 1_000_000) {
            throw new IllegalArgumentException("Prompt 资料来源或正文范围无效。");
        }
        if (transientToolResults.size() > 16 || transientToolResults.stream().anyMatch(value -> !value.matches("[a-zA-Z0-9_-]{1,64}"))) {
            throw new IllegalArgumentException("临时工具结果的名称或数量无效。");
        }
    }
}
