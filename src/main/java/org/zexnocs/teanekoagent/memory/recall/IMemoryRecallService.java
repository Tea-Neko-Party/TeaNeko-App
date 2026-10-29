package org.zexnocs.teanekoagent.memory.recall;

import java.util.concurrent.CompletionStage;

/**
 * 独立事实 Recall 端口，不读取人格、不收集历史、不自动查询聊天客户端。
 * 启用、当前引用、读开关和业务权限由宿主在调用及注入前检查。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
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
