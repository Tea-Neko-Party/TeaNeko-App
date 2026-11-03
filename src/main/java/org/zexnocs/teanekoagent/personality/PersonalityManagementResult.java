package org.zexnocs.teanekoagent.personality;

import org.jspecify.annotations.Nullable;

/**
 * 原管理操作的持久化回执，不代表当前引用和版本仍未变化。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param status 原业务状态
 * @param key 原结果 key，克隆返回新 key
 * @param stateRevision 原提交观察的集合版本，不存在时为 -1
 * @param bindingRevision 原提交观察的 scope 绑定版本
 * @param backupKey 本操作生成的不可变备份
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public record PersonalityManagementResult(Status status, String key, long stateRevision, long bindingRevision, @Nullable String backupKey) {
    /**
     * 区分真实提交、无变化、冲突及缺失，不把失败伪装成空补正。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    public enum Status { APPLIED, UNCHANGED, CONFLICT, NOT_FOUND }
}
