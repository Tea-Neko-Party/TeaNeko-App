package org.zexnocs.teanekoclient.agent.response;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import org.jspecify.annotations.Nullable;
import java.util.UUID;
/**
 * 真实事实提交回执，不复制完整记忆或画像。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param status 新增、重复、冲突或拒绝状态
 * @param recordId 已提交记录标识，未取得时为空
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
public record AgentMemoryWriteResponse(String status, @Nullable UUID recordId) { }
