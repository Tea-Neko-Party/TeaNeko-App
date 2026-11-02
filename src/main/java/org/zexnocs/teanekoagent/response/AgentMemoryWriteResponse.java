package org.zexnocs.teanekoagent.response;
import org.jspecify.annotations.Nullable;
import java.util.UUID;
/**
 * 真实事实提交回执，不复制完整记忆或画像。
 * @param status 新增、重复、冲突或拒绝状态
 * @param recordId 已提交记录标识，未取得时为空
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
public record AgentMemoryWriteResponse(String status, @Nullable UUID recordId) { }
