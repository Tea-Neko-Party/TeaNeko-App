package org.zexnocs.teanekoplugin.agent.cognition.memory.layered;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 已登记作用域的分层读取和压缩开关，不从模型文本取得授权。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param enabled 真实登记且启用
 * @param read 允许历史及记忆资料读取
 * @param compress 允许采集及后台摘要
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
public record LayeredMemoryPolicy(boolean enabled, boolean read, boolean compress) { }
