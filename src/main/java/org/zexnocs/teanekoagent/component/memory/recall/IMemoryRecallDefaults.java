package org.zexnocs.teanekoagent.component.memory.recall;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 本地检索策略来源，读取失败须明确失败而非换成宽松默认值。
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
public interface IMemoryRecallDefaults {
    /**
     * 在本次业务执行域中取得一份完整策略。
     * @return 已验证策略
     */
    MemoryRecallSettings settings();
}
