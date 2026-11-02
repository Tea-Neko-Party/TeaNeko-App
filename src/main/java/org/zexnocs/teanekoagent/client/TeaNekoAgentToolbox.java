package org.zexnocs.teanekoagent.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.client.tools.ITeaNekoAgentToolbox;
import org.zexnocs.teanekoagent.sender.AgentConversationSender;
import org.zexnocs.teanekoagent.sender.AgentMemoryWriteSender;
import org.zexnocs.teanekoagent.sender.AgentPersonalityCorrectionSender;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 仅公开 Agent 专属 Sender；普通平台工具保持原不支持边界。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
@Getter @Component @RequiredArgsConstructor
public final class TeaNekoAgentToolbox implements ITeaNekoAgentToolbox {
    private final AgentConversationSender agentConversationSender;
    private final AgentMemoryWriteSender agentMemoryWriteSender;
    private final AgentPersonalityCorrectionSender agentPersonalityCorrectionSender;
    private final ILogger logger;
}
