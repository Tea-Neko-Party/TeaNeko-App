package org.zexnocs.teanekoagent.memory.fact.legacy;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 显式旧资料预览及原子导入端口，不自动迁移或删除来源。
 * 权限、启用及当前绑定由可信管理边界检查，存储事务必须仲裁目标 generation。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface ILegacyMemoryStore {
    /**
     * 显式读取原行并规范化事实；没有行与空数组不同。
     * @param location 实际旧键
     * @param fallbackRecordedAt 固定缺失时间估算，导入时必须复用
     * @return 原行快照，损坏时异常而非空快照
     */
    CompletionStage<Optional<LegacyMemorySnapshot>> read(LegacyMemoryLocation location, Instant fallbackRecordedAt);
    /**
     * 锁定来源并检查预览版本，在同一事务保存事实、旧记录映射和原操作回执。
     * @param command 明确的来源、主体和目标映射
     * @return 提交后回执；同来源不能借新请求转投其他空间或清空后的代次
     */
    CompletionStage<LegacyMemoryImportResult> importSnapshot(LegacyMemoryImport command);
}
