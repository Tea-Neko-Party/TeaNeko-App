package org.zexnocs.teanekoagent_old.agent.prompt;

/**
 * Prompt 最终合并前的一个组件。
 *
 * @param name     组件名称。
 * @param priority 组件优先级，数值越小越靠前。
 * @param content  组件内容。
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public record AgentPromptComponent(
        String name,
        int priority,
        String content
) implements Comparable<AgentPromptComponent> {
    /**
     * 创建 Prompt 组件。
     *
     * @param name     组件名称。
     * @param priority 组件优先级。
     * @param content  组件内容。
     */
    public AgentPromptComponent {
        name = name == null ? "" : name;
        content = content == null ? "" : content;
    }

    /**
     * 按优先级比较组件。
     *
     * @param other 另一个组件。
     * @return 比较结果。
     */
    @Override
    public int compareTo(AgentPromptComponent other) {
        return Integer.compare(priority, other.priority);
    }
}
