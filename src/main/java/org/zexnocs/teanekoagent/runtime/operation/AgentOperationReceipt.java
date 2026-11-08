package org.zexnocs.teanekoagent.runtime.operation;

// 2026/11/07：按真实职责分离通用来源、防重底座与插件业务，持久标识保持不变。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 原生操作处理链的非敏感回执，COMPLETED 不等于业务成功，具体反馈由原生命令负责。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @param status 实际处理或防重状态
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record AgentOperationReceipt(Status status) {
    /**
     * 明确区分原分发终态及未知副作用，不把中断后的在途记录当作可重试。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    public enum Status { NOT_MATCHED, DISABLED, CANCELLED, METHOD_NOT_FOUND, INVALID_ARGUMENTS, DATA_TYPE_MISMATCH,
        OUT_OF_SCOPE, PERMISSION_DENIED, HELP, COMPLETED, FAILED, INDETERMINATE, CONFLICT, UNAVAILABLE }
}
