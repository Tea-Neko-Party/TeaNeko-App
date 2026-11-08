package org.zexnocs.teanekoagent.llm.framework.interaction;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import java.util.UUID;

/**
 * 独立模型交互的终态观察端口；由宿主负责汇总的交互不重复通知。
 * <br>2026/11/06：阶段日志改为一次性结果观察，正文只允许经脱敏后写入受控日志。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.9.0-l
 */
@FunctionalInterface
public interface ILLMInteractionObserver {
    /**
     * 完整交互结束后通知一次，观察失败不得改变业务结果。
     * @param request 原请求编号
     * @param elapsedMillis 实际耗时
     * @param result 本次全部模型、工具尝试及终态
     */
    void finished(UUID request, long elapsedMillis, LLMInteractionResult result);
}
