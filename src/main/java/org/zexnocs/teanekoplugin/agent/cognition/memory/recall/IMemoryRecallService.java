package org.zexnocs.teanekoplugin.agent.cognition.memory.recall;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.concurrent.CompletionStage;

/**
 * 独立事实 Recall 端口，不读取人格、不收集历史、不自动查询聊天客户端。
 * 启用、当前引用、读开关和业务权限由宿主在调用及注入前检查。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@FunctionalInterface
public interface IMemoryRecallService {
    /**
     * 在借用的业务域固定配置和时刻，异步查询并返回带覆盖程度的证据。
     * @param request 当前可信请求
     * @return 过滤和预算完成后的结果；配置、代次和来源失败异常结束
     */
    CompletionStage<MemoryRecallResult> recall(MemoryRecallRequest request);
}
