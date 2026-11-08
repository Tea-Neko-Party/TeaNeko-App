package org.zexnocs.teanekoclient.agent.response;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoclient.agent.service.AgentOperationProtocol;
import java.util.UUID;
/**
 * 真实事实提交回执，不复制完整记忆或画像。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：保留响应协议，具体写入通过可选操作贡献提供。
 *
 * @param status 新增、重复、冲突或拒绝状态
 * @param recordId 已提交记录标识，未取得时为空
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.9.0-l
 */
public record AgentMemoryWriteResponse(String status, @Nullable UUID recordId) {
    // 2026/11/07：响应投影与插件安装状态分离，缺失处理器仍产生原形状的失败回执。
    public static final AgentOperationProtocol<AgentMemoryWriteResponse> PROTOCOL = new AgentOperationProtocol<>(
            "memory", "MEMORY_WRITE", AgentMemoryWriteResponse.class,
            (id, status) -> new AgentMemoryWriteResponse(status, null),
            response -> response.status().equals("STORED") || response.status().equals("DUPLICATE"));
}
