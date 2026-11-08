package org.zexnocs.teanekoclient.agent.response;

import org.zexnocs.teanekoclient.agent.service.AgentOperationProtocol;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
/**
 * 表达候选的持久化观察结果，不把重复提交当作新增。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：保留响应协议，候选提交通过可选操作贡献提供。
 *
 * @param status 真实操作状态
 * @param added 本次是否新增
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.9.0-l
 */
public record AgentPersonalityCorrectionResponse(String status, boolean added) {
    // 2026/11/07：保留原安全状态投影，不在客户端解释人格领域规则。
    public static final AgentOperationProtocol<AgentPersonalityCorrectionResponse> PROTOCOL = new AgentOperationProtocol<>(
            "correction", "PERSONALITY_CORRECTION", AgentPersonalityCorrectionResponse.class,
            (id, status) -> new AgentPersonalityCorrectionResponse(status, false),
            response -> response.status().equals("STORED") || response.status().equals("DUPLICATE"));
}
