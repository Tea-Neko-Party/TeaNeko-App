package org.zexnocs.teanekoplugin.agent.cognition.integration.web;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryEventTime;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agent 控制台的有界请求和明确视图，不反序列化平台用户或执行身份。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class AgentConsole {
    /** 禁止实例化 DTO 命名空间。 */
    private AgentConsole() {}

    /**
     * 活动增量查询，游标仅在本进程窗口有效。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param after 已观察序号，首次为零
     * @param limit 一至一百项
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record ActivityQuery(long after, int limit) {
        /**
         * @param after 原序号
         * @param limit 本页上限
         * @throws IllegalArgumentException 当分页无效时
         */
        public ActivityQuery {
            if (after < 0 || limit < 1 || limit > 100) {
                throw new IllegalArgumentException("活动分页超出范围。");
            }
        }
    }

    /**
     * 记忆读取或原管理动作；查询原回执使用 OPERATION，相同操作重试保留所有参数。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param action READ、EDIT、CLEAR、CLONE 或 OPERATION
     * @param operationId 写入及查回执时的原编号
     * @param namespace 空串使用当前绑定或默认共享空间
     * @param generation 首次读取为 -1，后继读取必须固定代次
     * @param revision 明确预期事实或空间版本
     * @param after 原分页游标
     * @param recordId 编辑记录
     * @param text 可选替换正文
     * @param locked 可选锁定状态
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record MemoryQuery(String action, @Nullable UUID operationId, String namespace,
            long generation, long revision, @Nullable UUID after, @Nullable UUID recordId,
            @Nullable String text, @Nullable Boolean locked) {
        /**
         * @param action 明确动作
         * @param operationId 原操作
         * @param namespace 空间
         * @param generation 原代次
         * @param revision 原版本
         * @param after 原游标
         * @param recordId 原记录
         * @param text 正文
         * @param locked 锁定值
         * @throws IllegalArgumentException 当动作或请求规模无效时
         */
        public MemoryQuery {
            if (!Set.of("READ", "EDIT", "CLEAR", "CLONE", "OPERATION").contains(action)
                    || namespace == null || namespace.length() > 128 || generation < -1
                    || text != null && text.length() > 4096 || !action.equals("READ") && operationId == null) {
                throw new IllegalArgumentException("记忆管理参数无效。");
            }
        }
    }

    /**
     * 指定主体的证据画像，不另建可任意覆盖的画像数据库。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param subjectId 明确主体 UUID
     * @param namespace 空串使用当前绑定
     * @param generation 首次读取为 -1
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record ProfileQuery(UUID subjectId, String namespace, long generation) {
        /**
         * @param subjectId 主体
         * @param namespace 空间
         * @param generation 代次
         * @throws IllegalArgumentException 当主体或空间无效时
         */
        public ProfileQuery {
            if (subjectId == null || namespace == null || namespace.length() > 128 || generation < -1) {
                throw new IllegalArgumentException("画像主体或空间无效。");
            }
        }
    }

    /**
     * 人格当前绑定及补正管理，不通过浏览器变更基础文件或伪造学习来源。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param action READ、OPERATION 或原补正管理动作
     * @param operationId 原操作编号
     * @param key 空串读取当前 key，其余为明确查询或绑定目标
     * @param bindingRevision 原绑定版本
     * @param stateRevision 原内容版本
     * @param correctionId 原补正记录
     * @param field 新增表达字段
     * @param text 新增或替换正文
     * @param locked 锁定值
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record PersonalityQuery(String action, @Nullable UUID operationId, String key,
            long bindingRevision, long stateRevision, @Nullable UUID correctionId, @Nullable String field,
            @Nullable String text, @Nullable Boolean locked) {
        /**
         * @param action 动作
         * @param operationId 原操作
         * @param key 原 key
         * @param bindingRevision 原绑定版本
         * @param stateRevision 原内容版本
         * @param correctionId 原记录
         * @param field 允许的表达字段
         * @param text 正文
         * @param locked 锁定值
         * @throws IllegalArgumentException 当动作或规模无效时
         */
        public PersonalityQuery {
            if (!Set.of("READ", "OPERATION", "BIND", "CLONE", "BACKUP", "ADD", "EDIT", "LOCK", "REMOVE")
                    .contains(action) || key == null || key.length() > 256 || text != null && text.length() > 512
                    || !action.equals("READ") && operationId == null) {
                throw new IllegalArgumentException("人格管理参数无效。");
            }
        }
    }

    /**
     * 不包含原始导入 JSON 的证据视图。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param id 原记录 ID
     * @param revision 原内容版本
     * @param subjectId 原主体
     * @param text 正文
     * @param kind 事实或偏好
     * @param locked 管理锁定
     * @param createdAt 创建时刻
     * @param updatedAt 更新时间
     * @param time 事件时间及其精度
     * @param client 来源平台
     * @param sourceScope 来源聊天定位，仅作证据说明
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record Evidence(UUID id, long revision, UUID subjectId, String text, String kind, boolean locked,
            Instant createdAt, Instant updatedAt, MemoryEventTime time, String client, String sourceScope) {}

    /**
     * 一个固定代次的事实页。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param namespace 空间标识
     * @param generation 数据代次
     * @param revision 空间版本
     * @param exists 是否实际已登记
     * @param records 明确证据
     * @param next 原页末游标
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record MemoryView(String namespace, long generation, long revision, boolean exists,
                             List<Evidence> records, @Nullable UUID next) {}

    /**
     * 可追溯且承认扫描限制的画像。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param subjectId 主体
     * @param namespace 空间
     * @param generation 数据代次
     * @param complete 本次预算下是否完整
     * @param scanned 原扫描数
     * @param matched 命中数
     * @param records 事实和偏好证据，修正走原事实管理
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record ProfileView(UUID subjectId, String namespace, long generation, boolean complete,
                              int scanned, int matched, List<Evidence> records) {}

    /**
     * 人格视图只包含固定基础与明确补正，不导出执行控制或整个配置对象。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param name 基础名称
     * @param rules 固定基础规则
     * @param fields 允许补正的字段
     * @param key 查询 key，未初始化为空
     * @param bindingRevision 当前绑定版本，缺失为零
     * @param revision 查询内容版本，缺失为 -1
     * @param current 是否查询当前绑定
     * @param backup 是否只读备份
     * @param corrections 补正记录
     * @param backups 最近有界备份引用
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record PersonalityView(String name, List<String> rules, Set<String> fields, String key,
            long bindingRevision, long revision, boolean current, boolean backup,
            List<Correction> corrections, List<String> backups) {}

    /**
     * 独立补正来源视图。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param id 原记录
     * @param field 表达字段
     * @param text 正文
     * @param locked 锁定标记
     * @param occurredAt 来源时刻
     * @param origin 平台用户或管理主体
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record Correction(UUID id, String field, String text, boolean locked, Instant occurredAt, String origin) {}

    /**
     * 原事务结果与后续协调分别报告，不把协调失败当作没有提交。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param operationId 原操作
     * @param status 真实业务状态
     * @param target 实际结果定位
     * @param generation 记忆数据代次，其余为 -1
     * @param revision 原结果版本
     * @param note 后续会话协调说明
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record ChangeView(UUID operationId, String status, String target, long generation, long revision, String note) {}
}
