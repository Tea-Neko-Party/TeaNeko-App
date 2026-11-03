package org.zexnocs.teanekoagent.memory.admin;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactRecord;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespace;

import java.util.Objects;

/**
 * 已结束管理事务的结果，原操作重放返回相同结果，不表示当前空间仍处于原版本。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param status 提交或拒绝状态
 * @param namespace 已提交目标或冲突观察，空间不存在时为空
 * @param records 克隆记录数量
 * @param record 编辑后的记录，其他动作为空
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public record MemoryManagementResult(Status status, @Nullable MemoryNamespace namespace, int records, @Nullable MemoryFactRecord record) {
    /**
     * 保持计数和成功元数据有效，不把缺失目标描述为已提交。
     * @param status 结果状态
     * @param namespace 元数据
     * @param records 记录数量
     * @param record 编辑结果
     * @throws IllegalArgumentException 当成功缺少空间或数量为负时
     */
    public MemoryManagementResult {
        Objects.requireNonNull(status, "管理结果状态不能为空。");
        if (records < 0 || status == Status.APPLIED && namespace == null) { throw new IllegalArgumentException("记忆管理结果不完整。"); }
    }
    /**
     * 配额、版本冲突和规模限制均为未执行，不能伪装成部分成功。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    public enum Status { APPLIED, CONFLICT, NOT_FOUND, QUOTA_EXCEEDED, TOO_LARGE }
}
