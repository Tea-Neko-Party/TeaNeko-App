package org.zexnocs.teanekoagent.memory.recall;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import java.util.Objects;

/**
 * 可信宿主固定的空间引用、查询用途和可选单次预算；自身不授予读取权限。
 * @param reference 当前轮次固定引用
 * @param query 业务筛选
 * @param use 查询用途
 * @param limits 可选显式预算，不能提高本地硬上限或扫描/字符上限
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
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
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Use { INJECTION, TOOL }
}
