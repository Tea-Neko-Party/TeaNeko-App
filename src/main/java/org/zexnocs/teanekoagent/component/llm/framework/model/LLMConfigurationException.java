package org.zexnocs.teanekoagent.component.llm.framework.model;

import org.zexnocs.teanekocore.logger.IFailureReason;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 可向配置状态界面报告的错误；消息和异常链不得含配置值或凭据。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：通过 Core 安全原因接口解释配置错误，不让底层按业务类名识别异常。
 *
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.4-l
 */
public class LLMConfigurationException extends IllegalStateException implements IFailureReason {
    private final org.zexnocs.teanekocore.logger.@org.jspecify.annotations.Nullable FailureDiagnostic diagnostic;
    /**
     * 2026/11/05：仅公开固定的排查方向，不导出原配置值或任意异常消息。
     * @return 供模型检测与聊天失败提示共用的安全原因
     */
    @Override
    public String failureReason() {
        return "模型配置不可用，请检查路由、凭据、连接及参数。"
                + (diagnostic == null ? "" : " " + diagnostic.description());
    }

    /**
     * 建立配置边界失败信号；传入说明不得包含密钥、完整地址或其他敏感配置值。
     *
     * @param message 需要发送或映射的消息
     */
    public LLMConfigurationException(String message) {
        super(message);
        diagnostic = null;
    }

    /**
     * 2026/11/06：在首次脱敏处保留结构化根因，不传播原始异常消息。
     * @param message 固定安全配置说明
     * @param failure 原始失败
     */
    public LLMConfigurationException(String message, Throwable failure) {
        super(message);
        diagnostic = org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure);
    }

    /** @return 原异常安全结构 */
    @Override
    public org.zexnocs.teanekocore.logger.@org.jspecify.annotations.Nullable FailureDiagnostic failureDiagnostic() {
        return diagnostic;
    }
}
