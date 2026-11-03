package org.zexnocs.teanekoagent.agent.command;

import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import java.util.concurrent.CompletionStage;

/**
 * 可信命令桥接端口，原生身份由适配器拥有，不进入组件、模型参数或持久化正文。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public interface ICommandInvocationPort {
    /**
     * 只检查宿主是否仍持有本请求的实际原生来源，不授予永久执行权限。
     * @param request 原请求及真实来源
     * @return 已有匹配来源时为真
     */
    boolean available(AgentRunRequest request);
    /**
     * 经原命令解析、权限及执行链运行，等待处理及原生命令反馈结束。
     * @param request 本轮实际请求
     * @param callId 原工具调用标识，不能由业务 JSON 替换
     * @param commandText 模型选择的唯一业务参数
     * @param context 当前类型化上下文及借用父控制
     * @return 明确终态；已开始副作用不因观察取消而自动重试
     */
    CompletionStage<CommandInvocationReceipt> invoke(AgentRunRequest request, String callId, String commandText, ToolInvocationContext context);
}
