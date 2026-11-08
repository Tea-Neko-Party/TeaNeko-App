package org.zexnocs.teanekoplugin.agent.cognition.memory.recall;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import java.util.Objects;

/**
 * 可信宿主固定的空间引用、查询用途和可选单次预算；自身不授予读取权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param reference 当前轮次固定引用
 * @param query 业务筛选
 * @param use 查询用途
 * @param limits 可选显式预算，不能提高本地硬上限或扫描/字符上限
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record MemoryRecallRequest(MemoryNamespaceReference reference, MemoryRecallQuery query, Use use, @Nullable MemoryRecallLimits limits) {
    /**
     * 固定必需数据，不解析配置或访问数据库。
     * @param reference 空间引用
     * @param query 查询条件
     * @param use 用途
     * @param limits 可选预算
     * @throws NullPointerException 当必需字段为空时
     */
    public MemoryRecallRequest {
        Objects.requireNonNull(reference, "检索空间引用不能为空。"); Objects.requireNonNull(query, "记忆查询不能为空。");
        Objects.requireNonNull(use, "记忆查询用途不能为空。");
    }
    /**
     * 用途只选择默认预算，不自动注入 Prompt 或开放工具。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    public enum Use { INJECTION, TOOL }
}
