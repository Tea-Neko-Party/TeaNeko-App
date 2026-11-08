package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import java.util.List;

/**
 * 提供者固定的必需提示上下文；只携带不可变数据，不持有服务或执行资源。
 * <br>2026/11/07：以中立生命周期合同接入可选业务能力，保留宿主权限和异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentPromptContext {
    /** @return 本轮上下文身份，变化后旧窗口和重放失效 */
    String identity();
    /** @return 用于关联共享上下文的稳定分组键 */
    String groupKey();
    /** @return 插入原前导系统消息之后的完整协议消息，提供者负责可信规则与参考数据分离 */
    List<ILLMMessage> messages();
}
