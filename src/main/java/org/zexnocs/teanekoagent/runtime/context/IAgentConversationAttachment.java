package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import java.util.List;

/**
 * 会话压缩提供者生成的不可变附件；宿主只负责完整计量、固定采用与保留。
 * <br>2026/11/07：以中立生命周期合同接入可选业务能力，保留宿主权限和异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentConversationAttachment {
    /** @return 提供者已包装的必需上下文消息，不能将低信任来源提升为运行规则 */
    List<ILLMMessage> messages();
}
