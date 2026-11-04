package org.zexnocs.teanekocore.database.configdata;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 配置事务的明确请求、版本快照和无正文审计，不依赖 HTTP 或 Agent。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：字段标识允许合法下划线，仍不接受任意 JSON 指针。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
public final class ConfigEdits {
    /** 禁止实例化数据命名空间。 */
    private ConfigEdits() {}

    /**
     * 明确的配置生命周期操作。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public enum Action { REGISTER, PATCH, UNREGISTER }

    /**
     * 不直接导出的内部配置快照。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param registered 是否已登记
     * @param revision 原子版本标识，包含行身份以拒绝删除重建后的旧请求
     * @param json 原记录或未登记默认值
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    public record Snapshot(boolean registered, String revision, String json) {}

    /**
     * 有界原子写请求。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：允许合法下划线字段标识，保持请求大小限制。
     *
     * @param operationId 持久幂等标识
     * @param expectedRevision 调用者已确认的原版本
     * @param action 明确动作
     * @param fields 原字段的文本值，不接受任意对象或类型名
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.1-l
     */
    public record Change(UUID operationId, String expectedRevision, Action action, Map<String, String> fields) {
        /**
         * 2026/11/04：字段标识允许下划线，但仍不接受路径或任意属性表达式。
         * @param operationId 幂等标识
         * @param expectedRevision 原版本
         * @param action 动作
         * @param fields 有界字段
         */
        public Change {
            Objects.requireNonNull(operationId, "配置操作标识不能为空。");
            Objects.requireNonNull(action, "配置动作不能为空。");
            fields = Map.copyOf(fields);
            if (expectedRevision == null || expectedRevision.isBlank() || expectedRevision.length() > 128
                    || fields.size() > 64 || fields.entrySet().stream().anyMatch(entry ->
                    !entry.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || entry.getValue().length() > 8192)
                    || fields.values().stream().mapToInt(String::length).sum() > 65536
                    || action != Action.PATCH && !fields.isEmpty() || action == Action.PATCH && fields.isEmpty()) {
                throw new IllegalArgumentException("配置操作的版本、字段或正文长度无效。");
            }
        }
    }

    /**
     * 同事务提交的安全回执与审计。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 原操作
     * @param targetDigest 配置目标摘要，不暴露内部键
     * @param revision 已提交版本
     * @param registered 提交后是否登记
     * @param action 原动作
     * @param fields 变更字段名，不保存字段正文
     * @param at 提交时间
     * @param replayed 是否为原结果重放
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Receipt(UUID operationId, String targetDigest, String revision, boolean registered,
                          Action action, List<String> fields, Instant at, boolean replayed) {}

    /**
     * 幂等记录，正文仅保留摘要。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param actor 操作者摘要
     * @param digest 完整请求摘要
     * @param result 原真实回执
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Operation(String actor, String digest, Receipt result) {}

    /**
     * 行锁内使用的有界原始操作目录。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operations 至多五百一十二项
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Journal(Map<UUID, Operation> operations) {}

    /**
     * 固定管理器的键页，仅供受信适配器建立聊天目录。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param keys 当前页
     * @param next 下页物理键，空表示结束
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    public record Keys(List<String> keys, String next) {}
}
