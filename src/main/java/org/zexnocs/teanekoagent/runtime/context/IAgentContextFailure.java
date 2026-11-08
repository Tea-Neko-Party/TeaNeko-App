package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.runtime.AgentRunResult;

/**
 * 业务上下文失效的中立分类端口，宿主不识别插件异常类型或解析异常文本。
 * <br>2026/11/07：以中立生命周期合同接入可选业务能力，保留宿主权限和异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentContextFailure {
    /** @return 停止当前请求的固定业务状态 */
    AgentRunResult.Status rejectionStatus();
}
