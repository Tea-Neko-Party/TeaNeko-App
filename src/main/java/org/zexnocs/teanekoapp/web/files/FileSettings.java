package org.zexnocs.teanekoapp.web.files;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.file_config.FileConfigDocument;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

/**
 * 文件配置的脱敏表单与明确动作；原文、候选对象和文件路径均不属于传输视图。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class FileSettings {
    /** 禁止实例化契约命名空间。 */
    private FileSettings() {}

    /**
     * 已注册编辑字段；星号仅匹配数组的一层数字索引。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param path 精确指针或数组模板
     * @param label 中文名称
     * @param description 字段约束及单位
     * @param kind BOOLEAN、NUMBER、TEXT 或 JSON
     * @param secret 是否只接受替换/清除且禁止返回原值
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Rule(String path, String label, String description, String kind, boolean secret) {}

    /**
     * 一个允许有界追加及删除条目的数组。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param path 精确数组指针
     * @param label 中文集合名
     * @param maximum 最大条目数
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record CollectionRule(String path, String label, int maximum) {}

    /**
     * 一处明确局部操作，KEEP 通过不提交该路径表达。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param path 已开放路径，APPEND 使用集合本身
     * @param action SET、REMOVE、APPEND、REPLACE_SECRET 或 CLEAR_SECRET
     * @param value 新值，删除或清除为空
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Edit(String path, String action, @Nullable JsonNode value) {}

    /**
     * 预检和保存使用同一版本与局部动作，不接受完整脱敏对象覆盖。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 宿主操作 UUID
     * @param expectedRevision 原磁盘版本
     * @param edits 本次明确修改
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Change(UUID operationId, String expectedRevision, List<Edit> edits) {
        /**
         * @param operationId 原编号
         * @param expectedRevision 原版本
         * @param edits 有界字段操作
         * @throws IllegalArgumentException 当请求规模或动作无效时
         */
        public Change {
            Objects.requireNonNull(operationId);
            edits = List.copyOf(edits);
            if (expectedRevision == null || expectedRevision.isBlank() || expectedRevision.length() > 128
                    || edits.isEmpty() || edits.size() > 64 || edits.stream().anyMatch(edit -> edit.path() == null
                    || edit.path().length() > 256 || !edit.path().startsWith("/")
                    || !Set.of("SET", "REMOVE", "APPEND", "REPLACE_SECRET", "CLEAR_SECRET").contains(edit.action()))
                    || edits.stream().map(Edit::path).distinct().count() != edits.size()) {
                throw new IllegalArgumentException("文件配置修改参数无效。");
            }
        }
    }

    /**
     * 应用是独立的幂等操作，不把保存编号改作另一种动作。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 应用编号
     * @param expectedRevision 需要应用的已保存文件版本
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Application(UUID operationId, String expectedRevision) {
        /**
         * @param operationId 原应用编号
         * @param expectedRevision 原磁盘版本
         * @throws IllegalArgumentException 当版本无效时
         */
        public Application {
            Objects.requireNonNull(operationId);
            if (expectedRevision == null || expectedRevision.isBlank() || expectedRevision.length() > 128) {
                throw new IllegalArgumentException("文件应用版本无效。");
            }
        }
    }

    /**
     * 可发现配置区，不暴露真实文件路径。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 管理标识
     * @param name 中文名
     * @param application NEXT_USE 或 RESTART
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Section(String id, String name, String application) {}

    /**
     * 仅包含已开放字段的原始模板值，保护字段不返回原值或展开值。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param path 实际指针
     * @param label 中文名
     * @param description 说明
     * @param kind 编辑类型
     * @param secret 是否受保护
     * @param configured 是否在文件或模板中声明了非空值
     * @param source 文件、模板或未声明
     * @param value 非敏感原始节点，保护字段为空
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Field(String path, String label, String description, String kind, boolean secret,
                        boolean configured, String source, @Nullable JsonNode value) {}

    /**
     * 表单区和磁盘/运行状态明确分开。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 管理区
     * @param name 中文名
     * @param revision 磁盘版本
     * @param exists 是否已存在
     * @param application 应用方式
     * @param runtime 当前进程加载状态
     * @param fields 脱敏字段
     * @param collections 可编辑集合
     * @param collectionSizes 真实数组大小
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record View(String id, String name, String revision, boolean exists, String application,
            FileConfigDocument.Stamp runtime, List<Field> fields, List<CollectionRule> collections,
            Map<String, Integer> collectionSizes) {}

    /**
     * 原操作事实，不表示其他模块或在途请求已经全部切换。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 原操作
     * @param status PREPARED、SAVED、APPLIED、APPLY_FAILED、RESTART_REQUIRED、INDETERMINATE 或 NOT_SAVED
     * @param diskRevision 本操作目标磁盘版本
     * @param runtime 操作时观察的运行快照
     * @param note 固定中文说明
     * @param fields 变更路径，不包含字段值
     * @param at 原操作时间
     * @param replayed 是否原结果重放
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Receipt(UUID operationId, String status, String diskRevision, FileConfigDocument.Stamp runtime,
            String note, List<String> fields, Instant at, boolean replayed) {}
}
