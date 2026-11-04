package org.zexnocs.teanekoagent.component.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.Objects;
import java.util.UUID;

/**
 * 业务记忆空间标识，与 EasyData 的物理 namespace 分离，不拼入用户或 scope 隔离规则。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param value 区分明确且适合稳定存储的标识
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record MemoryNamespaceId(String value) {
    public static final MemoryNamespaceId SHARED = new MemoryNamespaceId("default");

    /**
     * 拒绝自动大小写或空白归一化，避免数据库排序规则造成别名。
     * @param value 小写字母、数字、点、横线或下划线组成的标识
     * @throws NullPointerException 当标识为空时
     * @throws IllegalArgumentException 当标识不是一至六十四位稳定形式时
     */
    public MemoryNamespaceId {
        Objects.requireNonNull(value, "记忆空间标识不能为空。");
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("记忆空间标识必须以小写字母或数字开头，仅含小写字母、数字、点、横线或下划线，且不超过六十四位。");
        }
    }

    /**
     * 由宿主稳定请求 ID 生成新空间定位，重试不得生成新 UUID。
     * @param requestId 宿主固定的创建请求
     * @return 可重复定位的新空间 ID
     * @throws NullPointerException 当请求 ID 为空时
     */
    public static MemoryNamespaceId created(UUID requestId) {
        return new MemoryNamespaceId("ns-" + Objects.requireNonNull(requestId, "创建请求标识不能为空。"));
    }
}
