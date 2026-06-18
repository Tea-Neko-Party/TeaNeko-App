package org.zexnocs.teanekoagent_old.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent_old.client.tools.ITeaNekoAgentToolbox;
import org.zexnocs.teanekoagent_old.sender.AgentConversationSender;
import org.zexnocs.teanekoagent_old.sender.AgentMemoryWriteSender;
import org.zexnocs.teanekoagent_old.sender.AgentPersonalityCorrectionSender;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * TeaNeko Agent 内置客户端工具箱实现。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Component
@RequiredArgsConstructor
public class TeaNekoAgentToolbox implements ITeaNekoAgentToolbox {
    /** Agent 手动对话 sender。 */
    private final AgentConversationSender agentConversationSender;

    /** Agent 手动记忆写入 sender。 */
    private final AgentMemoryWriteSender agentMemoryWriteSender;

    /** Agent 手动人格修正 sender。 */
    private final AgentPersonalityCorrectionSender agentPersonalityCorrectionSender;

    /** Agent 客户端日志服务。 */
    private final ILogger logger;
}
