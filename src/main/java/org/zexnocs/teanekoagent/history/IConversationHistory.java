package org.zexnocs.teanekoagent.history;

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;

/**
 * 有当前启用、空间及人格复核的历史业务入口；采集不启动模型或会话窗口。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IConversationHistory {
    /**
     * 保存一条真实来源消息，供当前对话及后续平台旁观采集复用。
     * @param context 固定当前边界
     * @param message 真实消息
     * @param control 借用控制
     * @return 已提交新增为 true，重复或采集关闭为 false
     */
    CompletionStage<Boolean> append(HistoryContext context, HistoryMessage message, ExecutionControl control);
    /**
     * 只读查询当前有效边界，不因此初始化另一个空间。
     * @param context 可信当前边界
     * @param query 有界查询
     * @param control 借用控制
     * @return 完整消息及覆盖信息
     */
    CompletionStage<HistoryPage> search(HistoryContext context, HistoryQuery query, ExecutionControl control);
}
