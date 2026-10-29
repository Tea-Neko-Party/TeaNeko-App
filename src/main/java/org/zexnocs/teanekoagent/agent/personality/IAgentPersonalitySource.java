package org.zexnocs.teanekoagent.agent.personality;

import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;

/**
 * 人格解析及当前身份复核端口，Agent 不依赖文件、数据库或补正存储实现。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentPersonalitySource {
    /**
     * 解析本轮基础人格及补正快照，必要的初始化或切换须等待提交。
     * @param scope 真实作用域
     * @param control 借用执行控制
     * @return 不含运行资源的人格快照
     */
    CompletionStage<AgentPersonalitySnapshot> resolve(AgentScope scope, ExecutionControl control);

    /**
     * 只读复核基础设定和绑定仍有效；新补正从下一轮生效，不覆盖本轮快照。
     * @param scope 原作用域
     * @param snapshot 原快照
     * @param control 当前使用或观察的借用控制
     * @return 原人格身份仍有效时完成
     */
    CompletionStage<Void> verify(AgentScope scope, AgentPersonalitySnapshot snapshot, ExecutionControl control);
}
