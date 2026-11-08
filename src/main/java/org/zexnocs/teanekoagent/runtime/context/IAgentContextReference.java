package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.runtime.AgentScope;

/**
 * 一次固定的作用域业务引用；具体数据、版本含义与访问策略由提供者拥有。
 * <br>2026/11/07：以中立生命周期合同接入可选业务能力，保留宿主权限和异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentContextReference {
    /** @return 引用绑定的可信作用域 */
    AgentScope scope();
    /** @return 用于关联已知会话的稳定分区标识，不授予访问权限 */
    String partitionId();
    /**
     * 在发布新观察前执行纯数据版本仲裁，不读取存储或刷新旧引用。
     * @param previous 当前已经发布的引用
     * @throws RuntimeException 当观察迟到、定位冲突或类型不匹配时
     */
    void validateSuccessor(IAgentContextReference previous);
}
