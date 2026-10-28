package org.zexnocs.teanekocore.framework.composition;

import java.util.Objects;
import java.util.UUID;

/**
 * 不包含平台或权限语义的实体标识。
 *
 * @param value 待处理的值
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record EntityId(String value) {
    /**
     * 规范化实体标识并拒绝空值，不在标识中自动编码业务权限。
     *
     * @param value 待处理的值
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public EntityId {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Entity id must not be blank");
        }
    }

    /**
     * 生成不编码业务权限的独立实体标识，供新实例建立自己的状态边界。
     *
     * @return 可用于新实体实例的稳定标识
     */
    public static EntityId random() {
        return new EntityId(UUID.randomUUID().toString());
    }
}
