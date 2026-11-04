package org.zexnocs.teanekoagent.component.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 从本地策略解析个人空间配额，不把业务配额规则放进 Core。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface IMemoryNamespaceDefaults {
    /**
     * 提供下一次创建使用的配额，不追溯删除已有空间。
     * @return 正整数配额
     * @throws IllegalStateException 当本地配置无效时
     */
    int maxCreated();
}
